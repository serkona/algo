package ru.itmo.search.index;

public final class RawPostings {
    public final int[] docIds;
    public final int[] freqs;
    public final int[] positions;
    public final int[] posStart;

    public RawPostings(int[] docIds, int[] freqs, int[] positions, int[] posStart) {
        this.docIds = docIds;
        this.freqs = freqs;
        this.positions = positions;
        this.posStart = posStart;
    }

    public int docCount() {
        return docIds.length;
    }
}
