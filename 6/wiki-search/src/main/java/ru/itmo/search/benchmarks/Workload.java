package ru.itmo.search.benchmarks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import ru.itmo.search.index.InvertedIndex;

public final class Workload {

    public final List<String> and = new ArrayList<>();
    public final List<String> or = new ArrayList<>();
    public final List<String> adj = new ArrayList<>();
    public final List<String> near = new ArrayList<>();
    public final List<String> bm25 = new ArrayList<>();

    public static Workload build(InvertedIndex index, int perType, long seed) {
        return build(index, perType, seed, "all");
    }

    public static Workload build(InvertedIndex index, int perType, long seed, String only) {
        List<String> terms = new ArrayList<>(index.terms());
        terms.sort(Comparator.comparingInt(index::docFreq).reversed());
        int n = terms.size();
        List<String> frequent = terms.subList(Math.min(5, n), Math.min(5 + 200, n));
        List<String> mid = terms.subList(Math.min(n / 3, n - 1), Math.min(n / 3 + 400, n));

        Workload w = new Workload();
        Random rng = new Random(seed);
        for (int i = 0; i < perType; i++) {
            String f1 = pick(frequent, rng);
            String f2 = pick(frequent, rng);
            String m1 = pick(mid, rng);
            String m2 = pick(mid, rng);
            if ("all".equals(only) || "and".equals(only)) {
                w.and.add(f1 + " AND " + m1);
            }
            if ("all".equals(only) || "or".equals(only)) {
                w.or.add(f1 + " OR " + f2);
            }
            if ("all".equals(only) || "adj".equals(only)) {
                w.adj.add("\"" + f1 + " " + f2 + "\"");
            }
            if ("all".equals(only) || "near".equals(only)) {
                w.near.add(f1 + " NEAR/10 " + f2);
            }
            if ("all".equals(only) || "bm25".equals(only)) {
                w.bm25.add(f1 + " " + m1 + " " + m2);
            }
        }
        return w;
    }

    private static String pick(List<String> pool, Random rng) {
        return pool.get(rng.nextInt(pool.size()));
    }
}
