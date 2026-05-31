package ru.itmo.search.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.itmo.search.TestIndexes;

class MemoryIndexTest {

    @Test
    void postingsAndPositions() {
        MemoryIndex idx = TestIndexes.build("a b c a", "b c d", "a c");
        assertEquals(3, idx.numDocs());
        assertEquals(2, idx.docFreq("a"));
        assertEquals(3, idx.docFreq("c"));
        assertEquals(0, idx.docFreq("zzz"));
        assertNull(idx.cursor("zzz"));

        PositionalCursor a = (PositionalCursor) idx.cursor("a");
        Assertions.assertNotNull(a);
        assertEquals(0, a.nextDoc());
        assertEquals(2, a.freq());
        assertArrayEquals(new int[]{0, 3}, java.util.Arrays.copyOf(a.positions(), a.freq()));
        assertEquals(2, a.nextDoc());
        assertEquals(PostingsCursor.NO_MORE, a.nextDoc());
    }

    @Test
    void advanceUsesSkippingAndMatchesLinearScan() {
        Random rng = new Random(42);
        StringBuilder body = new StringBuilder();
        // Term "x" lands in a controlled subset of 2000 docs.
        boolean[] hasX = new boolean[2000];
        String[] bodies = new String[2000];
        for (int d = 0; d < 2000; d++) {
            boolean x = rng.nextInt(5) == 0;
            hasX[d] = x;
            bodies[d] = x ? "x y" : "y z";
        }
        MemoryIndex idx = TestIndexes.build(bodies);
        for (int trial = 0; trial < 500; trial++) {
            int target = rng.nextInt(2000);
            PostingsCursor c = idx.cursor("x");
            Assertions.assertNotNull(c);
            c.nextDoc();
            int got = c.advance(target);
            int expected = PostingsCursor.NO_MORE;
            for (int d = target; d < 2000; d++) {
                if (hasX[d]) {
                    expected = d;
                    break;
                }
            }
            assertEquals(expected, got);
        }
    }

    @Test
    void collectionStats() {
        MemoryIndex idx = TestIndexes.build("a b c", "a a");
        assertEquals(5, idx.totalTokens());
        assertEquals(2.5, idx.avgDocLength(), 1e-9);
        assertEquals(3, idx.docLength(0));
        assertEquals(2, idx.docLength(1));
        assertEquals("doc1", idx.docName(1));
        assertEquals(List.of(0, 1), TestIndexes.collect(Objects.requireNonNull(idx.cursor("a"))));
    }
}
