package algo;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemHashTableTest {

    private static final Path TEST_DIR = Path.of(System.getProperty("java.io.tmpdir"), "fsht_test_" + ProcessHandle.current().pid());
    private FileSystemHashTable table;

    @BeforeEach
    void setUp() throws IOException {
        table = new FileSystemHashTable(TEST_DIR, 4096);
    }

    @AfterEach
    void tearDown() throws IOException {
        table.destroy();
    }

    @Test
    void putAndGet() throws IOException {
        table.put("hello", "world");
        assertEquals("world", table.get("hello"));
    }

    @Test
    void getMissing() throws IOException {
        assertNull(table.get("no-such-key"));
    }

    @Test
    void updateExistingKey() throws IOException {
        table.put("key", "v1");
        table.put("key", "v2");
        assertEquals("v2", table.get("key"));
    }

    @Test
    void deleteExistingKey() throws IOException {
        table.put("a", "1");
        assertTrue(table.delete("a"));
        assertNull(table.get("a"));
    }

    @Test
    void deleteNonExistingKey() throws IOException {
        assertFalse(table.delete("phantom"));
    }

    @Test
    void reuseTombstoneSlot() throws IOException {
        table.put("x", "1");
        table.delete("x");
        table.put("y", "2");
        assertNull(table.get("x"));
        assertEquals("2", table.get("y"));
    }

    @Test
    void collisionHandling() throws IOException {
        for (int i = 0; i < 100; i++) {
            table.put("key" + i, "val" + i);
        }
        for (int i = 0; i < 100; i++) {
            assertEquals("val" + i, table.get("key" + i));
        }
    }

    @Test
    void randomStressTest() throws IOException {
        Random rng = new Random(42);
        Map<String, String> reference = new HashMap<>();
        int ops = 5000;

        for (int i = 0; i < ops; i++) {
            int op = rng.nextInt(10);
            String key = "k" + rng.nextInt(500);

            if (op < 5) {
                String value = "v" + rng.nextInt(100000);
                table.put(key, value);
                reference.put(key, value);
            } else if (op < 8) {
                assertEquals(reference.get(key), table.get(key), "Mismatch for key: " + key);
            } else {
                boolean expected = reference.containsKey(key);
                boolean actual = table.delete(key);
                if (expected) {
                    assertTrue(actual);
                    reference.remove(key);
                }
            }
        }

        for (var entry : reference.entrySet()) {
            assertEquals(entry.getValue(), table.get(entry.getKey()),
                    "Final check mismatch for key: " + entry.getKey());
        }
    }

    @Test
    void persistenceAcrossInstances() throws IOException {
        table.put("persist", "data");
        var table2 = new FileSystemHashTable(TEST_DIR, 4096);
        assertEquals("data", table2.get("persist"));
    }

    @Test
    void emptyValueAndKey() throws IOException {
        table.put("", "empty-key");
        table.put("empty-value", "");
        assertEquals("empty-key", table.get(""));
        assertEquals("", table.get("empty-value"));
    }

    @Test
    void unicodeKeys() throws IOException {
        table.put("ключ", "значение");
        table.put("キー", "値");
        assertEquals("значение", table.get("ключ"));
        assertEquals("値", table.get("キー"));
    }

    @Test
    void splitIncreasesDepth() throws IOException {
        var small = new FileSystemHashTable(
                TEST_DIR.resolveSibling("fsht_split_" + ProcessHandle.current().pid()), 512);
        try {
            assertEquals(0, small.getGlobalDepth());
            for (int i = 0; i < 200; i++) {
                small.put("key" + i, "value" + i);
            }
            assertTrue(small.getGlobalDepth() > 0, "splits should have increased depth");
            assertTrue(small.getNumBuckets() > 1, "should have more than 1 bucket");

            for (int i = 0; i < 200; i++) {
                assertEquals("value" + i, small.get("key" + i), "key" + i);
            }
        } finally {
            small.destroy();
        }
    }

    @Test
    void splitWithDeletesAndUpdates() throws IOException {
        var small = new FileSystemHashTable(
                TEST_DIR.resolveSibling("fsht_splitdel_" + ProcessHandle.current().pid()), 512);
        try {
            for (int i = 0; i < 100; i++) {
                small.put("k" + i, "v" + i);
            }
            for (int i = 0; i < 50; i++) {
                small.delete("k" + i);
            }
            for (int i = 100; i < 200; i++) {
                small.put("k" + i, "v" + i);
            }
            for (int i = 0; i < 50; i++) {
                assertNull(small.get("k" + i));
            }
            for (int i = 50; i < 200; i++) {
                assertEquals("v" + i, small.get("k" + i), "k" + i);
            }
        } finally {
            small.destroy();
        }
    }
}
