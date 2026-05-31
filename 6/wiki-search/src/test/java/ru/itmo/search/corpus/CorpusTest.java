package ru.itmo.search.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itmo.search.analysis.StandardAnalyzer;
import ru.itmo.search.index.IndexBuilder;
import ru.itmo.search.index.MemoryIndex;

class CorpusTest {

    @Test
    void jsonlExtractHandlesEscapes() {
        String line = "{\"id\": 1, \"title\": \"A \\\"quoted\\\" title\", \"text\": \"line1\\nline2\"}";
        assertEquals("A \"quoted\" title", JsonlCorpusReader.extractString(line, "title"));
        assertEquals("line1\nline2", JsonlCorpusReader.extractString(line, "text"));
    }

    @Test
    void roundTripThroughJsonlFile(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("corpus.jsonl");
        Files.writeString(file, """
                {"title":"Doc A","text":"alpha beta beta"}
                {"title":"Doc B","text":"gamma alpha"}
                """);
        IndexBuilder b = new IndexBuilder(new StandardAnalyzer(1, java.util.Set.of()));
        int n = new JsonlCorpusReader(file).indexInto(b, Integer.MAX_VALUE);
        MemoryIndex index = b.build();
        assertEquals(2, n);
        assertEquals(2, index.numDocs());
        assertEquals(2, index.docFreq("alpha"));
        assertEquals(1, index.docFreq("gamma"));
    }
}
