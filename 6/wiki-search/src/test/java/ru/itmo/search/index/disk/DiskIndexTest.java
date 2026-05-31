package ru.itmo.search.index.disk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itmo.search.analysis.StandardAnalyzer;
import ru.itmo.search.index.IndexBuilder;
import ru.itmo.search.index.MemoryIndex;
import ru.itmo.search.index.PositionalCursor;
import ru.itmo.search.index.PostingsCursor;
import ru.itmo.search.rank.BM25Scorer;
import ru.itmo.search.rank.ScoreDoc;
import ru.itmo.search.rank.SearchEngine;

class DiskIndexTest {

    private MemoryIndex buildRandom(long seed, int n) {
        Random rng = new Random(seed);
        String[] vocab = new String[300];
        for (int i = 0; i < vocab.length; i++) {
            vocab[i] = "term" + i;
        }
        IndexBuilder b = new IndexBuilder(new StandardAnalyzer(1, java.util.Set.of()));
        for (int d = 0; d < n; d++) {
            StringBuilder sb = new StringBuilder();
            int len = 5 + rng.nextInt(80);
            for (int i = 0; i < len; i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                int t = (int) (vocab.length * Math.pow(rng.nextDouble(), 3));
                sb.append(vocab[Math.min(t, vocab.length - 1)]);
            }
            b.addDocument("Article " + d, sb.toString());
        }
        return b.build();
    }

    private static List<int[]> dump(PostingsCursor c) {
        List<int[]> rows = new ArrayList<>();
        for (int d = c.nextDoc(); d != PostingsCursor.NO_MORE; d = c.nextDoc()) {
            int[] pos = ((PositionalCursor) c).positions();
            int[] row = new int[2 + c.freq()];
            row[0] = d;
            row[1] = c.freq();
            System.arraycopy(pos, 0, row, 2, c.freq());
            rows.add(row);
        }
        return rows;
    }

    private static final IndexConfig[] CONFIGS = {
            new IndexConfig(128, "raw", "raw", "raw"),
            new IndexConfig(128, "vbyte", "vbyte", "vbyte"),
            new IndexConfig(64, "bitpack", "bitpack", "bitpack"),
            new IndexConfig(128, "pfor", "vbyte", "pfor"),
            new IndexConfig(32, "pfor", "bitpack", "pfor"),
    };

    @Test
    void diskCursorsMatchMemoryAcrossConfigs(@TempDir Path tmp) throws IOException {
        MemoryIndex mem = buildRandom(13, 1500);
        List<String> sample = new ArrayList<>(mem.terms());

        int cfgIdx = 0;
        for (IndexConfig cfg : CONFIGS) {
            Path dir = tmp.resolve("idx" + cfgIdx++);
            new DiskIndexWriter(cfg).write(mem, dir);
            try (DiskIndex disk = DiskIndex.open(dir)) {
                assertEquals(mem.numDocs(), disk.numDocs(), cfg.toString());
                assertEquals(mem.totalTokens(), disk.totalTokens(), cfg.toString());

                for (String term : sample) {
                    assertEquals(mem.docFreq(term), disk.docFreq(term), cfg + " df " + term);
                    List<int[]> expected = dump(Objects.requireNonNull(mem.cursor(term)));
                    List<int[]> actual = dump(Objects.requireNonNull(disk.cursor(term)));
                    assertEquals(expected.size(), actual.size(), cfg + " size " + term);
                    for (int i = 0; i < expected.size(); i++) {
                        assertArrayEquals(expected.get(i), actual.get(i), cfg + " row " + term);
                    }
                }
                for (int d = 0; d < mem.numDocs(); d += 37) {
                    assertEquals(mem.docLength(d), disk.docLength(d), cfg + " len");
                    assertEquals(mem.docName(d), disk.docName(d), cfg + " name");
                }
            }
        }
    }

    @Test
    void diskAdvanceMatchesMemory(@TempDir Path tmp) throws IOException {
        MemoryIndex mem = buildRandom(7, 2000);
        Path dir = tmp.resolve("idx");
        new DiskIndexWriter(IndexConfig.defaults()).write(mem, dir);
        Random rng = new Random(5);
        try (DiskIndex disk = DiskIndex.open(dir)) {
            for (String term : new String[]{"term0", "term1", "term5", "term50", "term200"}) {
                for (int trial = 0; trial < 200; trial++) {
                    int target = rng.nextInt(mem.numDocs());
                    PostingsCursor mc = mem.cursor(term);
                    PostingsCursor dc = disk.cursor(term);
                    Assertions.assertNotNull(mc);
                    mc.nextDoc();
                    Assertions.assertNotNull(dc);
                    dc.nextDoc();
                    assertEquals(mc.advance(target), dc.advance(target), term + " target=" + target);
                }
            }
        }
    }

    @Test
    void endToEndBooleanQueriesMatch(@TempDir Path tmp) throws IOException {
        MemoryIndex mem = buildRandom(21, 1200);
        Path dir = tmp.resolve("idx");
        new DiskIndexWriter(IndexConfig.defaults()).write(mem, dir);
        StandardAnalyzer analyzer = new StandardAnalyzer(1, java.util.Set.of());

        try (DiskIndex disk = DiskIndex.open(dir)) {
            SearchEngine memEngine = new SearchEngine(mem, analyzer, BM25Scorer.defaults());
            SearchEngine diskEngine = new SearchEngine(disk, analyzer, BM25Scorer.defaults());
            String[] queries = {
                    "term0 AND term1", "term0 OR term2", "term0 AND NOT term3",
                    "\"term0 term1\"", "term0 NEAR/5 term2", "(term0 OR term1) AND term2"
            };
            for (String q : queries) {
                List<ScoreDoc> a = memEngine.searchBoolean(q, 20);
                List<ScoreDoc> b = diskEngine.searchBoolean(q, 20);
                assertEquals(a.size(), b.size(), q);
                for (int i = 0; i < a.size(); i++) {
                    assertEquals(a.get(i).docId, b.get(i).docId, q + " #" + i);
                    assertEquals(a.get(i).score, b.get(i).score, 1e-9, q + " score #" + i);
                }
            }
        }
    }
}
