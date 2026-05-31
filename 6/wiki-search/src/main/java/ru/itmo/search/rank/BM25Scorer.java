package ru.itmo.search.rank;

public final class BM25Scorer {

    private final double k1;
    private final double b;

    public BM25Scorer(double k1, double b) {
        this.k1 = k1;
        this.b = b;
    }

    public static BM25Scorer defaults() {
        return new BM25Scorer(1.2, 0.75);
    }

    public double k1() {
        return k1;
    }

    public double b() {
        return b;
    }

    public double idf(int numDocs, int docFreq) {
        return Math.log(1.0 + (numDocs - docFreq + 0.5) / (docFreq + 0.5));
    }

    public double score(double idf, int tf, int docLen, double avgDocLen) {
        double norm = k1 * (1 - b + b * (docLen / avgDocLen));
        return idf * (tf * (k1 + 1)) / (tf + norm);
    }

    public double upperBound(double idf) {
        return idf * (k1 + 1);
    }
}
