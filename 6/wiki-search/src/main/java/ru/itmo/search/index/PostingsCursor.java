package ru.itmo.search.index;

public interface PostingsCursor {

    int NO_MORE = Integer.MAX_VALUE;

    int docId();

    int nextDoc();

    int advance(int target);

    int freq();

    long cost();
}
