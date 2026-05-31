package ru.itmo.search.index.disk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import ru.itmo.search.compress.DeltaCodec;
import ru.itmo.search.compress.IntCodec;
import ru.itmo.search.compress.VarByteCodec;
import ru.itmo.search.index.InvertedIndex;
import ru.itmo.search.index.PostingsCursor;

public final class DiskIndex implements InvertedIndex {

    static final class TermEntry {
        final long offset;
        final int docFreq;

        TermEntry(long offset, int docFreq) {
            this.offset = offset;
            this.docFreq = docFreq;
        }
    }

    private final Map<String, TermEntry> dictionary;
    private final int[] docLengths;
    private final int[] nameOffsets;
    private final MappedByteBuffer namesBuf;

    private final FileChannel postingsChannel;
    private final MappedByteBuffer[] segments;
    private final long postingsSize;

    private final int numDocs;
    private final long totalTokens;
    private final double avgDocLength;
    final int blockSize;

    final IntCodec deltaDocId;
    final IntCodec freqCodec;
    final IntCodec posCodec;
    final IntCodec skipCodec = new DeltaCodec(new VarByteCodec());
    final long segmentSizeBytes;

    private DiskIndex(Path dir, IndexConfig config, int numDocs, long totalTokens,
                      Map<String, TermEntry> dict, int[] docLengths, int[] nameOffsets,
                      MappedByteBuffer namesBuf, FileChannel postingsChannel,
                      MappedByteBuffer[] segments, long postingsSize) {
        this.blockSize = config.blockSize;
        this.deltaDocId = new DeltaCodec(config.docIdBase());
        this.freqCodec = config.freqBase();
        this.posCodec = config.posBase();
        this.segmentSizeBytes = config.segmentSizeBytes;
        this.numDocs = numDocs;
        this.totalTokens = totalTokens;
        this.avgDocLength = numDocs == 0 ? 0 : (double) totalTokens / numDocs;
        this.dictionary = dict;
        this.docLengths = docLengths;
        this.nameOffsets = nameOffsets;
        this.namesBuf = namesBuf;
        this.postingsChannel = postingsChannel;
        this.segments = segments;
        this.postingsSize = postingsSize;
    }

    public static DiskIndex open(Path dir) throws IOException {
        Properties meta = new Properties();
        try (InputStream in = Files.newInputStream(dir.resolve(DiskIndexWriter.META))) {
            meta.load(in);
        }
        int numDocs = Integer.parseInt(meta.getProperty("numDocs"));
        long totalTokens = Long.parseLong(meta.getProperty("totalTokens"));
        String postingsFormat = meta.getProperty("postingsFormat");
        if (!DiskIndexWriter.POSTINGS_FORMAT.equals(postingsFormat)) {
            throw new IOException("Unsupported postings format: " + postingsFormat
                    + ". Rebuild the index with the current code.");
        }
        IndexConfig config = new IndexConfig(
                Integer.parseInt(meta.getProperty("blockSize")),
                meta.getProperty("docIdCodec"),
                meta.getProperty("freqCodec"),
                meta.getProperty("posCodec"),
                Long.parseLong(meta.getProperty("segmentSizeBytes",
                        Long.toString(IndexConfig.DEFAULT_SEGMENT_SIZE_BYTES))));

        Map<String, TermEntry> dict = loadDictionary(dir.resolve(DiskIndexWriter.DICT));
        int[] docLengths = loadDocLengths(dir.resolve(DiskIndexWriter.DOCLEN), config);
        int[] nameOffsets = loadNameOffsets(dir.resolve(DiskIndexWriter.NAMES_IDX));
        MappedByteBuffer namesBuf = mapWhole(dir.resolve(DiskIndexWriter.NAMES));

        FileChannel channel = FileChannel.open(dir.resolve(DiskIndexWriter.POSTINGS), StandardOpenOption.READ);
        long size = channel.size();
        int numSegments = (int) ((size + config.segmentSizeBytes - 1) / config.segmentSizeBytes);
        MappedByteBuffer[] segments = new MappedByteBuffer[Math.max(1, numSegments)];
        for (int s = 0; s < numSegments; s++) {
            long start = (long) s * config.segmentSizeBytes;
            long len = Math.min(config.segmentSizeBytes, size - start);
            segments[s] = channel.map(FileChannel.MapMode.READ_ONLY, start, len);
        }
        return new DiskIndex(dir, config, numDocs, totalTokens, dict, docLengths, nameOffsets,
                namesBuf, channel, segments, size);
    }

