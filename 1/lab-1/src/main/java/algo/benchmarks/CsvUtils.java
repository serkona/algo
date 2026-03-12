package algo.benchmarks;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class CsvUtils {

    private CsvUtils() {}

    @SuppressWarnings("unchecked")
    public static List<String>[] readTwoColumns(Path csvPath, String keyCol, String valCol)
            throws IOException {
        List<String> keys = new ArrayList<>();
        List<String> vals = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(csvPath), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) return new List[]{keys, vals};

            int ki = findColumnIndex(header, keyCol);
            int vi = findColumnIndex(header, valCol);
            if (ki < 0) throw new IllegalArgumentException("Column '" + keyCol + "' not found in " + csvPath);
            if (vi < 0) throw new IllegalArgumentException("Column '" + valCol + "' not found in " + csvPath);

            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = parseCsvLine(line);
                if (Math.max(ki, vi) < fields.length) {
                    String k = fields[ki].trim();
                    String v = fields[vi].trim();
                    if (!k.isEmpty()) {
                        keys.add(k);
                        vals.add(v);
                    }
                }
            }
        }
        return new List[]{keys, vals};
    }

    public static List<String> readColumn(Path csvPath, String columnName) throws IOException {
        List<String> values = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(csvPath), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) return values;

            int colIdx = findColumnIndex(header, columnName);
            if (colIdx < 0) throw new IllegalArgumentException(
                    "Column '" + columnName + "' not found in " + csvPath);

            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = parseCsvLine(line);
                if (colIdx < fields.length) {
                    String val = fields[colIdx].trim();
                    if (!val.isEmpty()) {
                        values.add(val);
                    }
                }
            }
        }
        return values;
    }

    private static int findColumnIndex(String header, String column) {
        String[] cols = parseCsvLine(header);
        for (int i = 0; i < cols.length; i++) {
            if (cols[i].trim().equalsIgnoreCase(column)) return i;
        }
        return -1;
    }

    static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
