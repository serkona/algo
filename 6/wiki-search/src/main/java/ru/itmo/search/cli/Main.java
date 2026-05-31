package ru.itmo.search.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ru.itmo.search.analysis.Analyzer;
import ru.itmo.search.analysis.StandardAnalyzer;
import ru.itmo.search.corpus.CorpusReader;
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

public final class Main {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            usage();
            return;
        }
        String cmd = args[0];
        Map<String, String> opt = parseOptions(args, 1);
        switch (cmd) {
            case "build" -> build(opt);
            case "shell" -> shell(opt);
            case "search" -> search(opt);
            default -> usage();
        }
    }

    private static void build(Map<String, String> opt) throws IOException {
        Path out = Path.of(require(opt, "out"));
        IndexConfig config = configFrom(opt);
        Analyzer analyzer = new StandardAnalyzer();
        IndexBuilder builder = new IndexBuilder(analyzer);

        long t0 = System.nanoTime();
        int n = corpusFrom(opt).indexInto(builder, intOpt(opt, "maxDocs", Integer.MAX_VALUE));
        MemoryIndex mem = builder.build();
        System.out.printf("indexed %d docs, %d terms in %.1f s%n",
                n, mem.terms().size(), (System.nanoTime() - t0) / 1e9);

        long t1 = System.nanoTime();
        new DiskIndexWriter(config).write(mem, out);
        System.out.printf("wrote on-disk index (%s) to %s in %.1f s%n",
                config, out, (System.nanoTime() - t1) / 1e9);
    }

    private static void shell(Map<String, String> opt) throws IOException {
        InvertedIndex index = openIndex(opt);
        SearchEngine engine = engineFrom(index, opt);
        new SearchShell(engine, System.out,
                intOpt(opt, "k", 10),
                opt.getOrDefault("mode", "boolean"),
                doubleOpt(opt, "wandf", 1.0)).run();
        index.close();
    }

    private static void search(Map<String, String> opt) throws IOException {
        InvertedIndex index = openIndex(opt);
        SearchEngine engine = engineFrom(index, opt);
        String query = require(opt, "q");
        int k = intOpt(opt, "k", 10);
        String mode = opt.getOrDefault("mode", "boolean");
        double f = doubleOpt(opt, "wandf", 1.0);

        List<ScoreDoc> results = switch (mode) {
            case "wand" -> engine.searchRankedWand(query, k, f);
            case "exhaustive" -> engine.searchRankedExhaustive(query, k);
            default -> engine.searchBoolean(query, k);
        };
        int rank = 1;
        for (ScoreDoc d : results) {
            System.out.printf("%2d. [%.4f] #%d  %s%n", rank++, d.score, d.docId, d.name);
        }
        if (opt.containsKey("export")) {
            ResultExporter.export(Path.of(opt.get("export")), query, results);
            System.out.println("exported to " + opt.get("export"));
        }
        index.close();
    }

    private static InvertedIndex openIndex(Map<String, String> opt) throws IOException {
        if (opt.containsKey("index")) {
            return DiskIndex.open(Path.of(opt.get("index")));
        }
        throw new IllegalArgumentException("--index DIR is required");
    }

    private static CorpusReader corpusFrom(Map<String, String> opt) {
        if (opt.containsKey("corpus")) {
            return new JsonlCorpusReader(Path.of(opt.get("corpus")));
        }
        throw new IllegalArgumentException("--corpus FILE.jsonl is required");
    }

    private static IndexConfig configFrom(Map<String, String> opt) {
        return new IndexConfig(
                intOpt(opt, "blockSize", 128),
                opt.getOrDefault("docIdCodec", "pfor"),
                opt.getOrDefault("freqCodec", "vbyte"),
                opt.getOrDefault("posCodec", "pfor"),
                longOpt(opt, "segmentSizeBytes", IndexConfig.DEFAULT_SEGMENT_SIZE_BYTES));
    }

    private static SearchEngine engineFrom(InvertedIndex index, Map<String, String> opt) {
        BM25Scorer scorer = new BM25Scorer(doubleOpt(opt, "k1", 1.2), doubleOpt(opt, "b", 0.75));
        return new SearchEngine(index, new StandardAnalyzer(), scorer);
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
        String v = opt.get(key);
        if (v == null) {
            throw new IllegalArgumentException("missing required --" + key);
        }
        return v;
    }

    private static int intOpt(Map<String, String> opt, String key, int def) {
        return opt.containsKey(key) ? Integer.parseInt(opt.get(key)) : def;
    }

    private static long longOpt(Map<String, String> opt, String key, long def) {
        return opt.containsKey(key) ? Long.parseLong(opt.get(key)) : def;
    }

    private static double doubleOpt(Map<String, String> opt, String key, double def) {
        return opt.containsKey(key) ? Double.parseDouble(opt.get(key)) : def;
    }

    private static void usage() {
        System.out.println("""
                wiki-search — boolean + BM25 full-text search engine
                usage:
                  build  --out DIR --corpus FILE.jsonl
                         [--blockSize 128] [--segmentSizeBytes N]
                         [--docIdCodec pfor] [--freqCodec vbyte] [--posCodec pfor] [--maxDocs N]
                  shell  --index DIR [--k 10] [--mode boolean|wand|exhaustive] [--k1 1.2 --b 0.75]
                  search --index DIR --q "QUERY" [--k 10] [--mode ...] [--wandf 1.0] [--export OUT]""");
    }
}
