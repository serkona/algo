package ru.itmo.search.query;

import java.util.List;

public sealed interface Query
        permits Query.Term, Query.Phrase, Query.Bool, Query.Prox, Query.Not {

    record Term(String text) implements Query {
    }

    record Phrase(List<String> terms) implements Query {
    }

    record Bool(Op op, List<Query> clauses) implements Query {
        public enum Op { AND, OR }
    }

    record Prox(Query left, Query right, int slop, boolean ordered) implements Query {
    }

    record Not(Query operand) implements Query {
    }
}
