package algo;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LSHIndexTest {

    private static final Path BASE_DIR = Path.of(System.getProperty("java.io.tmpdir"),
            "lsh_test_" + ProcessHandle.current().pid());

    private Path testDir;
    private int testCounter = 0;

    @BeforeEach
    void setUp() throws IOException {
        testDir = BASE_DIR.resolve("t" + (testCounter++));
        Files.createDirectories(testDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(testDir)) {
            try (var stream = Files.walk(testDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (Files.exists(BASE_DIR)) {
            try (var stream = Files.walk(BASE_DIR)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    void exactDuplicate() throws IOException {
        var lsh = new LSHIndex(testDir.resolve("idx"), 3, 20, 5, 0.5);
        int id0 = lsh.add("the quick brown fox jumps over the lazy dog");
        int id1 = lsh.add("the quick brown fox jumps over the lazy dog");

        List<int[]> dups = lsh.findAllDuplicates();
        assertTrue(dups.size() >= 1, "Exact duplicates must be found");
        assertEquals(id0, dups.get(0)[0]);
        assertEquals(id1, dups.get(0)[1]);
    }

    @Test
    void nearDuplicate() throws IOException {
        var lsh = new LSHIndex(testDir.resolve("idx"), 3, 20, 5, 0.5);
        lsh.add("the quick brown fox jumps over the lazy dog");
        lsh.add("the quick brown fox leaps over the lazy dog");

        List<int[]> dups = lsh.findAllDuplicates();
        assertTrue(dups.size() >= 1, "Near-duplicate (1 word diff) should be detected");
    }

    @Test
    void completelyDifferentTexts() throws IOException {
        var lsh = new LSHIndex(testDir.resolve("idx"), 3, 20, 5, 0.5);
        lsh.add("the quick brown fox jumps over the lazy dog");
        lsh.add("абвгдежзиклмнопрстуфхцчшщъыьэюя");

        List<int[]> dups = lsh.findAllDuplicates();
        assertEquals(0, dups.size(), "Completely different texts should not be duplicates");
    }

    @Test
    void querySimilarFindsMatch() throws IOException {
        var lsh = new LSHIndex(testDir.resolve("idx"), 3, 20, 5, 0.3);
        lsh.add("machine learning is a subset of artificial intelligence");
        lsh.add("deep learning is a subset of machine learning");
        lsh.add("the weather is nice today");

        List<Integer> similar = lsh.querySimilar("machine learning is a part of artificial intelligence");
        assertTrue(similar.contains(0), "Should find doc 0 as similar");
    }

    @Test
    void addAndSize() throws IOException {
        var lsh = new LSHIndex(testDir.resolve("idx"), 3, 10, 3, 0.5);
        assertEquals(0, lsh.size());
        lsh.add("hello world");
        lsh.add("foo bar baz");
        assertEquals(2, lsh.size());
    }

    @Test
    void bruteForceAgreement() throws IOException {
        Random rng = new Random(777);
        var lsh = new LSHIndex(testDir.resolve("idx"), 3, 30, 5, 0.5, rng);

        List<String> docs = List.of(
                "algorithms and data structures are fundamental topics in computer science education",
                "algorithms and data structures are fundamental topics in CS education",
                "algorithms & data structures are fundamental topics in computer science",
                "data structures and algorithms are fundamental in computer science education",
                "the stock market had a significant drop today due to economic concerns",
                "recipe for chocolate cake: mix flour sugar cocoa butter eggs and milk"
        );
        for (String doc : docs) {
            lsh.add(doc);
        }

        List<int[]> bfDups = lsh.findAllDuplicatesBruteForce(docs);
        List<int[]> lshDups = lsh.findAllDuplicates();

        Set<Long> bfSet = pairsToSet(bfDups);
        Set<Long> lshSet = pairsToSet(lshDups);

        for (long pair : lshSet) {
            assertTrue(bfSet.contains(pair), "LSH returned a pair not in brute-force result");
        }

        if (!bfSet.isEmpty()) {
            double recall = (double) lshSet.size() / bfSet.size();
            assertTrue(recall > 0.3,
                    "Recall too low: " + recall + " (" + lshSet.size() + "/" + bfSet.size() + ")");
        }
    }

    @Test
    void randomStressTest() throws IOException {
        Random rng = new Random(42);
        var lsh = new LSHIndex(testDir.resolve("idx"), 3, 20, 5, 0.5, rng);

        String[] bases = {
                "the quick brown fox jumps over the lazy dog",
                "a stitch in time saves nine",
                "to be or not to be that is the question",
                "all that glitters is not gold",
                "the pen is mightier than the sword"
        };

        for (String base : bases) {
            lsh.add(base);
            for (int i = 0; i < 5; i++) {
                lsh.add(mutateText(base, rng));
            }
        }
        for (int i = 0; i < 50; i++) {
            lsh.add(randomText(rng, 40 + rng.nextInt(60)));
        }

        assertEquals(80, lsh.size());

        List<int[]> dups = lsh.findAllDuplicates();
        for (int[] pair : dups) {
            double sim = LSHIndex.estimatedJaccard(
                    lsh.getSignature(pair[0]), lsh.getSignature(pair[1]));
            assertTrue(sim >= 0.5 - 0.01,
                    "Pair (" + pair[0] + "," + pair[1] + ") sim=" + sim + " below threshold");
        }
    }

    @Test
    void unicodeTexts() throws IOException {
        var lsh = new LSHIndex(testDir.resolve("idx"), 3, 20, 5, 0.5);
        lsh.add("алгоритмы и структуры данных очень важны для программирования");
        lsh.add("алгоритмы и структуры данных крайне важны для программирования");
        lsh.add("日本語のテストテキストです。これは完全に異なるテキストです。");

        List<int[]> dups = lsh.findAllDuplicates();
        boolean foundRussianPair = dups.stream().anyMatch(
                p -> (p[0] == 0 && p[1] == 1) || (p[0] == 1 && p[1] == 0));
        assertTrue(foundRussianPair, "Similar Russian texts should be detected as duplicates");
    }

    @Test
    void highThresholdReducesDuplicates() throws IOException {
        var strict = new LSHIndex(testDir.resolve("strict"), 3, 20, 5, 0.9);
        var relaxed = new LSHIndex(testDir.resolve("relaxed"), 3, 20, 5, 0.3);

        String[] texts = {
                "the quick brown fox",
                "the quick brown dog",
                "the slow brown fox",
                "completely different text here nothing alike"
        };
        for (String t : texts) {
            strict.add(t);
            relaxed.add(t);
        }

        assertTrue(relaxed.findAllDuplicates().size() >= strict.findAllDuplicates().size(),
                "Relaxed threshold should find at least as many duplicates");
    }

    @Test
    void shinglingProducesExpectedSets() throws IOException {
        var lsh = new LSHIndex(testDir.resolve("idx"), 3, 5, 2, 0.5);
        Set<Long> shingles = lsh.shingle("abcde");
        assertEquals(3, shingles.size());
    }

    @Test
    void emptyAndShortTexts() throws IOException {
        var lsh = new LSHIndex(testDir.resolve("idx"), 3, 10, 3, 0.5);
        lsh.add("");
        lsh.add("ab");
        lsh.add("abc");
        assertEquals(3, lsh.size());
        lsh.findAllDuplicates();
    }


    private static String mutateText(String text, Random rng) {
        String[] words = text.split(" ");
        int idx = rng.nextInt(words.length);
        words[idx] = randomWord(rng);
        return String.join(" ", words);
    }

    private static String randomText(Random rng, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + rng.nextInt(26)));
        }
        return sb.toString();
    }

    private static String randomWord(Random rng) {
        int len = 3 + rng.nextInt(7);
        char[] chars = new char[len];
        for (int i = 0; i < len; i++) chars[i] = (char) ('a' + rng.nextInt(26));
        return new String(chars);
    }

    private Set<Long> pairsToSet(List<int[]> pairs) {
        Set<Long> set = new HashSet<>();
        for (int[] p : pairs) {
            long a = Math.min(p[0], p[1]);
            long b = Math.max(p[0], p[1]);
            set.add(a * 100_000 + b);
        }
        return set;
    }
}
