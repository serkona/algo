package ru.itmo.search.index.op;

import ru.itmo.search.index.PostingsCursor;

public final class AndNotCursor implements PostingsCursor {

    private final PostingsCursor include;
    private final PostingsCursor exclude;
    private int current = -1;
    private int exDoc = -1;
    private boolean exStarted = false;

    public AndNotCursor(PostingsCursor include, PostingsCursor exclude) {
        this.include = include;
        this.exclude = exclude;
    }

    @Override
    public int docId() {
        return current;
    }

    @Override
    public int nextDoc() {
        return filter(include.nextDoc());
    }

    @Override
    public int advance(int target) {
        return filter(include.advance(target));
    }

    private int filter(int d) {
        while (d != NO_MORE) {
            if (excludeAtLeast(d) != d) {
                return current = d;
            }
            d = include.nextDoc();
        }
        return current = NO_MORE;
    }

    private int excludeAtLeast(int d) {
        if (!exStarted) {
            exDoc = exclude.nextDoc();
            exStarted = true;
        }
        if (exDoc == NO_MORE || exDoc >= d) {
            return exDoc;
        }
        return exDoc = exclude.advance(d);
    }

    @Override
    public int freq() {
        return include.freq();
    }

    @Override
    public long cost() {
        return include.cost();
    }
}
