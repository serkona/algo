package ru.itmo.search.index.op;

import ru.itmo.search.index.PostingsCursor;

public final class OrCursor implements PostingsCursor {

    private final PostingsCursor[] cursors;
    private int current = -1;

    public OrCursor(PostingsCursor... cursors) {
        if (cursors.length == 0) {
            throw new IllegalArgumentException("OR needs at least one operand");
        }
        this.cursors = cursors.clone();
    }

    @Override
    public int docId() {
        return current;
    }

    @Override
    public int nextDoc() {
        for (PostingsCursor c : cursors) {
            if (c.docId() == current) {
                c.nextDoc();
            }
        }
        return recomputeMin();
    }

    @Override
    public int advance(int target) {
        for (PostingsCursor c : cursors) {
            int d = c.docId();
            if (d < target) {
                c.advance(target);
            }
        }
        return recomputeMin();
    }

    private int recomputeMin() {
        int min = NO_MORE;
        for (PostingsCursor c : cursors) {
            int d = c.docId();
            if (d != -1 && d < min) {
                min = d;
            }
        }
        return current = min;
    }

    @Override
    public int freq() {
        int f = 0;
        for (PostingsCursor c : cursors) {
            if (c.docId() == current) {
                f += c.freq();
            }
        }
        return f;
    }

    @Override
    public long cost() {
        long total = 0;
        for (PostingsCursor c : cursors) {
            total += c.cost();
        }
        return total;
    }
}
