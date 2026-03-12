package algo;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PerfectHashTableTest {

    @Test
    void basicLookup() {
        String[] keys = {"alpha", "beta", "gamma"};
        Integer[] values = {1, 2, 3};
        var pht = new PerfectHashTable<>(keys, values);

        assertEquals(1, pht.get("alpha"));
        assertEquals(2, pht.get("beta"));
        assertEquals(3, pht.get("gamma"));
    }

    @Test
    void missingKey() {
        String[] keys = {"a", "b"};
        Integer[] values = {10, 20};
        var pht = new PerfectHashTable<>(keys, values);

        assertNull(pht.get("c"));
        assertNull(pht.get(""));
    }

    @Test
    void singleElement() {
        var pht = new PerfectHashTable<>(new String[]{"only"}, new Integer[]{42});
        assertEquals(42, pht.get("only"));
        assertNull(pht.get("other"));
    }

    @Test
    void largeRandomDataset() {
        int n = 10_000;
        Random rng = new Random(123);
        Set<String> keySet = new LinkedHashSet<>();
        while (keySet.size() < n) {
            keySet.add("key_" + rng.nextLong());
        }
        String[] keys = keySet.toArray(new String[0]);
        Integer[] values = new Integer[n];
        for (int i = 0; i < n; i++) values[i] = i;

        var pht = new PerfectHashTable<>(keys, values);

        for (int i = 0; i < n; i++) {
            assertEquals(i, pht.get(keys[i]), "Mismatch at index " + i);
        }

        for (int i = 0; i < 1000; i++) {
            assertNull(pht.get("nonexistent_" + rng.nextLong()));
        }
    }

    @Test
    void noFalsePositives() {
        int n = 5000;
        String[] keys = new String[n];
        String[] values = new String[n];
        for (int i = 0; i < n; i++) {
            keys[i] = "item_" + i;
            values[i] = "val_" + i;
        }

        var pht = new PerfectHashTable<>(keys, values);

        for (int i = n; i < n + 5000; i++) {
            assertNull(pht.get("item_" + i));
        }
    }

    @Test
    void spaceUsage() {
        int n = 1000;
        String[] keys = new String[n];
        Integer[] values = new Integer[n];
        for (int i = 0; i < n; i++) {
            keys[i] = "k" + i;
            values[i] = i;
        }
        var pht = new PerfectHashTable<>(keys, values);

        // FKS guarantees O(n) total space; secondary space ≤ ~4n in expectation
        long secondary = pht.totalSecondarySpace();
        assertTrue(secondary < 10L * n,
                "Secondary space " + secondary + " exceeds 10n for n=" + n);
    }

    @Test
    void duplicateValuesForDifferentKeys() {
        String[] keys = {"a", "b", "c"};
        String[] values = {"same", "same", "same"};
        var pht = new PerfectHashTable<>(keys, values);

        assertEquals("same", pht.get("a"));
        assertEquals("same", pht.get("b"));
        assertEquals("same", pht.get("c"));
    }

    @Test
    void unicodeKeys() {
        String[] keys = {"ключ", "키", "鍵"};
        Integer[] values = {1, 2, 3};
        var pht = new PerfectHashTable<>(keys, values);

        assertEquals(1, pht.get("ключ"));
        assertEquals(2, pht.get("키"));
        assertEquals(3, pht.get("鍵"));
    }
}
