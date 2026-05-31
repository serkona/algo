package ru.itmo.search.index.disk;

import ru.itmo.search.compress.Codecs;
import ru.itmo.search.compress.IntCodec;

public final class IndexConfig {

    public static final long DEFAULT_SEGMENT_SIZE_BYTES = 1L << 30;

    public final int blockSize;
    public final String docIdCodec;
    public final String freqCodec;
    public final String posCodec;
    public final long segmentSizeBytes;

    public IndexConfig(int blockSize, String docIdCodec, String freqCodec, String posCodec) {
        this(blockSize, docIdCodec, freqCodec, posCodec, DEFAULT_SEGMENT_SIZE_BYTES);
    }

    public IndexConfig(int blockSize, String docIdCodec, String freqCodec, String posCodec, long segmentSizeBytes) {
        this.blockSize = blockSize;
        this.docIdCodec = docIdCodec;
        this.freqCodec = freqCodec;
        this.posCodec = posCodec;
        this.segmentSizeBytes = segmentSizeBytes;
    }

    public static IndexConfig defaults() {
        return new IndexConfig(256, "pfor", "bitpack", "pfor");
    }

    public IntCodec docIdBase() {
        return Codecs.byName(docIdCodec);
    }

    public IntCodec freqBase() {
        return Codecs.byName(freqCodec);
    }

    public IntCodec posBase() {
        return Codecs.byName(posCodec);
    }

    @Override
    public String toString() {
        return "blockSize=" + blockSize + ",docId=" + docIdCodec + ",freq=" + freqCodec
                + ",pos=" + posCodec + ",segmentSizeBytes=" + segmentSizeBytes;
    }
}
