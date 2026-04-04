package algo;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class LSHIndex {

    private final int shingleSize;
    private final int numHashFunctions;
    private final int numBands;
    private final int rowsPerBand;
    private final double jaccardThreshold;

    private final long[] hashA;
    private final long[] hashB;
    private static final long LARGE_PRIME_1 = 2_147_483_647L;
    private static final long LARGE_PRIME_2 = 1_000_000_007L;

    private final Path storageDir;
    private final List<Long2ObjectOpenHashMap<List<Integer>>> bandTables;
    private final List<int[]> signatures;
    private int docCount;

    public LSHIndex(Path storageDir, int shingleSize, int numBands, int rowsPerBand,
                    double jaccardThreshold) throws IOException {
        this(storageDir, shingleSize, numBands, rowsPerBand, jaccardThreshold, new Random(42));
    }

    public LSHIndex(Path storageDir, int shingleSize, int numBands, int rowsPerBand,
                    double jaccardThreshold, Random rng) throws IOException {
        this.storageDir = storageDir;
        this.shingleSize = shingleSize;
        this.numBands = numBands;
        this.rowsPerBand = rowsPerBand;
        this.numHashFunctions = numBands * rowsPerBand;
        this.jaccardThreshold = jaccardThreshold;

        this.hashA = new long[numHashFunctions];
        this.hashB = new long[numHashFunctions];
        for (int i = 0; i < numHashFunctions; i++) {
            hashA[i] = 1 + Math.abs(rng.nextLong()) % (LARGE_PRIME_1 - 1);
            hashB[i] = Math.abs(rng.nextLong()) % LARGE_PRIME_1;
        }

        this.bandTables = new ArrayList<>();
        for (int b = 0; b < numBands; b++) {
            bandTables.add(new Long2ObjectOpenHashMap<>());
        }
        this.signatures = new ArrayList<>();
        this.docCount = 0;
    }

    public int add(String text) throws IOException {
        int id = docCount++;

        Set<Long> shingles = shingle(text);
        int[] sig = computeMinHash(shingles);
        signatures.add(sig);

        for (int b = 0; b < numBands; b++) {
            long bandHash = bandHash(sig, b);
            bandTables.get(b).computeIfAbsent(bandHash, k -> new ArrayList<>()).add(id);
        }
        return id;
    }

    public Set<Integer> queryCandidates(String text) {
        Set<Long> shingles = shingle(text);
        int[] sig = computeMinHash(shingles);

        Set<Integer> candidates = new HashSet<>();
        for (int b = 0; b < numBands; b++) {
            long bandHash = bandHash(sig, b);
            List<Integer> bucket = bandTables.get(b).get(bandHash);
            if (bucket != null) {
                candidates.addAll(bucket);
            }
        }
        return candidates;
    }

    public List<Integer> querySimilar(String text) {
        Set<Long> shingles = shingle(text);
        int[] sig = computeMinHash(shingles);

        Set<Integer> candidates = new HashSet<>();
        for (int b = 0; b < numBands; b++) {
            long bandHash = bandHash(sig, b);
            List<Integer> bucket = bandTables.get(b).get(bandHash);
            if (bucket != null) {
                candidates.addAll(bucket);
            }
        }

        List<Integer> result = new ArrayList<>();
        for (int cid : candidates) {
            if (estimatedJaccard(sig, signatures.get(cid)) >= jaccardThreshold) {
                result.add(cid);
            }
        }
        return result;
    }

    public List<int[]> findAllDuplicates() {
        Set<Long> seenPairs = new HashSet<>();
        List<int[]> duplicates = new ArrayList<>();
        int n = docCount;

        for (int b = 0; b < numBands; b++) {
            for (List<Integer> bucket : bandTables.get(b).values()) {
                if (bucket.size() < 2) continue;
                for (int i = 0; i < bucket.size(); i++) {
                    for (int j = i + 1; j < bucket.size(); j++) {
                        int a = bucket.get(i);
                        int bId = bucket.get(j);
                        long pairKey = (long) Math.min(a, bId) * n + Math.max(a, bId);
                        if (seenPairs.add(pairKey)) {
                            double sim = estimatedJaccard(signatures.get(a), signatures.get(bId));
                            if (sim >= jaccardThreshold) {
                                duplicates.add(new int[]{a, bId});
                            }
                        }
                    }
                }
            }
        }
        return duplicates;
    }

    public List<int[]> findAllDuplicatesBruteForce(List<String> documents) {
        if (documents.size() != docCount) {
            throw new IllegalArgumentException("documents.size() must equal index size");
        }
        List<Set<Long>> shingleSets = new ArrayList<>();
        for (String text : documents) {
            shingleSets.add(shingle(text));
        }

        List<int[]> duplicates = new ArrayList<>();
        for (int i = 0; i < docCount; i++) {
            for (int j = i + 1; j < docCount; j++) {
                double sim = exactJaccard(shingleSets.get(i), shingleSets.get(j));
                if (sim >= jaccardThreshold) {
                    duplicates.add(new int[]{i, j});
                }
            }
        }
        return duplicates;
    }

    Set<Long> shingle(String text) {
        Set<Long> shingles = new HashSet<>();
        String lower = text.toLowerCase();
        for (int i = 0; i <= lower.length() - shingleSize; i++) {
            long hash = 0;
            for (int j = 0; j < shingleSize; j++) {
                hash = hash * 31 + lower.charAt(i + j);
            }
            shingles.add(hash);
        }
        return shingles;
    }

    int[] computeMinHash(Set<Long> shingles) {
        int[] sig = new int[numHashFunctions];
        Arrays.fill(sig, Integer.MAX_VALUE);
        for (long s : shingles) {
            long sUnsigned = s & Long.MAX_VALUE;
            for (int i = 0; i < numHashFunctions; i++) {
                int h = (int) ((hashA[i] * sUnsigned + hashB[i]) % LARGE_PRIME_1);
                if (h < sig[i]) {
                    sig[i] = h;
                }
            }
        }
        return sig;
    }

    private long bandHash(int[] sig, int bandIndex) {
        long h = 0;
        int start = bandIndex * rowsPerBand;
        for (int r = 0; r < rowsPerBand; r++) {
            h = h * LARGE_PRIME_2 + sig[start + r];
        }
        return h;
    }

    public static double estimatedJaccard(int[] sig1, int[] sig2) {
        int match = 0;
        for (int i = 0; i < sig1.length; i++) {
            if (sig1[i] == sig2[i]) match++;
        }
        return (double) match / sig1.length;
    }

    public static double exactJaccard(Set<Long> a, Set<Long> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        int intersection = 0;
        Set<Long> smaller = a.size() <= b.size() ? a : b;
        Set<Long> larger = a.size() <= b.size() ? b : a;
        for (long s : smaller) {
            if (larger.contains(s)) intersection++;
        }
        int union = a.size() + b.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    public int size() {
        return docCount;
    }

    public int[] getSignature(int id) {
        return signatures.get(id);
    }
}
