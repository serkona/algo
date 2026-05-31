package ru.itmo.search.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StandardAnalyzerTest {

    @Test
    void lowercasesAndSplitsOnNonAlnum() {
        Analyzer a = new StandardAnalyzer(1, Set.of());
        assertEquals(List.of("hello", "world", "123", "x"), a.analyze("Hello, World! 123_x"));
    }

    @Test
    void minLengthAndStopwords() {
        Analyzer a = new StandardAnalyzer(2, StandardAnalyzer.DEFAULT_STOPWORDS);
        assertEquals(List.of("quick", "brown", "fox"), a.analyze("the a quick brown fox"));
    }

    @Test
    void queryTermNormalisation() {
        Analyzer a = new StandardAnalyzer(2, Set.of());
        assertEquals("brown", a.analyzeTerm("BROWN"));
        assertNull(a.analyzeTerm("!"));
    }
}
