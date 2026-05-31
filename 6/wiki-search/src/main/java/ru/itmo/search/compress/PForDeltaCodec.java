package ru.itmo.search.compress;

import java.nio.ByteBuffer;

public final class PForDeltaCodec implements IntCodec {

    @Override
    public String name() {
        return "pfor";
    }

    @Override
    public void encode(int[] values, int len, ByteWriter out) {
        int b = chooseBestBits(values, len);
        long mask = mask(b);

        int nExc = 0;
        for (int i = 0; i < len; i++) {
            if (isException(values[i], mask)) {
                nExc++;
            }
        }
        out.putByte(b);
        out.putVarInt(nExc);
        long acc = 0;
        int accBits = 0;
        if (b > 0) {
            for (int i = 0; i < len; i++) {
                long packed = values[i] & mask;
                acc |= packed << accBits;
                accBits += b;
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
        int prevPos = 0;
        for (int i = 0; i < len; i++) {
            if (isException(values[i], mask)) {
                out.putVarInt(i - prevPos);
                out.putVarInt(values[i] >>> b);
                prevPos = i;
            }
        }
    }

    @Override
    public void decode(ByteBuffer in, int[] out, int len) {
        int b = in.get() & 0xFF;
        int nExc = readVarInt(in);
        BitPackingCodec.unpack(in, out, len, b);
        int pos = 0;
        for (int e = 0; e < nExc; e++) {
            pos += readVarInt(in);
            int high = readVarInt(in);
            out[pos] |= high << b;
        }
    }

    private static int chooseBestBits(int[] values, int len) {
        int maxBits = bitsRequired(values, len);
        int bestBits = maxBits;
        int bestBytes = Integer.MAX_VALUE;
        for (int b = 0; b <= maxBits; b++) {
            int bytes = estimatedBytes(values, len, b);
            if (bytes < bestBytes) {
                bestBytes = bytes;
                bestBits = b;
            }
        }
        return bestBits;
    }

    private static int estimatedBytes(int[] values, int len, int b) {
        long mask = mask(b);
        int bytes = 1 + varIntBytes(exceptionCount(values, len, mask)) + ((len * b + 7) >>> 3);
        int prevPos = 0;
        for (int i = 0; i < len; i++) {
            if (isException(values[i], mask)) {
                bytes += varIntBytes(i - prevPos);
                bytes += varIntBytes(values[i] >>> b);
                prevPos = i;
            }
        }
        return bytes;
    }

    private static int exceptionCount(int[] values, int len, long mask) {
        int count = 0;
        for (int i = 0; i < len; i++) {
            if (isException(values[i], mask)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isException(int value, long mask) {
        return (value & ~mask) != 0;
    }

    private static long mask(int bits) {
        if (bits == 0) {
            return 0;
        }
        return bits == 32 ? 0xFFFFFFFFL : ((1L << bits) - 1);
    }

    private static int bitsRequired(int[] values, int len) {
        int max = 0;
        for (int i = 0; i < len; i++) {
            max |= values[i];
        }
        return max == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(max);
    }

    private static int varIntBytes(int value) {
        int bytes = 1;
        int v = value;
        while ((v & ~0x7F) != 0) {
            bytes++;
            v >>>= 7;
        }
        return bytes;
    }

    static int readVarInt(ByteBuffer in) {
        int v = 0;
        int shift = 0;
        int bb;
        do {
            bb = in.get() & 0xFF;
            v |= (bb & 0x7F) << shift;
            shift += 7;
        } while ((bb & 0x80) != 0);
        return v;
    }
}
