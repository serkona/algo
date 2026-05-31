package ru.itmo.search.index.disk;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import ru.itmo.search.compress.ByteWriter;
import ru.itmo.search.compress.DeltaCodec;
import ru.itmo.search.compress.IntCodec;
import ru.itmo.search.compress.VarByteCodec;
import ru.itmo.search.index.MemoryIndex;
import ru.itmo.search.index.RawPostings;

public final class DiskIndexWriter {
    public static final String META = "meta.properties";
    public static final String POSTINGS = "postings.bin";
    public static final String DICT = "terms.dict";
    public static final String DOCLEN = "doclen.bin";
    public static final String NAMES = "names.bin";
    public static final String NAMES_IDX = "names.idx";
    public static final String POSTINGS_FORMAT = "2";

    private final IndexConfig config;
    private final IntCodec deltaDocId;
    private final IntCodec freqCodec;
    private final IntCodec posCodec;
    private final IntCodec skipCodec = new DeltaCodec(new VarByteCodec());

    public DiskIndexWriter(IndexConfig config) {
        this.config = config;
        this.deltaDocId = new DeltaCodec(config.docIdBase());
        this.freqCodec = config.freqBase();
        this.posCodec = config.posBase();
    }

    public void write(MemoryIndex index, Path dir) throws IOException {
        Files.createDirectories(dir);
        writePostingsAndDict(index, dir);
        writeDocStore(index, dir);
        writeMeta(index, dir);
    }

    private void writePostingsAndDict(MemoryIndex index, Path dir) throws IOException {
        try (DataOutputStream postings = open(dir, POSTINGS);
             DataOutputStream dict = open(dir, DICT)) {

            dict.writeInt(index.terms().size());
            long offset = 0;
            String prevTerm = "";
            long prevOffset = 0;
            ByteWriter region = new ByteWriter(1 << 16);

            for (String term : index.terms()) {
                RawPostings p = index.raw(term);
                region = encodeTerm(p);
                if (region.size() > config.segmentSizeBytes) {
                    throw new IllegalStateException("single term region exceeds segment size: " + term);
                }
                long segOff = offset % config.segmentSizeBytes;
                if (segOff + region.size() > config.segmentSizeBytes) {
                    long pad = config.segmentSizeBytes - segOff;
                    writeZeros(postings, pad);
                    offset += pad;
                }
                postings.write(region.backing(), 0, region.size());

                int shared = commonPrefix(prevTerm, term);
                byte[] suffix = term.substring(shared).getBytes(StandardCharsets.UTF_8);
                writeVarInt(dict, shared);
                writeVarInt(dict, suffix.length);
                dict.write(suffix);
                writeVarInt(dict, p.docCount());
                writeVarLong(dict, offset - prevOffset);

                prevTerm = term;
                prevOffset = offset;
                offset += region.size();
            }
        }
    }

    ByteWriter encodeTerm(RawPostings p) {
        int df = p.docCount();
        int blockSize = config.blockSize;
        int numBlocks = (df + blockSize - 1) / blockSize;

        ByteWriter blocks = new ByteWriter(1 << 12);
        int[] blockLastDoc = new int[numBlocks];
        int[] blockOffset = new int[numBlocks + 1];
        int[] rebased = new int[blockSize];
        int[] freqTmp = new int[blockSize];
        int[] posGapTmp = new int[16];

        int prevBlockLast = 0;
        for (int b = 0; b < numBlocks; b++) {
            blockOffset[b] = blocks.size();
            int start = b * blockSize;
            int len = Math.min(blockSize, df - start);

            for (int i = 0; i < len; i++) {
                rebased[i] = p.docIds[start + i] - prevBlockLast;
            }
            deltaDocId.encode(rebased, len, blocks);

            for (int i = 0; i < len; i++) {
                freqTmp[i] = p.freqs[start + i];
            }
            freqCodec.encode(freqTmp, len, blocks);

            int totalPositions = 0;
            for (int i = 0; i < len; i++) {
                totalPositions += p.freqs[start + i];
            }
            if (posGapTmp.length < totalPositions) {
                posGapTmp = new int[Integer.highestOneBit(Math.max(1, totalPositions)) << 1];
            }
            int out = 0;
            for (int i = 0; i < len; i++) {
                int doc = start + i;
                int f = p.freqs[doc];
                int prev = 0;
                int base = p.posStart[doc];
                for (int j = 0; j < f; j++) {
                    int pos = p.positions[base + j];
                    posGapTmp[out++] = pos - prev;
                    prev = pos;
                }
            }
            posCodec.encode(posGapTmp, totalPositions, blocks);

            prevBlockLast = p.docIds[start + len - 1];
            blockLastDoc[b] = prevBlockLast;
        }
        blockOffset[numBlocks] = blocks.size();

        ByteWriter region = new ByteWriter(blocks.size() + 64);
        skipCodec.encode(blockLastDoc, numBlocks, region);
        skipCodec.encode(blockOffset, numBlocks + 1, region);
        region.putBytes(blocks.backing(), 0, blocks.size());
        return region;
    }

