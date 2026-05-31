package ru.itmo.search.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import ru.itmo.search.analysis.Analyzer;

public final class IndexBuilder {

    private static final class Accumulator {
        final IntArrayList docIds = new IntArrayList();
        final IntArrayList freqs = new IntArrayList();
        final IntArrayList positions = new IntArrayList();
        int lastDoc = -1;

        void add(int doc, int pos) {
            if (doc != lastDoc) {
                docIds.add(doc);
                freqs.add(0);
                lastDoc = doc;
            }
            positions.add(pos);
            int k = freqs.size() - 1;
            freqs.set(k, freqs.getInt(k) + 1);
        }

        RawPostings finish() {
            int dc = docIds.size();
            int[] dids = docIds.toIntArray();
            int[] fr = freqs.toIntArray();
            int[] pos = positions.toIntArray();
            int[] posStart = new int[dc + 1];
            for (int k = 0; k < dc; k++) {
                posStart[k + 1] = posStart[k] + fr[k];
            }
            return new RawPostings(dids, fr, pos, posStart);
        }
    }

    private final Analyzer analyzer;
    private final Map<String, Accumulator> acc = new HashMap<>();
    private final IntArrayList docLengths = new IntArrayList();
    private final List<String> docNames = new ArrayList<>();

    public IndexBuilder(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    public int addDocument(String name, String body) {
        int docId = docNames.size();
        docNames.add(name);
        List<String> tokens = analyzer.analyze(body);
        for (int pos = 0; pos < tokens.size(); pos++) {
            acc.computeIfAbsent(tokens.get(pos), k -> new Accumulator()).add(docId, pos);
        }
        docLengths.add(tokens.size());
        return docId;
    }

    public int numDocs() {
        return docNames.size();
    }

    public MemoryIndex build() {
        Map<String, RawPostings> postings = new TreeMap<>();
        for (Map.Entry<String, Accumulator> e : acc.entrySet()) {
            postings.put(e.getKey(), e.getValue().finish());
        }
        return new MemoryIndex(postings, docLengths.toIntArray(), docNames.toArray(new String[0]));
    }
}
