package ru.itmo.search.index;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import ru.itmo.search.TestIndexes;
import ru.itmo.search.index.op.AndCursor;
import ru.itmo.search.index.op.AndNotCursor;
import ru.itmo.search.index.op.OrCursor;
import ru.itmo.search.index.op.ProximityCursor;

class OperatorsTest {

    private final MemoryIndex idx = TestIndexes.build(
            "a b c a",   // 0
            "b c d",     // 1
            "a c",       // 2
            "d e a b");  // 3

    @Test
    void and() {
        AndCursor and = new AndCursor(idx.cursor("a"), idx.cursor("b"));
        assertEquals(List.of(0, 3), TestIndexes.collect(and));
    }

    @Test
    void or() {
        OrCursor or = new OrCursor(idx.cursor("a"), idx.cursor("d"));
        assertEquals(List.of(0, 1, 2, 3), TestIndexes.collect(or));
    }

    @Test
    void andNot() {
        AndNotCursor c = new AndNotCursor(idx.cursor("a"), idx.cursor("b"));
        assertEquals(List.of(2), TestIndexes.collect(c));
    }

    @Test
    void adjacentPhrase() {
        // a immediately followed by b
        ProximityCursor c = ProximityCursor.adj(
                (PositionalCursor) idx.cursor("a"), (PositionalCursor) idx.cursor("b"), 1);
        assertEquals(List.of(0, 3), TestIndexes.collect(c));
    }

    @Test
    void adjacentBC() {
        ProximityCursor c = ProximityCursor.adj(
                (PositionalCursor) idx.cursor("b"), (PositionalCursor) idx.cursor("c"), 1);
        assertEquals(List.of(0, 1), TestIndexes.collect(c));
    }

    @Test
    void nearUnordered() {
        ProximityCursor c = ProximityCursor.near(
                (PositionalCursor) idx.cursor("a"), (PositionalCursor) idx.cursor("c"), 2);
        assertEquals(List.of(0, 2), TestIndexes.collect(c));
    }

    @Test
    void andMatchesBruteForceOnRandomCorpus() {
        Random rng = new Random(99);
        int n = 3000;
        String[] bodies = new String[n];
        List<Integer> pDocs = new ArrayList<>();
        List<Integer> qDocs = new ArrayList<>();
        for (int d = 0; d < n; d++) {
            StringBuilder sb = new StringBuilder("filler");
            boolean p = rng.nextInt(3) == 0;
            boolean q = rng.nextInt(4) == 0;
            if (p) {
                sb.append(" p");
                pDocs.add(d);
            }
            if (q) {
                sb.append(" q");
                qDocs.add(d);
            }
            bodies[d] = sb.toString();
        }
        MemoryIndex big = TestIndexes.build(bodies);
        TreeSet<Integer> expected = new TreeSet<>(pDocs);
        expected.retainAll(new TreeSet<>(qDocs));

        AndCursor and = new AndCursor(big.cursor("p"), big.cursor("q"));
        assertEquals(new ArrayList<>(expected), TestIndexes.collect(and));
    }
}
