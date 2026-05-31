package ru.itmo.search.query;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public final class QueryParser {

    public static final int DEFAULT_NEAR_SLOP = 8;

    public static final class QueryParseException extends RuntimeException {
        public QueryParseException(String message) {
            super(message);
        }
    }

    private static final class ThrowingErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                int charPositionInLine, String msg, RecognitionException e) {
            throw new QueryParseException("query syntax error at " + charPositionInLine + ": " + msg);
        }
    }

    public Query parse(String text) {
        BoolQueryLexer lexer = new BoolQueryLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new ThrowingErrorListener());
        BoolQueryParser parser = new BoolQueryParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(new ThrowingErrorListener());
        return orExpr(parser.query().orExpr());
    }

    private Query orExpr(BoolQueryParser.OrExprContext ctx) {
        List<Query> clauses = new ArrayList<>();
        for (BoolQueryParser.AndExprContext a : ctx.andExpr()) {
            clauses.add(andExpr(a));
        }
        return clauses.size() == 1 ? clauses.get(0) : new Query.Bool(Query.Bool.Op.OR, clauses);
    }

    private Query andExpr(BoolQueryParser.AndExprContext ctx) {
        List<Query> clauses = new ArrayList<>();
        for (BoolQueryParser.NotExprContext n : ctx.notExpr()) {
            clauses.add(notExpr(n));
        }
        return clauses.size() == 1 ? clauses.get(0) : new Query.Bool(Query.Bool.Op.AND, clauses);
    }

    private Query notExpr(BoolQueryParser.NotExprContext ctx) {
        if (ctx instanceof BoolQueryParser.NotExprNegContext neg) {
            return new Query.Not(notExpr(neg.notExpr()));
        }
        return proxExpr(((BoolQueryParser.NotExprPassContext) ctx).proxExpr());
    }

    private Query proxExpr(BoolQueryParser.ProxExprContext ctx) {
        List<BoolQueryParser.PrimaryContext> prims = ctx.primary();
        Query acc = primary(prims.get(0));
        List<BoolQueryParser.ProxOpContext> ops = ctx.proxOp();
        for (int i = 0; i < ops.size(); i++) {
            Query right = primary(prims.get(i + 1));
            BoolQueryParser.ProxOpContext op = ops.get(i);
            if (op instanceof BoolQueryParser.AdjOpContext adj) {
                int slop = adj.slop() != null ? slop(adj.slop()) : 1;
                acc = new Query.Prox(acc, right, slop, true);
            } else {
                BoolQueryParser.NearOpContext near = (BoolQueryParser.NearOpContext) op;
                int slop = near.slop() != null ? slop(near.slop()) : DEFAULT_NEAR_SLOP;
                acc = new Query.Prox(acc, right, slop, false);
            }
        }
        return acc;
    }

    private int slop(BoolQueryParser.SlopContext ctx) {
        return Integer.parseInt(ctx.INT().getText());
    }

    private Query primary(BoolQueryParser.PrimaryContext ctx) {
        if (ctx instanceof BoolQueryParser.ParenExprContext paren) {
            return orExpr(paren.orExpr());
        }
        if (ctx instanceof BoolQueryParser.PhraseExprContext phrase) {
            return phrase(phrase.QUOTED().getText());
        }
        BoolQueryParser.TermExprContext te = (BoolQueryParser.TermExprContext) ctx;
        return new Query.Term(te.term().getText());
    }

    private Query phrase(String quoted) {
        String inner = quoted.substring(1, quoted.length() - 1);
        List<String> words = new ArrayList<>();
        for (String w : inner.trim().split("\\s+")) {
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        return new Query.Phrase(words);
    }
}
