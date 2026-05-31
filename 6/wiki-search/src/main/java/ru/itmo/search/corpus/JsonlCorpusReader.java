package ru.itmo.search.corpus;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import ru.itmo.search.index.IndexBuilder;

public final class JsonlCorpusReader implements CorpusReader {

    private final Path path;
    private final String titleKey;
    private final String textKey;

    public JsonlCorpusReader(Path path) {
        this(path, "title", "text");
    }

    public JsonlCorpusReader(Path path, String titleKey, String textKey) {
        this.path = path;
        this.titleKey = titleKey;
        this.textKey = textKey;
    }

    @Override
    public int indexInto(IndexBuilder builder, int maxDocs) throws IOException {
        int count = 0;
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while (count < maxDocs && (line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String text = extractString(line, textKey);
                if (text == null || text.isEmpty()) {
                    continue;
                }
                String title = extractString(line, titleKey);
                if (title == null) {
                    title = "doc-" + count;
                }
                builder.addDocument(title, text);
                count++;
            }
        }
        return count;
    }

    static String extractString(String json, String key) {
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        if (k < 0) {
            return null;
        }
        int i = k + needle.length();
        while (i < json.length() && json.charAt(i) != ':') {
            i++;
        }
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i++);
            if (c == '\\' && i < json.length()) {
                char e = json.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (i + 4 <= json.length()) {
                            sb.append((char) Integer.parseInt(json.substring(i, i + 4), 16));
                            i += 4;
                        }
                    }
                    default -> sb.append(e);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
