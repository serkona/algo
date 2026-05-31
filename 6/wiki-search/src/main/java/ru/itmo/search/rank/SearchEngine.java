package ru.itmo.search.rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import ru.itmo.search.analysis.Analyzer;
import ru.itmo.search.index.InvertedIndex;
import ru.itmo.search.index.PostingsCursor;
import ru.itmo.search.query.Query;
import ru.itmo.search.query.QueryEvaluator;
import ru.itmo.search.query.QueryParser;

public final class SearchEngine {

    private static final class TermState {
        final PostingsCursor cursor;
        final double idf;
        final double upperBound;
        int doc;

        TermState(PostingsCursor cursor, double idf, double upperBound) {
            this.cursor = cursor;
            this.idf = idf;
            this.upperBound = upperBound;
            this.doc = -1;
        }
    }

    private final InvertedIndex index;
    private final Analyzer analyzer;
    private final BM25Scorer scorer;
    private final QueryParser parser = new QueryParser();
    private final QueryEvaluator evaluator;

    public SearchEngine(InvertedIndex index, Analyzer analyzer, BM25Scorer scorer) {
        this.index = index;
        this.analyzer = analyzer;
        this.scorer = scorer;
        this.evaluator = new QueryEvaluator(index, analyzer);
    }

    public Query parse(String query) {
        return parser.parse(query);
    }

    public List<String> leafTerms(Query ast) {
        return evaluator.leafTerms(ast);
    }

    public List<ScoreDoc> searchBoolean(String query, int k) {
        Query ast = parser.parse(query);
        PostingsCursor cursor = evaluator.toCursor(ast);
        List<String> terms = evaluator.leafTerms(ast);
        TermState[] states = buildStates(terms);
        double avgdl = index.avgDocLength();

        TopKCollector top = new TopKCollector(k);
        for (int d = cursor.nextDoc(); d != PostingsCursor.NO_MORE; d = cursor.nextDoc()) {
            double score = 0;
            int dl = index.docLength(d);
            for (TermState t : states) {
                int tf = tfAt(t, d);
                if (tf > 0) {
                    score += scorer.score(t.idf, tf, dl, avgdl);
                }
            }
            top.collect(d, score);
        }
        return withNames(top.toSortedList());
    }

    private int tfAt(TermState t, int doc) {
        int cd = t.cursor.docId();
        if (cd < doc) {
            cd = t.cursor.advance(doc);
        }
        return cd == doc ? t.cursor.freq() : 0;
    }

    public List<ScoreDoc> searchRankedExhaustive(String query, int k) {
        return rankedDisjunctive(query, k, 1.0, false);
    }

    public List<ScoreDoc> searchRankedWand(String query, int k, double factor) {
        if (factor < 1.0) {
            throw new IllegalArgumentException("WAND factor must be >= 1.0");
        }
        return rankedDisjunctive(query, k, factor, true);
    }

    private List<ScoreDoc> rankedDisjunctive(String query, int k, double factor, boolean wand) {
        // Ranked retrieval tokenises the query for BM25 rather than parsing boolean algebra.
        List<String> terms = analyzer.analyze(query);
        List<TermState> live = new ArrayList<>();
        for (TermState t : buildStates(terms)) {
            t.doc = t.cursor.nextDoc();
            if (t.doc != PostingsCursor.NO_MORE) {
                live.add(t);
            }
        }
        TopKCollector top = new TopKCollector(k);
        double avgdl = index.avgDocLength();

        while (!live.isEmpty()) {
            live.sort(Comparator.comparingInt(s -> s.doc));
            int pivotDoc;
            int pivotIdx;
            if (wand) {
                double threshold = top.threshold();
                double thr = threshold == Double.NEGATIVE_INFINITY ? threshold : threshold * factor;
                double sumUb = 0;
                pivotIdx = -1;
                for (int i = 0; i < live.size(); i++) {
                    sumUb += live.get(i).upperBound;
                    if (sumUb > thr) {
                        pivotIdx = i;
                        break;
                    }
                }
                if (pivotIdx < 0) {
                    break;
                }
                pivotDoc = live.get(pivotIdx).doc;
            } else {
                pivotIdx = 0;
                pivotDoc = live.get(0).doc;
            }

            if (live.get(0).doc == pivotDoc) {
                double score = 0;
                int dl = index.docLength(pivotDoc);
                for (TermState t : live) {
                    if (t.doc == pivotDoc) {
                        score += scorer.score(t.idf, t.cursor.freq(), dl, avgdl);
                    }
                }
                top.collect(pivotDoc, score);
                advanceAll(live, pivotDoc);
            } else {
                TermState mover = null;
                for (int i = 0; i < pivotIdx; i++) {
                    TermState t = live.get(i);
                    if (t.doc < pivotDoc && (mover == null || t.upperBound > mover.upperBound)) {
                        mover = t;
                    }
                }
                assert mover != null;
                mover.doc = mover.cursor.advance(pivotDoc);
                pruneExhausted(live);
            }
        }
        return withNames(top.toSortedList());
    }

    private void advanceAll(List<TermState> live, int atDoc) {
        for (TermState t : live) {
            if (t.doc == atDoc) {
                t.doc = t.cursor.nextDoc();
            }
        }
        pruneExhausted(live);
    }

    private void pruneExhausted(List<TermState> live) {
        live.removeIf(t -> t.doc == PostingsCursor.NO_MORE);
    }

    private TermState[] buildStates(List<String> terms) {
        List<TermState> states = new ArrayList<>();
        int n = index.numDocs();
        for (String term : terms) {
            PostingsCursor c = index.cursor(term);
            if (c == null) {
                continue;
            }
            double idf = scorer.idf(n, index.docFreq(term));
            states.add(new TermState(c, idf, scorer.upperBound(idf)));
        }
        return states.toArray(new TermState[0]);
    }

    private List<ScoreDoc> withNames(List<ScoreDoc> docs) {
        for (ScoreDoc d : docs) {
            d.name = index.docName(d.docId);
        }
        return docs;
    }
}
