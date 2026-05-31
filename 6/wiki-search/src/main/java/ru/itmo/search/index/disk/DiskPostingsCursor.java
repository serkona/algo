package ru.itmo.search.index.disk;

import java.nio.ByteBuffer;
import ru.itmo.search.index.PositionalCursor;

public final class DiskPostingsCursor implements PositionalCursor {

    private final DiskIndex index;
    private final int df;
    private final int blockSize;
    private final int numBlocks;

    private final ByteBuffer base;
    private final int blocksBaseWithin;
    private final int[] blockLastDoc;
    private final int[] blockOffset;

    private final int[] docIds;
    private final int[] freqs;

    private int curBlock = -1;
    private int blockLen = 0;
    private int posInBlock = 0;
    private int current = -1;

    private int posSectionWithin = -1;
    private boolean posDecoded = false;
    private int[] blockPositions = new int[16];
    private final int[] posStart;
    private int[] curPosBuf = new int[8];

    public DiskPostingsCursor(DiskIndex index, long regionOffset, int df) {
        this.index = index;
        this.df = df;
        this.blockSize = index.blockSize;
        this.numBlocks = (df + blockSize - 1) / blockSize;
        this.blockLastDoc = new int[numBlocks];
        this.blockOffset = new int[numBlocks + 1];
        this.docIds = new int[blockSize];
        this.freqs = new int[blockSize];
        this.posStart = new int[blockSize + 1];

        ByteBuffer rb = index.regionAt(regionOffset);
        index.skipCodec.decode(rb, blockLastDoc, numBlocks);
        index.skipCodec.decode(rb, blockOffset, numBlocks + 1);
        this.blocksBaseWithin = rb.position();
        this.base = rb;
    }

    @Override
    public int docId() {
        return current;
    }

    @Override
    public int nextDoc() {
        if (curBlock < 0) {
            if (numBlocks == 0) {
                return current = NO_MORE;
            }
            loadBlock(0);
            posInBlock = 0;
        } else {
            posInBlock++;
            if (posInBlock >= blockLen) {
                if (curBlock + 1 >= numBlocks) {
                    return current = NO_MORE;
                }
                loadBlock(curBlock + 1);
                posInBlock = 0;
            }
        }
        return current = docIds[posInBlock];
    }

    @Override
    public int advance(int target) {
        if (current == NO_MORE) {
            return NO_MORE;
        }
        int startB = Math.max(curBlock, 0);
        int b = lowerBound(startB, target);
        if (b >= numBlocks) {
            return current = NO_MORE;
        }
        if (b != curBlock) {
            loadBlock(b);
            posInBlock = 0;
        }
        while (posInBlock < blockLen && docIds[posInBlock] < target) {
            posInBlock++;
        }
        return current = docIds[posInBlock];
    }

    private int lowerBound(int from, int target) {
        int lo = from;
        int hi = numBlocks;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (blockLastDoc[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private void loadBlock(int b) {
        int len = Math.min(blockSize, df - b * blockSize);
        int prevLast = b == 0 ? 0 : blockLastDoc[b - 1];

        ByteBuffer wb = base.duplicate();
        wb.position(blocksBaseWithin + blockOffset[b]);
        index.deltaDocId.decode(wb, docIds, len);
        for (int i = 0; i < len; i++) {
            docIds[i] += prevLast;
        }
        index.freqCodec.decode(wb, freqs, len);
        this.posSectionWithin = wb.position();
        this.posDecoded = false;
        this.curBlock = b;
        this.blockLen = len;
    }

    @Override
    public int freq() {
        return freqs[posInBlock];
    }

    @Override
    public long cost() {
        return df;
    }

    @Override
    public int[] positions() {
        if (!posDecoded) {
            decodeBlockPositions();
        }
        int f = freqs[posInBlock];
        int from = posStart[posInBlock];
        if (curPosBuf.length < f) {
            curPosBuf = new int[Integer.highestOneBit(Math.max(1, f)) << 1];
        }
        System.arraycopy(blockPositions, from, curPosBuf, 0, f);
        return curPosBuf;
    }

    private void decodeBlockPositions() {
        int total = 0;
        posStart[0] = 0;
        for (int i = 0; i < blockLen; i++) {
            total += freqs[i];
            posStart[i + 1] = total;
        }
        if (blockPositions.length < total) {
            blockPositions = new int[Integer.highestOneBit(Math.max(1, total)) << 1];
        }
        ByteBuffer wb = base.duplicate();
        wb.position(posSectionWithin);
        index.posCodec.decode(wb, blockPositions, total);
        int off = 0;
        for (int i = 0; i < blockLen; i++) {
            int f = freqs[i];
            int prev = 0;
            for (int j = 0; j < f; j++) {
                int idx = off + j;
                prev += blockPositions[idx];
                blockPositions[idx] = prev;
            }
            off += f;
        }
        posDecoded = true;
    }
}
