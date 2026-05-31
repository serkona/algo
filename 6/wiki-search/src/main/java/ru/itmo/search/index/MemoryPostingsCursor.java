package ru.itmo.search.index;

public final class MemoryPostingsCursor implements PositionalCursor {

    private final RawPostings p;
    private final int len;
    private int i = -1;
    private int[] posBuf = new int[8];

    public MemoryPostingsCursor(RawPostings postings) {
        this.p = postings;
        this.len = postings.docIds.length;
    }

    @Override
    public int docId() {
        if (i < 0) {
            return -1;
        }
        return i >= len ? NO_MORE : p.docIds[i];
    }

    @Override
    public int nextDoc() {
        i++;
        return i >= len ? NO_MORE : p.docIds[i];
    }

    @Override
    public int advance(int target) {
        if (i >= len) {
            return NO_MORE;
        }
        int lo = Math.max(i, 0);
        if (p.docIds[lo] >= target) {
            i = lo;
            return p.docIds[i];
        }
        int step = 1;
        int hi = lo + 1;
        while (hi < len && p.docIds[hi] < target) {
            lo = hi;
            step <<= 1;
            hi = lo + step;
        }
        if (hi > len) {
            hi = len;
        }
        int left = lo + 1;
        int right = hi;
        while (left < right) {
            int mid = (left + right) >>> 1;
            if (p.docIds[mid] < target) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        i = left;
        return i >= len ? NO_MORE : p.docIds[i];
    }

    @Override
    public int freq() {
        return p.freqs[i];
    }

    @Override
    public long cost() {
        return len;
    }

    @Override
    public int[] positions() {
        int f = p.freqs[i];
        if (posBuf.length < f) {
            posBuf = new int[Integer.highestOneBit(f) << 1];
        }
        System.arraycopy(p.positions, p.posStart[i], posBuf, 0, f);
        return posBuf;
    }
}
