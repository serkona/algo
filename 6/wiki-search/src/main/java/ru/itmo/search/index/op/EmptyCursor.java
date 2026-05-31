package ru.itmo.search.index.op;

import ru.itmo.search.index.PositionalCursor;

public final class EmptyCursor implements PositionalCursor {

    public static final EmptyCursor INSTANCE = new EmptyCursor();
    private static final int[] NO_POS = new int[0];

    @Override
    public int docId() {
        return NO_MORE;
    }

    @Override
    public int nextDoc() {
        return NO_MORE;
    }

    @Override
    public int advance(int target) {
        return NO_MORE;
    }

    @Override
    public int freq() {
        return 0;
    }

    @Override
    public long cost() {
        return 0;
    }

    @Override
    public int[] positions() {
        return NO_POS;
    }
}
