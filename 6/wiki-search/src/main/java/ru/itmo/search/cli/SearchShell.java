package ru.itmo.search.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import ru.itmo.search.rank.ScoreDoc;
import ru.itmo.search.rank.SearchEngine;

public final class SearchShell {

    private enum Mode { BOOLEAN, WAND, EXHAUSTIVE }

    private final SearchEngine engine;
    private final PrintStream out;
    private int k = 10;
    private Mode mode = Mode.BOOLEAN;
    private double wandFactor = 1.0;

    private String lastQuery = "";
    private List<ScoreDoc> lastResults = List.of();

    public SearchShell(SearchEngine engine, PrintStream out) {
        this.engine = engine;
        this.out = out;
    }

    public SearchShell(SearchEngine engine, PrintStream out, int k, String mode, double wandFactor) {
        this(engine, out);
        this.k = k;
        this.mode = Mode.valueOf(mode.toUpperCase());
        this.wandFactor = wandFactor;
    }

    public void run() throws IOException {
        out.printf("wiki-search shell (mode=%s, k=%d, wandF=%.2f). Type :help for commands, :quit to exit.%n",
                mode.name().toLowerCase(), k, wandFactor);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            out.print("> ");
            out.flush();
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    if (line.startsWith(":")) {
                        if (handleCommand(line)) {
                            break;
                        }
                    } else {
                        runQuery(line);
                    }
                }
                out.print("> ");
                out.flush();
            }
        }
    }

    private boolean handleCommand(String line) {
        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0];
        String arg = parts.length > 1 ? parts[1] : "";
        switch (cmd) {
            case ":quit", ":q", ":exit" -> {
                return true;
            }
            case ":help", ":h" -> printHelp();
            case ":k" -> k = Integer.parseInt(arg.trim());
            case ":mode" -> mode = Mode.valueOf(arg.trim().toUpperCase());
            case ":wandf" -> {
                double f = Double.parseDouble(arg.trim());
                if (f < 1.0) {
                    out.println("wandF must be >= 1.0");
                } else {
                    wandFactor = f;
                }
            }
            case ":export" -> doExport(arg.trim());
            default -> out.println("unknown command: " + cmd + " (try :help)");
        }
        return false;
    }

    private void runQuery(String query) {
        try {
            long t0 = System.nanoTime();
            List<ScoreDoc> results = switch (mode) {
                case BOOLEAN -> engine.searchBoolean(query, k);
                case WAND -> engine.searchRankedWand(query, k, wandFactor);
                case EXHAUSTIVE -> engine.searchRankedExhaustive(query, k);
            };
            double ms = (System.nanoTime() - t0) / 1e6;
            lastQuery = query;
            lastResults = results;
            print(results, ms);
        } catch (RuntimeException e) {
            out.println("error: " + e.getMessage());
        }
    }

    private void print(List<ScoreDoc> results, double ms) {
        out.printf("%d hits in %.2f ms (mode=%s, k=%d)%n", results.size(), ms,
                mode.name().toLowerCase(), k);
        int rank = 1;
        for (ScoreDoc d : results) {
            out.printf("  %2d. [%.4f] #%d  %s%n", rank++, d.score, d.docId, d.name);
        }
    }

    private void doExport(String pathStr) {
        if (pathStr.isEmpty()) {
            out.println("usage: :export <path>");
            return;
        }
        try {
            ResultExporter.export(Path.of(pathStr), lastQuery, lastResults);
            out.println("exported " + lastResults.size() + " results to " + pathStr);
        } catch (IOException e) {
            out.println("export failed: " + e.getMessage());
        }
    }

    private void printHelp() {
        out.println("""
                commands:
                  <query>                 boolean algebra: AND OR NOT ADJ NEAR/k "phrase" ( )
                  :k N                    number of results (default 10)
                  :mode boolean|wand|exhaustive
                  :wandf F                WAND aggressiveness factor (>=1)
                  :export <path>          export last results (.json or .csv/.txt)
                  :help                   this help
                  :quit                   exit""");
    }
}
