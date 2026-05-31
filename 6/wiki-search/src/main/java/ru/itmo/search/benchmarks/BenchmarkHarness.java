package ru.itmo.search.benchmarks;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.itmo.search.analysis.StandardAnalyzer;
import ru.itmo.search.corpus.CorpusReader;
import ru.itmo.search.corpus.JsonlCorpusReader;
import ru.itmo.search.index.IndexBuilder;
import ru.itmo.search.index.MemoryIndex;
import ru.itmo.search.index.disk.DiskIndex;
import ru.itmo.search.index.disk.DiskIndexWriter;
import ru.itmo.search.index.disk.IndexConfig;
import ru.itmo.search.rank.BM25Scorer;
import ru.itmo.search.rank.ScoreDoc;
import ru.itmo.search.rank.SearchEngine;

public final class BenchmarkHarness {

    private static final int WARMUP = 3;
    private static final int ROUNDS = 10;

    private record QueryTask(String name, List<String> queries, boolean exhaustive) {
    }

    private record DiskCfg(String name, IndexConfig config) {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> opt = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) {
                opt.put(args[i].substring(2), args[++i]);
            }
        }
        long seed = Long.parseLong(opt.getOrDefault("seed", "42"));
        int perType = intOpt(opt, "queries", 120);
        int maxDocs = intOpt(opt, "maxDocs", Integer.MAX_VALUE);
        String corpusPath = opt.get("corpus");
        if (corpusPath == null) {
            throw new IllegalArgumentException("--corpus FILE.jsonl is required for benchmarks");
        }
        Path outDir = Path.of(opt.getOrDefault("out", "results"));
        Path workDir = Path.of(opt.getOrDefault("work", "target/bench-index"));
        Files.createDirectories(outDir);
        Files.createDirectories(workDir);

        StandardAnalyzer analyzer = new StandardAnalyzer(1, Set.of());
        IndexBuilder builder = new IndexBuilder(analyzer);
        CorpusReader corpus = new JsonlCorpusReader(Path.of(corpusPath));
        String corpusDesc = "wikipedia jsonl=" + corpusPath + " maxDocs=" + maxDocs;

        System.out.println("Building corpus: " + corpusDesc);
        long t0 = System.nanoTime();
        corpus.indexInto(builder, maxDocs);
        MemoryIndex mem = builder.build();
        double buildSec = (System.nanoTime() - t0) / 1e9;
        long totalPostings = 0;
        for (String t : mem.terms()) {
            totalPostings += mem.docFreq(t);
        }
        System.out.printf("Indexed %d docs, %d terms, %d postings, %d tokens in %.1fs%n",
                mem.numDocs(), mem.terms().size(), totalPostings, mem.totalTokens(), buildSec);

        Workload work = Workload.build(mem, perType, seed);

        compressionSweep(mem, analyzer, work, workDir, outDir, totalPostings);
        blockSizeSweep(mem, analyzer, work, workDir, outDir, totalPostings);
        backendComparison(mem, analyzer, work, workDir, outDir);
        recallQpsSweep(mem, analyzer, work, outDir);

        System.out.println("Benchmarks complete. CSVs in " + outDir.toAbsolutePath());
    }

    private static void compressionSweep(MemoryIndex mem, StandardAnalyzer analyzer, Workload work,
                                         Path workDir, Path outDir, long totalPostings) throws IOException {
        IndexConfig[] profiles = {
                new IndexConfig(128, "raw", "raw", "raw"),
                new IndexConfig(128, "vbyte", "vbyte", "vbyte"),
                new IndexConfig(128, "vbyte", "bitpack", "vbyte"),
                new IndexConfig(128, "bitpack", "bitpack", "bitpack"),
                new IndexConfig(128, "pfor", "vbyte", "pfor"),
                new IndexConfig(128, "pfor", "bitpack", "pfor"),
        };
        long rawPostings = -1;
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outDir.resolve("compression.csv")))) {
            w.println("profile,blockSize,docIdCodec,freqCodec,posCodec,postings_bytes,doclen_bytes,"
                    + "names_bytes,dict_bytes,total_bytes,bits_per_posting,ratio_vs_raw,build_write_ms,"
                    + "and_mean_ms,and_std_ms,and_ci95_ms,and_qps,and_qps_low,and_qps_high,"
                    + "bm25_mean_ms,bm25_std_ms,bm25_ci95_ms,bm25_qps,bm25_qps_low,bm25_qps_high,"
                    + "adj_mean_ms,adj_std_ms,adj_ci95_ms,adj_qps,adj_qps_low,adj_qps_high");
            for (IndexConfig cfg : profiles) {
                Path dir = workDir.resolve("cmp_" + cfg.docIdCodec + "_" + cfg.freqCodec + "_" + cfg.posCodec);
                long tw = System.nanoTime();
                new DiskIndexWriter(cfg).write(mem, dir);
                double writeMs = (System.nanoTime() - tw) / 1e6;

                long postings = size(dir, DiskIndexWriter.POSTINGS);
                long doclen = size(dir, DiskIndexWriter.DOCLEN);
                long names = size(dir, DiskIndexWriter.NAMES) + size(dir, DiskIndexWriter.NAMES_IDX);
                long dict = size(dir, DiskIndexWriter.DICT);
                long total = postings + doclen + names + dict;
                if (rawPostings < 0) {
                    rawPostings = postings;
                }
                double bitsPerPosting = postings * 8.0 / totalPostings;
                double ratio = (double) rawPostings / postings;

                try (DiskIndex disk = DiskIndex.open(dir)) {
                    SearchEngine eng = new SearchEngine(disk, analyzer, BM25Scorer.defaults());
                    Bench.Timing and = Bench.measure(WARMUP, ROUNDS, work.and.size(),
                            () -> runBoolean(eng, work.and));
                    Bench.Timing bm25 = Bench.measure(WARMUP, ROUNDS, work.bm25.size(),
                            () -> runExhaustive(eng, work.bm25));
                    Bench.Timing adj = Bench.measure(WARMUP, ROUNDS, work.adj.size(),
                            () -> runBoolean(eng, work.adj));
                    w.printf("%s,%d,%s,%s,%s,%d,%d,%d,%d,%d,%.3f,%.3f,%.1f,"
                                    + "%.4f,%.4f,%.4f,%.1f,%.1f,%.1f,"
                                    + "%.4f,%.4f,%.4f,%.1f,%.1f,%.1f,"
                                    + "%.4f,%.4f,%.4f,%.1f,%.1f,%.1f%n",
                            cfg.docIdCodec + "+" + cfg.freqCodec + "+" + cfg.posCodec, cfg.blockSize,
                            cfg.docIdCodec, cfg.freqCodec, cfg.posCodec,
                            postings, doclen, names, dict, total, bitsPerPosting, ratio, writeMs,
                            and.meanMs(), and.stdMs(), and.ci95Ms(), and.qps(), and.qpsLow(), and.qpsHigh(),
                            bm25.meanMs(), bm25.stdMs(), bm25.ci95Ms(), bm25.qps(), bm25.qpsLow(), bm25.qpsHigh(),
                            adj.meanMs(), adj.stdMs(), adj.ci95Ms(), adj.qps(), adj.qpsLow(), adj.qpsHigh());
                    System.out.printf("  [cmp] %-22s postings=%.1fMB ratio=%.2fx bits/posting=%.2f and=%.0fqps%n",
                            cfg.docIdCodec + "+" + cfg.posCodec, postings / 1e6, ratio, bitsPerPosting, and.qps());
                }
                deleteDir(dir);
            }
        }
    }

    private static void blockSizeSweep(MemoryIndex mem, StandardAnalyzer analyzer, Workload work,
                                       Path workDir, Path outDir, long totalPostings) throws IOException {
        int[] blockSizes = {16, 32, 64, 128, 256, 512};
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outDir.resolve("blocksize.csv")))) {
            w.println("blockSize,postings_bytes,total_bytes,bits_per_posting,"
                    + "and_mean_ms,and_std_ms,and_ci95_ms,and_qps,and_qps_low,and_qps_high");
            for (int bs : blockSizes) {
                IndexConfig cfg = new IndexConfig(bs, "vbyte", "bitpack", "vbyte");
                Path dir = workDir.resolve("bs_" + bs);
                new DiskIndexWriter(cfg).write(mem, dir);
                long postings = size(dir, DiskIndexWriter.POSTINGS);
                long total = postings + size(dir, DiskIndexWriter.DOCLEN)
                        + size(dir, DiskIndexWriter.NAMES) + size(dir, DiskIndexWriter.NAMES_IDX)
                        + size(dir, DiskIndexWriter.DICT);
                try (DiskIndex disk = DiskIndex.open(dir)) {
                    SearchEngine eng = new SearchEngine(disk, analyzer, BM25Scorer.defaults());
                    Bench.Timing t = Bench.measure(WARMUP, ROUNDS, work.and.size(),
                            () -> runBoolean(eng, work.and));
                    w.printf("%d,%d,%d,%.3f,%.4f,%.4f,%.4f,%.1f,%.1f,%.1f%n", bs, postings, total,
                            postings * 8.0 / totalPostings, t.meanMs(), t.stdMs(), t.ci95Ms(),
                            t.qps(), t.qpsLow(), t.qpsHigh());
                    System.out.printf("  [bs=%d] postings=%.1fMB and_mean=%.3fms%n",
                            bs, postings / 1e6, t.meanMs());
                }
                deleteDir(dir);
            }
        }
    }

    private static void backendComparison(MemoryIndex mem, StandardAnalyzer analyzer, Workload work,
                                          Path workDir, Path outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outDir.resolve("backend.csv")))) {
            w.println("backend,query_type,mean_ms,std_ms,ci95_ms,qps,qps_low,qps_high");
            SearchEngine memEng = new SearchEngine(mem, analyzer, BM25Scorer.defaults());
            QueryTask[] types = {
                    new QueryTask("AND", work.and, false),
                    new QueryTask("OR", work.or, false),
                    new QueryTask("ADJ", work.adj, false),
                    new QueryTask("NEAR", work.near, false),
                    new QueryTask("BM25", work.bm25, true),
            };

            measureBackend(w, "memory", memEng, types);

            DiskCfg[] configs = {
                    new DiskCfg("mmap-128m", new IndexConfig(128, "vbyte", "bitpack", "vbyte", 128L << 20)),
                    new DiskCfg("mmap-512m", new IndexConfig(128, "vbyte", "bitpack", "vbyte", 512L << 20)),
                    new DiskCfg("mmap-1024m", new IndexConfig(128, "vbyte", "bitpack", "vbyte", 1L << 30)),
            };
            for (DiskCfg cfg : configs) {
                Path dir = workDir.resolve("backend_" + cfg.name.replace('+', '_'));
                new DiskIndexWriter(cfg.config).write(mem, dir);
                try (DiskIndex disk = DiskIndex.open(dir)) {
                    SearchEngine diskEng = new SearchEngine(disk, analyzer, BM25Scorer.defaults());
                    measureBackend(w, cfg.name, diskEng, types);
                }
                deleteDir(dir);
            }
        }
    }

    private static void measureBackend(PrintWriter w, String name, SearchEngine eng, QueryTask[] types) {
        for (QueryTask qt : types) {
            Bench.Timing t = Bench.measure(WARMUP, ROUNDS, qt.queries().size(),
                    qt.exhaustive() ? () -> runExhaustive(eng, qt.queries())
                            : () -> runBoolean(eng, qt.queries()));
            w.printf("%s,%s,%.4f,%.4f,%.4f,%.1f,%.1f,%.1f%n",
                    name, qt.name(), t.meanMs(), t.stdMs(), t.ci95Ms(), t.qps(), t.qpsLow(), t.qpsHigh());
            System.out.printf("  [%s] %-7s mean=%.4fms qps=%.0f%n", name, qt.name(), t.meanMs(), t.qps());
        }
    }

    private static void recallQpsSweep(MemoryIndex mem, StandardAnalyzer analyzer, Workload work,
                                       Path outDir) throws IOException {
        SearchEngine eng = new SearchEngine(mem, analyzer, BM25Scorer.defaults());
        int k = 10;
        List<Set<Integer>> truth = new ArrayList<>();
        for (String q : work.bm25) {
            truth.add(topSet(eng.searchRankedExhaustive(q, k)));
        }
        double[] factors = {1.0, 1.02, 1.05, 1.1, 1.2, 1.4, 1.7, 2.0, 3.0};
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outDir.resolve("recall.csv")))) {
            w.println("wand_factor,recall_at10,mean_ms,std_ms,ci95_ms,qps,qps_low,qps_high");
            Bench.Timing be = Bench.measure(WARMUP, ROUNDS, work.bm25.size(), () -> runExhaustive(eng, work.bm25));
            w.printf("exhaustive,1.000,%.4f,%.4f,%.4f,%.1f,%.1f,%.1f%n",
                    be.meanMs(), be.stdMs(), be.ci95Ms(), be.qps(), be.qpsLow(), be.qpsHigh());
            System.out.printf("  [recall] exhaustive mean=%.4fms qps=%.0f%n", be.meanMs(), be.qps());
            for (double f : factors) {
                double recall = 0;
                for (int i = 0; i < work.bm25.size(); i++) {
                    Set<Integer> got = topSet(eng.searchRankedWand(work.bm25.get(i), k, f));
                    Set<Integer> exp = truth.get(i);
                    if (!exp.isEmpty()) {
                        Set<Integer> inter = new HashSet<>(got);
                        inter.retainAll(exp);
                        recall += (double) inter.size() / exp.size();
                    } else {
                        recall += 1.0;
                    }
                }
                recall /= work.bm25.size();
                final double ff = f;
                Bench.Timing t = Bench.measure(WARMUP, ROUNDS, work.bm25.size(),
                        () -> runWand(eng, work.bm25, ff));
                w.printf("%.2f,%.4f,%.4f,%.4f,%.4f,%.1f,%.1f,%.1f%n",
                        f, recall, t.meanMs(), t.stdMs(), t.ci95Ms(), t.qps(), t.qpsLow(), t.qpsHigh());
                System.out.printf("  [recall] F=%.2f recall@10=%.3f qps=%.0f%n", f, recall, t.qps());
            }
        }
    }

    private static long runBoolean(SearchEngine eng, List<String> queries) {
        long sink = 0;
        for (String q : queries) {
            List<ScoreDoc> r = eng.searchBoolean(q, 10);
            sink += r.size();
            if (!r.isEmpty()) {
                sink += r.get(0).docId;
            }
        }
        return sink;
    }

    private static long runExhaustive(SearchEngine eng, List<String> queries) {
        long sink = 0;
        for (String q : queries) {
            sink += eng.searchRankedExhaustive(q, 10).size();
        }
        return sink;
    }

    private static long runWand(SearchEngine eng, List<String> queries, double f) {
        long sink = 0;
        for (String q : queries) {
            sink += eng.searchRankedWand(q, 10, f).size();
        }
        return sink;
    }

    private static Set<Integer> topSet(List<ScoreDoc> docs) {
        Set<Integer> s = new HashSet<>();
        for (ScoreDoc d : docs) {
            s.add(d.docId);
        }
        return s;
    }

    private static long size(Path dir, String file) throws IOException {
        Path p = dir.resolve(file);
        return Files.exists(p) ? Files.size(p) : 0;
    }

    private static void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var s = Files.walk(dir)) {
            s.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static int intOpt(Map<String, String> opt, String key, int def) {
        return opt.containsKey(key) ? Integer.parseInt(opt.get(key)) : def;
    }
}
