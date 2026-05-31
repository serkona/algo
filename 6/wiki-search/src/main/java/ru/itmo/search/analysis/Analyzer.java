package ru.itmo.search.analysis;

import java.util.List;

public interface Analyzer {

    List<String> analyze(String text);

    String analyzeTerm(String term);
}
