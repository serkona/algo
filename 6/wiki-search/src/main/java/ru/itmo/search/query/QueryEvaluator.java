package ru.itmo.search.query;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import ru.itmo.search.analysis.Analyzer;
import ru.itmo.search.index.InvertedIndex;
import ru.itmo.search.index.PositionalCursor;
import ru.itmo.search.index.PostingsCursor;
import ru.itmo.search.index.op.AndCursor;
import ru.itmo.search.index.op.AndNotCursor;
import ru.itmo.search.index.op.EmptyCursor;
import ru.itmo.search.index.op.OrCursor;
import ru.itmo.search.index.op.ProximityCursor;

public final class QueryEvaluator {

    public static final class QueryEvalException extends RuntimeException {
        public QueryEvalException(String message) {
            super(message);
        }
    }

    private final InvertedIndex index;
    private final Analyzer analyzer;

    public QueryEvaluator(InvertedIndex index, Analyzer analyzer) {
        this.index = index;
        this.analyzer = analyzer;
    }

    public PostingsCursor toCursor(Query q) {
        if (q instanceof Query.Term t) {
            return termCursor(t.text());
        }
        if (q instanceof Query.Phrase p) {
            return phraseCursor(p.terms());
        }
        if (q instanceof Query.Prox pr) {
            PostingsCursor l = toCursor(pr.left());
            PostingsCursor r = toCursor(pr.right());
            if (!(l instanceof PositionalCursor) || !(r instanceof PositionalCursor)) {
                throw new QueryEvalException("ADJ/NEAR operands must be terms or phrases");
            }
            return new ProximityCursor((PositionalCursor) l, (PositionalCursor) r, pr.slop(), pr.ordered());
        }
        if (q instanceof Query.Not) {
            throw new QueryEvalException("NOT must be combined with another operator (e.g. 'a AND NOT b')");
        }
        Query.Bool b = (Query.Bool) q;
        return b.op() == Query.Bool.Op.AND ? andCursor(b.clauses()) : orCursor(b.clauses());
    }

    private PostingsCursor termCursor(String surface) {
        String norm = analyzer.analyzeTerm(surface);
        if (norm == null) {
            return EmptyCursor.INSTANCE;
        }
        PostingsCursor c = index.cursor(norm);
        return c == null ? EmptyCursor.INSTANCE : c;
    }

    private PostingsCursor phraseCursor(List<String> words) {
        List<PositionalCursor> cursors = new ArrayList<>();
        for (String w : words) {
            String norm = analyzer.analyzeTerm(w);
            if (norm == null) {
                continue;
            }
            PostingsCursor c = index.cursor(norm);
            if (c == null) {
                return EmptyCursor.INSTANCE;
            }
            cursors.add((PositionalCursor) c);
        }
        if (cursors.isEmpty()) {
            return EmptyCursor.INSTANCE;
        }
        PositionalCursor acc = cursors.get(0);
        for (int i = 1; i < cursors.size(); i++) {
            acc = ProximityCursor.adj(acc, cursors.get(i), 1);
        }
        return acc;
    }

    private PostingsCursor andCursor(List<Query> clauses) {
        List<PostingsCursor> positives = new ArrayList<>();
        List<PostingsCursor> negatives = new ArrayList<>();
        for (Query c : clauses) {
            if (c instanceof Query.Not not) {
                negatives.add(toCursor(not.operand()));
            } else {
                positives.add(toCursor(c));
            }
        }
        if (positives.isEmpty()) {
            throw new QueryEvalException("a query of only NOT clauses is not allowed");
        }
        PostingsCursor result = positives.size() == 1
                ? positives.get(0)
                : new AndCursor(positives.toArray(new PostingsCursor[0]));
        for (PostingsCursor neg : negatives) {
            result = new AndNotCursor(result, neg);
        }
        return result;
    }

    private PostingsCursor orCursor(List<Query> clauses) {
        List<PostingsCursor> cursors = new ArrayList<>();
        for (Query c : clauses) {
            if (c instanceof Query.Not) {
                throw new QueryEvalException("NOT cannot be a direct operand of OR");
            }
            cursors.add(toCursor(c));
        }
        return new OrCursor(cursors.toArray(new PostingsCursor[0]));
    }

    public List<String> leafTerms(Query q) {
        Set<String> out = new LinkedHashSet<>();
        collectTerms(q, out, false);
        return new ArrayList<>(out);
    }

    private void collectTerms(Query q, Set<String> out, boolean negated) {
        if (negated) {
            return;
        }
        if (q instanceof Query.Term t) {
            String norm = analyzer.analyzeTerm(t.text());
            if (norm != null) {
                out.add(norm);
            }
        } else if (q instanceof Query.Phrase p) {
            for (String w : p.terms()) {
                String norm = analyzer.analyzeTerm(w);
                if (norm != null) {
                    out.add(norm);
                }
            }
        } else if (q instanceof Query.Prox pr) {
            collectTerms(pr.left(), out, false);
            collectTerms(pr.right(), out, false);
        } else if (q instanceof Query.Not) {
            return;
        } else {
            Query.Bool b = (Query.Bool) q;
            for (Query c : b.clauses()) {
                collectTerms(c, out, c instanceof Query.Not);
            }
        }
    }
}
