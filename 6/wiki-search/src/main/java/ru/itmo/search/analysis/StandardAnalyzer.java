package ru.itmo.search.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class StandardAnalyzer implements Analyzer {

    public static final Set<String> DEFAULT_STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "of", "to", "in", "is", "it",
            "for", "on", "with", "as", "by", "at", "be", "this", "that", "are");

    private final int minLength;
    private final Set<String> stopwords;

    public StandardAnalyzer() {
        this(2, Set.of());
    }

    public StandardAnalyzer(int minLength, Set<String> stopwords) {
        this.minLength = minLength;
        this.stopwords = stopwords;
    }

    @Override
    public List<String> analyze(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        int n = text.length();
        int start = -1;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (start < 0) {
                    start = i;
                }
            } else if (start >= 0) {
                emit(text, start, i, out);
                start = -1;
            }
        }
        if (start >= 0) {
            emit(text, start, n, out);
        }
        return out;
    }

    private void emit(String text, int start, int end, List<String> out) {
        if (end - start < minLength) {
            return;
        }
        String token = text.substring(start, end).toLowerCase();
        if (!stopwords.isEmpty() && stopwords.contains(token)) {
            return;
        }
        out.add(token);
    }

    @Override
    public String analyzeTerm(String term) {
        List<String> tokens = analyze(term);
        return tokens.isEmpty() ? null : tokens.get(0);
    }
}
