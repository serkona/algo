package algo;

import java.util.*;

public class PerfectHashTable<V> {

    private static final long LARGE_PRIME = 1_000_000_007L;

    private final int m;
    private final long a1, b1;
    private final long[] a2, b2;
    private final int[] secondarySizes;
    private final String[][] secondaryKeys;
    private final Object[][] secondaryValues;

    public PerfectHashTable(String[] keys, V[] values) {
        if (keys.length != values.length) throw new IllegalArgumentException("keys.length != values.length");

        int n = keys.length;
        this.m = Math.max(n, 1);

        Random rng = new Random(42);
        this.a1 = 1 + rng.nextLong(LARGE_PRIME - 1);
        this.b1 = rng.nextLong(LARGE_PRIME);

        List<List<Integer>> buckets = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            buckets.add(new ArrayList<>());
        }

        for (int i = 0; i < n; i++) {
            int h = primaryHash(keys[i]);
            buckets.get(h).add(i);
        }

        this.a2 = new long[m];
        this.b2 = new long[m];
        this.secondarySizes = new int[m];
        this.secondaryKeys = new String[m][];
        this.secondaryValues = new Object[m][];

        for (int j = 0; j < m; j++) {
            List<Integer> bucket = buckets.get(j);
            int k = bucket.size();
            if (k == 0) {
                secondarySizes[j] = 0;
                secondaryKeys[j] = new String[0];
                secondaryValues[j] = new Object[0];
                a2[j] = 1;
                b2[j] = 0;
                continue;
            }
            int s = k * k;
            secondarySizes[j] = s;

            boolean found = false;
            for (long attempt = 0; attempt < Long.MAX_VALUE; attempt++) {
                long ca = 1 + rng.nextLong(LARGE_PRIME - 1);
                long cb = rng.nextLong(LARGE_PRIME);
                String[] sk = new String[s];
                Object[] sv = new Object[s];
                boolean collision = false;
                for (int idx : bucket) {
                    int h2 = secondaryHash(keys[idx], ca, cb, s);
                    if (sk[h2] != null) {
                        collision = true;
                        break;
                    }
                    sk[h2] = keys[idx];
                    sv[h2] = values[idx];
                }
                if (!collision) {
                    a2[j] = ca;
                    b2[j] = cb;
                    secondaryKeys[j] = sk;
                    secondaryValues[j] = sv;
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new RuntimeException("Failed to find collision-free secondary hash for bucket " + j);
            }
        }
    }

    public V get(String key) {
        int h1 = primaryHash(key);
        int s = secondarySizes[h1];
        if (s == 0) return null;
        int h2 = secondaryHash(key, a2[h1], b2[h1], s);
        String stored = secondaryKeys[h1][h2];
        if (stored != null && stored.equals(key)) {
            @SuppressWarnings("unchecked")
            V val = (V) secondaryValues[h1][h2];
            return val;
        }
        return null;
    }

    private int primaryHash(String key) {
        return polyHash(key, a1, b1, m);
    }

    private static int polyHash(String key, long a, long b, int size) {
        long h = b;
        for (int i = 0; i < key.length(); i++) {
            h = Math.floorMod(h * a + key.charAt(i), LARGE_PRIME);
        }
        return Math.floorMod(h, size);
    }

    private static int secondaryHash(String key, long a, long b, int size) {
        return polyHash(key, a, b, size);
    }

    public long totalSecondarySpace() {
        long total = 0;
        for (int s : secondarySizes) {
            total += s;
        }
        return total;
    }
}