    ByteBuffer regionAt(long offset) {
        int seg = (int) (offset / segmentSizeBytes);
        int within = (int) (offset % segmentSizeBytes);
        ByteBuffer b = segments[seg].duplicate();
        b.position(within);
        return b;
    }

    @Override
    public PostingsCursor cursor(String term) {
        TermEntry e = dictionary.get(term);
        return e == null ? null : new DiskPostingsCursor(this, e.offset, e.docFreq);
    }

    @Override
    public int docFreq(String term) {
        TermEntry e = dictionary.get(term);
        return e == null ? 0 : e.docFreq;
    }

    @Override
    public int numDocs() {
        return numDocs;
    }

    @Override
    public long totalTokens() {
        return totalTokens;
    }

    @Override
    public double avgDocLength() {
        return avgDocLength;
    }

    @Override
    public int docLength(int docId) {
        return docLengths[docId];
    }

    @Override
    public String docName(int docId) {
        int start = nameOffsets[docId];
        int end = nameOffsets[docId + 1];
        byte[] bytes = new byte[end - start];
        ByteBuffer b = namesBuf.duplicate();
        b.position(start);
        b.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public Set<String> terms() {
        return dictionary.keySet();
    }

    public long postingsSizeBytes() {
        return postingsSize;
    }

    @Override
    public void close() throws IOException {
        postingsChannel.close();
    }

    private static Map<String, TermEntry> loadDictionary(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        ByteCursor c = new ByteCursor(data);
        int numTerms = c.readIntBE();
        Map<String, TermEntry> dict = new HashMap<>(numTerms * 2);
        String prev = "";
        long offset = 0;
        for (int i = 0; i < numTerms; i++) {
            int shared = c.readVarInt();
            int suffixLen = c.readVarInt();
            String term = prev.substring(0, shared) + c.readString(suffixLen);
            int df = c.readVarInt();
            offset += c.readVarLong();
            dict.put(term, new TermEntry(offset, df));
            prev = term;
        }
        return dict;
    }

    private static int[] loadDocLengths(Path path, IndexConfig config) throws IOException {
        byte[] data = Files.readAllBytes(path);
        ByteCursor c = new ByteCursor(data);
        int n = c.readIntBE();
        c.readIntBE();
        int[] lengths = new int[n];
        ByteBuffer bb = ByteBuffer.wrap(data, c.pos(), data.length - c.pos());
        IntCodec freqCodec = config.freqBase();
        int bs = config.blockSize;
        int[] tmp = new int[bs];
        for (int s = 0; s < n; s += bs) {
            int len = Math.min(bs, n - s);
            freqCodec.decode(bb, tmp, len);
            System.arraycopy(tmp, 0, lengths, s, len);
        }
        return lengths;
    }

    private static int[] loadNameOffsets(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        ByteCursor c = new ByteCursor(data);
        int n = c.readIntBE();
        c.readIntBE();
        int[] offsets = new int[n + 1];
        ByteBuffer bb = ByteBuffer.wrap(data, c.pos(), data.length - c.pos());
        new DeltaCodec(new VarByteCodec()).decode(bb, offsets, n + 1);
        return offsets;
    }

    private static MappedByteBuffer mapWhole(Path path) throws IOException {
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            return ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
        }
    }

    private static final class ByteCursor {
        private final byte[] data;
        private int pos;

        ByteCursor(byte[] data) {
            this.data = data;
        }

        int pos() {
            return pos;
        }

        int readIntBE() {
            int v = ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                    | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
            pos += 4;
            return v;
        }

        int readVarInt() {
            int v = 0, shift = 0, b;
            do {
                b = data[pos++] & 0xFF;
                v |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            return v;
        }

        long readVarLong() {
            long v = 0;
            int shift = 0, b;
            do {
                b = data[pos++] & 0xFF;
                v |= (long) (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            return v;
        }

        String readString(int len) {
            String s = new String(data, pos, len, StandardCharsets.UTF_8);
            pos += len;
            return s;
        }
    }
}
