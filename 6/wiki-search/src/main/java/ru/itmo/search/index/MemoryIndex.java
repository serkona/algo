package ru.itmo.search.index;

import java.util.Map;
import java.util.Set;

public final class MemoryIndex implements InvertedIndex {

    private final Map<String, RawPostings> postings;
    private final int[] docLengths;
    private final String[] docNames;
    private final long totalTokens;
    private final double avgDocLength;

    MemoryIndex(Map<String, RawPostings> postings, int[] docLengths, String[] docNames) {
        this.postings = postings;
        this.docLengths = docLengths;
        this.docNames = docNames;
        long sum = 0;
        for (int len : docLengths) {
            sum += len;
        }
        this.totalTokens = sum;
        this.avgDocLength = docLengths.length == 0 ? 0 : (double) sum / docLengths.length;
    }

    @Override
    public int numDocs() {
        return docLengths.length;
    }

    @Override
    public long totalTokens() {
        return totalTokens;
    }

    @Override
    public double avgDocLength() {
        return avgDocLength;
    }

    @Override
    public int docLength(int docId) {
        return docLengths[docId];
    }

    @Override
    public String docName(int docId) {
        return docNames[docId];
    }

    @Override
    public int docFreq(String term) {
        RawPostings p = postings.get(term);
        return p == null ? 0 : p.docCount();
    }

    @Override
    public PostingsCursor cursor(String term) {
        RawPostings p = postings.get(term);
        return p == null ? null : new MemoryPostingsCursor(p);
    }

    @Override
    public Set<String> terms() {
        return postings.keySet();
    }

    public RawPostings raw(String term) {
        return postings.get(term);
    }
}
