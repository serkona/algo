package ru.itmo.search.compress;

import java.nio.ByteBuffer;

public final class BitPackingCodec implements IntCodec {

    private static final int CHUNK_SIZE = 32;
    private static final int CHUNKED_MARKER = 0x80;

    @Override
    public String name() {
        return "bitpack";
    }

    static int bitsRequired(int[] values, int len) {
        int max = 0;
        for (int i = 0; i < len; i++) {
            max |= values[i];
        }
        return max == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(max);
    }

    static void pack(int[] values, int len, int bits, ByteWriter out) {
        if (bits == 0) {
            return;
        }
        long mask = (bits == 32) ? 0xFFFFFFFFL : ((1L << bits) - 1);
        long acc = 0;
        int accBits = 0;
        for (int i = 0; i < len; i++) {
            acc |= (values[i] & mask) << accBits;
            accBits += bits;
            while (accBits >= 8) {
                out.putByte((int) (acc & 0xFF));
                acc >>>= 8;
                accBits -= 8;
            }
        }
        if (accBits > 0) {
            out.putByte((int) (acc & 0xFF));
        }
    }

    static void unpack(ByteBuffer in, int[] out, int len, int bits) {
        if (bits == 0) {
            for (int i = 0; i < len; i++) {
                out[i] = 0;
            }
            return;
        }
        long mask = (bits == 32) ? 0xFFFFFFFFL : ((1L << bits) - 1);
        long acc = 0;
        int accBits = 0;
        for (int i = 0; i < len; i++) {
            while (accBits < bits) {
                acc |= (long) (in.get() & 0xFF) << accBits;
                accBits += 8;
            }
            out[i] = (int) (acc & mask);
            acc >>>= bits;
            accBits -= bits;
        }
    }

    @Override
    public void encode(int[] values, int len, ByteWriter out) {
        int bits = bitsRequired(values, len);
        int wholeBytes = 1 + packedBytes(len, bits);
        int chunkedBytes = chunkedBytes(values, len);
        if (chunkedBytes < wholeBytes) {
            out.putByte(CHUNKED_MARKER);
            for (int off = 0; off < len; off += CHUNK_SIZE) {
                int chunkLen = Math.min(CHUNK_SIZE, len - off);
                int chunkBits = bitsRequired(values, off, chunkLen);
                out.putByte(chunkBits);
                pack(values, off, chunkLen, chunkBits, out);
            }
        } else {
            out.putByte(bits);
            pack(values, len, bits, out);
        }
    }

    @Override
    public void decode(ByteBuffer in, int[] out, int len) {
        int header = in.get() & 0xFF;
        if (header == CHUNKED_MARKER) {
            for (int off = 0; off < len; off += CHUNK_SIZE) {
                int chunkLen = Math.min(CHUNK_SIZE, len - off);
                int bits = in.get() & 0xFF;
                unpack(in, out, off, chunkLen, bits);
            }
        } else {
            unpack(in, out, len, header);
        }
    }

    private static int bitsRequired(int[] values, int off, int len) {
        int max = 0;
        for (int i = 0; i < len; i++) {
            max |= values[off + i];
        }
        return max == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(max);
    }

    private static int packedBytes(int len, int bits) {
        return (len * bits + 7) >>> 3;
    }

    private static int chunkedBytes(int[] values, int len) {
        if (len <= CHUNK_SIZE) {
            return Integer.MAX_VALUE;
        }
        int total = 1;
        for (int off = 0; off < len; off += CHUNK_SIZE) {
            int chunkLen = Math.min(CHUNK_SIZE, len - off);
            total += 1 + packedBytes(chunkLen, bitsRequired(values, off, chunkLen));
        }
        return total;
    }

    private static void pack(int[] values, int off, int len, int bits, ByteWriter out) {
        if (bits == 0) {
            return;
        }
        long mask = (bits == 32) ? 0xFFFFFFFFL : ((1L << bits) - 1);
        long acc = 0;
        int accBits = 0;
        for (int i = 0; i < len; i++) {
            acc |= (values[off + i] & mask) << accBits;
            accBits += bits;
            while (accBits >= 8) {
                out.putByte((int) (acc & 0xFF));
                acc >>>= 8;
                accBits -= 8;
            }
        }
        if (accBits > 0) {
            out.putByte((int) (acc & 0xFF));
        }
    }

    private static void unpack(ByteBuffer in, int[] out, int off, int len, int bits) {
        if (bits == 0) {
            for (int i = 0; i < len; i++) {
                out[off + i] = 0;
            }
            return;
        }
        long mask = (bits == 32) ? 0xFFFFFFFFL : ((1L << bits) - 1);
        long acc = 0;
        int accBits = 0;
        for (int i = 0; i < len; i++) {
            while (accBits < bits) {
                acc |= (long) (in.get() & 0xFF) << accBits;
                accBits += 8;
            }
            out[off + i] = (int) (acc & mask);
            acc >>>= bits;
            accBits -= bits;
        }
    }
}
