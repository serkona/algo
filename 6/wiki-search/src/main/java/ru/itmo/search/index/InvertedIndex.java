package ru.itmo.search.index;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;

public interface InvertedIndex extends Closeable {

    int numDocs();

    long totalTokens();

    double avgDocLength();

    int docLength(int docId);

    String docName(int docId);

    int docFreq(String term);

    PostingsCursor cursor(String term);

    Set<String> terms();

    @Override
    default void close() throws IOException {
    }
}
