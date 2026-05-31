package ru.itmo.search.benchmarks;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.itmo.search.analysis.StandardAnalyzer;
import ru.itmo.search.corpus.JsonlCorpusReader;
import ru.itmo.search.index.IndexBuilder;
import ru.itmo.search.index.InvertedIndex;
import ru.itmo.search.index.MemoryIndex;
import ru.itmo.search.index.disk.DiskIndex;
import ru.itmo.search.index.disk.DiskIndexWriter;
import ru.itmo.search.index.disk.IndexConfig;
import ru.itmo.search.rank.BM25Scorer;
import ru.itmo.search.rank.ScoreDoc;
import ru.itmo.search.rank.SearchEngine;

public final class ProfileHarness {

    private ProfileHarness() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            usage();
            return;
        }
        String command = args[0];
        Map<String, String> opt = parseOptions(args, 1);
        switch (command) {
            case "build" -> build(opt);
            case "query" -> query(opt);
            default -> usage();
        }
    }

    private static void build(Map<String, String> opt) throws IOException {
        Path corpus = Path.of(require(opt, "corpus"));
        Path out = Path.of(require(opt, "out"));
        int maxDocs = intOpt(opt, "maxDocs", 500_000);
        StandardAnalyzer analyzer = new StandardAnalyzer(1, Set.of());
        IndexBuilder builder = new IndexBuilder(analyzer);
        long t0 = System.nanoTime();
        new JsonlCorpusReader(corpus).indexInto(builder, maxDocs);
        MemoryIndex mem = builder.build();
        new DiskIndexWriter(configFrom(opt)).write(mem, out);
        System.out.printf("profile-build indexed %d docs, %d terms into %s in %.1fs%n",
                mem.numDocs(), mem.terms().size(), out, (System.nanoTime() - t0) / 1e9);
    }

    private static void query(Map<String, String> opt) throws IOException {
        Path indexDir = Path.of(require(opt, "index"));
        String op = opt.getOrDefault("op", "and").toLowerCase();
        int queries = intOpt(opt, "queries", 160);
        int seconds = intOpt(opt, "seconds", 120);
        long seed = longOpt(opt, "seed", 42);
        StandardAnalyzer analyzer = new StandardAnalyzer(1, Set.of());
        try (DiskIndex index = DiskIndex.open(indexDir)) {
            Workload work = Workload.build(index, queries, seed, op);
            SearchEngine engine = new SearchEngine(index, analyzer, BM25Scorer.defaults());
            List<String> selected = switch (op) {
                case "and" -> work.and;
                case "or" -> work.or;
                case "adj" -> work.adj;
                case "near" -> work.near;
                case "bm25" -> work.bm25;
                default -> throw new IllegalArgumentException("unknown op: " + op);
            };
            long warmUntil = System.nanoTime() + 3_000_000_000L;
            while (System.nanoTime() < warmUntil) {
                run(engine, selected, op);
            }
            long deadline = System.nanoTime() + seconds * 1_000_000_000L;
            long rounds = 0;
            long sink = 0;
            while (System.nanoTime() < deadline) {
                sink += run(engine, selected, op);
                rounds++;
            }
            System.out.printf("profile-query op=%s queries=%d rounds=%d sink=%d%n",
                    op, selected.size(), rounds, sink);
        }
    }

    private static long run(SearchEngine engine, List<String> queries, String op) {
        long sink = 0;
        for (String q : queries) {
            List<ScoreDoc> r = "bm25".equals(op)
                    ? engine.searchRankedWand(q, 10, 1.0)
                    : engine.searchBoolean(q, 10);
            sink += r.size();
            if (!r.isEmpty()) {
                sink += r.get(0).docId;
            }
        }
        return sink;
    }

    private static IndexConfig configFrom(Map<String, String> opt) {
        return new IndexConfig(
                intOpt(opt, "blockSize", 256),
                opt.getOrDefault("docIdCodec", "pfor"),
                opt.getOrDefault("freqCodec", "bitpack"),
                opt.getOrDefault("posCodec", "pfor"),
                longOpt(opt, "segmentSizeBytes", IndexConfig.DEFAULT_SEGMENT_SIZE_BYTES));
    }

    private static Map<String, String> parseOptions(String[] args, int from) {
        Map<String, String> opt = new HashMap<>();
        for (int i = from; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    opt.put(key, args[++i]);
                } else {
                    opt.put(key, "true");
                }
            }
        }
        return opt;
    }

    private static String require(Map<String, String> opt, String key) {
        String value = opt.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing required --" + key);
        }
        return value;
    }

    private static int intOpt(Map<String, String> opt, String key, int def) {
        return opt.containsKey(key) ? Integer.parseInt(opt.get(key)) : def;
    }

    private static long longOpt(Map<String, String> opt, String key, long def) {
        return opt.containsKey(key) ? Long.parseLong(opt.get(key)) : def;
    }

    private static void usage() {
        System.out.println("""
                usage:
                  ProfileHarness build --corpus data/wikipedia.jsonl --out target/profile-balanced-v2-pfor-bitpack-bs256-index [--maxDocs 500000]
                  ProfileHarness query --index target/profile-balanced-v2-pfor-bitpack-bs256-index --op and|or|adj|near|bm25 [--seconds 120]
                """);
    }
}
