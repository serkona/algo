package ru.itmo.search.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import ru.itmo.search.rank.ScoreDoc;

public final class ResultExporter {

    private ResultExporter() {
    }

    public static void export(Path path, String query, List<ScoreDoc> results) throws IOException {
        if (path.toString().endsWith(".json")) {
            exportJson(path, query, results);
        } else {
            exportCsv(path, query, results);
        }
    }

    private static void exportCsv(Path path, String query, List<ScoreDoc> results) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("# query: " + query);
            w.newLine();
            w.write("rank,docId,score,title");
            w.newLine();
            int rank = 1;
            for (ScoreDoc d : results) {
                w.write(rank++ + "," + d.docId + "," + String.format("%.6f", d.score) + ","
                        + csvEscape(d.name));
                w.newLine();
            }
        }
    }

    private static void exportJson(Path path, String query, List<ScoreDoc> results) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("{\n  \"query\": \"" + jsonEscape(query) + "\",\n  \"results\": [\n");
            for (int i = 0; i < results.size(); i++) {
                ScoreDoc d = results.get(i);
                w.write("    {\"rank\": " + (i + 1) + ", \"docId\": " + d.docId
                        + ", \"score\": " + String.format("%.6f", d.score)
                        + ", \"title\": \"" + jsonEscape(d.name) + "\"}");
                w.write(i + 1 < results.size() ? ",\n" : "\n");
            }
            w.write("  ]\n}\n");
        }
    }

    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
