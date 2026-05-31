package ru.itmo.search;

import java.util.ArrayList;
import java.util.List;
import ru.itmo.search.analysis.StandardAnalyzer;
import ru.itmo.search.index.IndexBuilder;
import ru.itmo.search.index.MemoryIndex;
import ru.itmo.search.index.PostingsCursor;

public final class TestIndexes {

    private TestIndexes() {
    }

    public static MemoryIndex build(String... bodies) {
        IndexBuilder b = new IndexBuilder(new StandardAnalyzer(1, java.util.Set.of()));
        for (int i = 0; i < bodies.length; i++) {
            b.addDocument("doc" + i, bodies[i]);
        }
        return b.build();
    }

    public static List<Integer> collect(PostingsCursor c) {
        List<Integer> out = new ArrayList<>();
        for (int d = c.nextDoc(); d != PostingsCursor.NO_MORE; d = c.nextDoc()) {
            out.add(d);
        }
        return out;
    }
}
