package ru.itmo.search.query;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.itmo.search.TestIndexes;
import ru.itmo.search.analysis.StandardAnalyzer;
import ru.itmo.search.index.MemoryIndex;
import ru.itmo.search.index.PostingsCursor;

import static org.junit.jupiter.api.Assertions.*;

class QueryTest {

    private final QueryParser parser = new QueryParser();
    private final MemoryIndex idx = TestIndexes.build(
            "a b c a",   // 0
            "b c d",     // 1
            "a c",       // 2
            "d e a b");  // 3
    private final QueryEvaluator eval =
            new QueryEvaluator(idx, new StandardAnalyzer(1, java.util.Set.of()));

    private List<Integer> run(String q) {
        PostingsCursor c = eval.toCursor(parser.parse(q));
        List<Integer> out = new ArrayList<>();
        for (int d = c.nextDoc(); d != PostingsCursor.NO_MORE; d = c.nextDoc()) {
            out.add(d);
        }
        return out;
    }

    @Test
    void parsesPrecedenceAndShapes() {
        assertInstanceOf(Query.Bool.class, parser.parse("a AND b"));
        assertInstanceOf(Query.Phrase.class, parser.parse("\"a b\""));
        assertInstanceOf(Query.Prox.class, parser.parse("a ADJ b"));
        assertInstanceOf(Query.Prox.class, parser.parse("a NEAR/3 b"));
        Query q = parser.parse("a OR b AND c");
        Query.Bool top = (Query.Bool) q;
        assertEquals(Query.Bool.Op.OR, top.op());
        assertInstanceOf(Query.Bool.class, top.clauses().get(1));
    }

    @Test
    void booleanEvaluation() {
        assertEquals(List.of(0, 3), run("a AND b"));
        assertEquals(List.of(0, 1, 2, 3), run("a OR d"));
        assertEquals(List.of(2), run("a AND NOT b"));
        assertEquals(List.of(0, 3), run("\"a b\""));
        assertEquals(List.of(0, 1), run("b ADJ c"));
        assertEquals(List.of(0, 2), run("a NEAR/2 c"));
    }

    @Test
    void symbolsAndCaseInsensitive() {
        assertEquals(run("a AND b"), run("a && b"));
        assertEquals(run("a AND NOT b"), run("a and -b"));
        assertEquals(run("a OR d"), run("a || d"));
    }

    @Test
    void invalidQueriesFail() {
        assertThrows(QueryParser.QueryParseException.class, () -> parser.parse("a AND"));
        assertThrows(QueryEvaluator.QueryEvalException.class, () -> eval.toCursor(parser.parse("NOT a")));
    }

    @Test
    void missingTermYieldsNoHits() {
        assertEquals(List.of(), run("zzz"));
        assertEquals(List.of(), run("a AND zzz"));
    }
}
