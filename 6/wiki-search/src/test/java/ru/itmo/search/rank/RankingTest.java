package ru.itmo.search.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import ru.itmo.search.TestIndexes;
import ru.itmo.search.analysis.StandardAnalyzer;
import ru.itmo.search.index.MemoryIndex;

class RankingTest {

    private SearchEngine engine(MemoryIndex idx) {
        return new SearchEngine(idx, new StandardAnalyzer(1, java.util.Set.of()), BM25Scorer.defaults());
    }

    @Test
    void resultsSortedByScoreDescending() {
        MemoryIndex idx = TestIndexes.build(
                "a", "a a a x x x x x x x", "a a", "b b", "a b");
        List<ScoreDoc> r = engine(idx).searchBoolean("a OR b", 10);
        for (int i = 1; i < r.size(); i++) {
            assertTrue(r.get(i - 1).score >= r.get(i).score, "must be descending");
        }
    }

    @Test
    void rarerTermDominatesScore() {
        String[] bodies = new String[50];
        for (int i = 0; i < 50; i++) {
            bodies[i] = "common";
        }
        bodies[7] = "common rare";
        List<ScoreDoc> r = engine(TestIndexes.build(bodies)).searchBoolean("common OR rare", 5);
        assertEquals(7, r.get(0).docId);
    }

    @Test
    void wandWithFactorOneEqualsExhaustive() {
        Random rng = new Random(2024);
        String[] vocab = {"a", "b", "c", "d", "e", "f", "g"};
        int n = 800;
        String[] bodies = new String[n];
        for (int d = 0; d < n; d++) {
            StringBuilder sb = new StringBuilder();
            int len = 3 + rng.nextInt(12);
            for (int i = 0; i < len; i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(vocab[rng.nextInt(vocab.length)]);
            }
            bodies[d] = sb.toString();
        }
        SearchEngine engine = engine(TestIndexes.build(bodies));

        for (String q : new String[]{"a b c", "a", "d e f g", "b c"}) {
            for (int k : new int[]{1, 5, 10, 25}) {
                List<ScoreDoc> exhaustive = engine.searchRankedExhaustive(q, k);
                List<ScoreDoc> wand = engine.searchRankedWand(q, k, 1.0);
                assertEquals(exhaustive.size(), wand.size(), "k=" + k + " q=" + q);
                for (int i = 0; i < exhaustive.size(); i++) {
                    assertEquals(exhaustive.get(i).docId, wand.get(i).docId, "doc mismatch q=" + q + " k=" + k);
                    assertEquals(exhaustive.get(i).score, wand.get(i).score, 1e-9, "score mismatch");
                }
            }
        }
    }

    @Test
    void wandRejectsFactorBelowOne() {
        SearchEngine engine = engine(TestIndexes.build("a b", "a", "b"));
        assertThrows(IllegalArgumentException.class, () -> engine.searchRankedWand("a b", 10, 0.9));
    }
}
