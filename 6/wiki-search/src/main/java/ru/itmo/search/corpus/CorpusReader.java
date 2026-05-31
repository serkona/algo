package ru.itmo.search.corpus;

import java.io.IOException;
import ru.itmo.search.index.IndexBuilder;

public interface CorpusReader {

    int indexInto(IndexBuilder builder, int maxDocs) throws IOException;
}
