package ru.itmo.search.index;

public interface PositionalCursor extends PostingsCursor {

    int[] positions();
}