    private void writeDocStore(MemoryIndex index, Path dir) throws IOException {
        int n = index.numDocs();
        int[] lengths = new int[n];
        for (int d = 0; d < n; d++) {
            lengths[d] = index.docLength(d);
        }
        try (DataOutputStream out = open(dir, DOCLEN)) {
            out.writeInt(n);
            ByteWriter bw = new ByteWriter(1 << 16);
            int bs = config.blockSize;
            int[] tmp = new int[bs];
            for (int s = 0; s < n; s += bs) {
                int len = Math.min(bs, n - s);
                System.arraycopy(lengths, s, tmp, 0, len);
                freqCodec.encode(tmp, len, bw);
            }
            out.writeInt(bw.size());
            out.write(bw.backing(), 0, bw.size());
        }

        try (DataOutputStream namesOut = open(dir, NAMES);
             DataOutputStream idxOut = open(dir, NAMES_IDX)) {
            int[] offsets = new int[n + 1];
            int cur = 0;
            for (int d = 0; d < n; d++) {
                offsets[d] = cur;
                byte[] bytes = index.docName(d).getBytes(StandardCharsets.UTF_8);
                namesOut.write(bytes);
                cur += bytes.length;
            }
            offsets[n] = cur;
            idxOut.writeInt(n);
            ByteWriter bw = new ByteWriter(1 << 16);
            skipCodec.encode(offsets, n + 1, bw);
            idxOut.writeInt(bw.size());
            idxOut.write(bw.backing(), 0, bw.size());
        }
    }

    private void writeMeta(MemoryIndex index, Path dir) throws IOException {
        Properties props = new Properties();
        props.setProperty("numDocs", Integer.toString(index.numDocs()));
        props.setProperty("totalTokens", Long.toString(index.totalTokens()));
        props.setProperty("blockSize", Integer.toString(config.blockSize));
        props.setProperty("docIdCodec", config.docIdCodec);
        props.setProperty("freqCodec", config.freqCodec);
        props.setProperty("posCodec", config.posCodec);
        props.setProperty("postingsFormat", POSTINGS_FORMAT);
        props.setProperty("segmentSizeBytes", Long.toString(config.segmentSizeBytes));
        try (OutputStream out = Files.newOutputStream(dir.resolve(META))) {
            props.store(out, "wiki-search on-disk index metadata");
        }
    }

    private static void writeZeros(DataOutputStream out, long count) throws IOException {
        byte[] zeros = new byte[8192];
        long remaining = count;
        while (remaining > 0) {
            int chunk = (int) Math.min(zeros.length, remaining);
            out.write(zeros, 0, chunk);
            remaining -= chunk;
        }
    }

    private static DataOutputStream open(Path dir, String name) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(dir.resolve(name)), 1 << 16));
    }

    private static int commonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v & 0x7F);
    }

    private static void writeVarLong(DataOutputStream out, long value) throws IOException {
        long v = value;
        while ((v & ~0x7FL) != 0) {
            out.writeByte((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.writeByte((int) (v & 0x7F));
    }
}
