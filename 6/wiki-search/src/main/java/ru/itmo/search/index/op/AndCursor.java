package ru.itmo.search.index.op;

import java.util.Arrays;
import java.util.Comparator;
import ru.itmo.search.index.PostingsCursor;

public final class AndCursor implements PostingsCursor {

    private final PostingsCursor[] cursors;
    private int current = -1;

    public AndCursor(PostingsCursor... cursors) {
        if (cursors.length == 0) {
            throw new IllegalArgumentException("AND needs at least one operand");
        }
        this.cursors = cursors.clone();
        Arrays.sort(this.cursors, Comparator.comparingLong(PostingsCursor::cost));
    }

    @Override
    public int docId() {
        return current;
    }

    @Override
    public int nextDoc() {
        return align(current + 1);
    }

    @Override
    public int advance(int target) {
        return align(target);
    }

    private int align(int target) {
        int candidate = cursors[0].advance(target);
        while (candidate != NO_MORE) {
            boolean agreed = true;
            for (int k = 1; k < cursors.length; k++) {
                int d = cursors[k].advance(candidate);
                if (d != candidate) {
                    candidate = cursors[0].advance(d);
                    agreed = false;
                    break;
                }
            }
            if (agreed) {
                return current = candidate;
            }
        }
        return current = NO_MORE;
    }

    @Override
    public int freq() {
        int f = 0;
        for (PostingsCursor c : cursors) {
            f += c.freq();
        }
        return f;
    }

    @Override
    public long cost() {
        return cursors[0].cost();
    }
}
