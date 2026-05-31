package ru.itmo.search.rank;

public final class ScoreDoc implements Comparable<ScoreDoc> {
    public final int docId;
    public final double score;
    public String name;

    public ScoreDoc(int docId, double score) {
        this.docId = docId;
        this.score = score;
    }

    @Override
    public int compareTo(ScoreDoc o) {
        int c = Double.compare(o.score, score);
        return c != 0 ? c : Integer.compare(docId, o.docId);
    }

    @Override
    public String toString() {
        return "#" + docId + " (" + score + ")" + (name != null ? " " + name : "");
    }
}
