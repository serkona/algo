package ru.itmo.search.compress;

import java.nio.ByteBuffer;

public final class PForDeltaCodec implements IntCodec {

    private static final double COVERAGE = 0.90;

    @Override
    public String name() {
        return "pfor";
    }

    static int chooseBaseBits(int[] values, int len) {
        if (len == 0) {
            return 0;
        }
        int[] hist = new int[33];
        for (int i = 0; i < len; i++) {
            int v = values[i];
            int bits = v == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(v);
            hist[bits]++;
        }
        int target = (int) Math.ceil(COVERAGE * len);
        int cum = 0;
        for (int b = 0; b <= 32; b++) {
            cum += hist[b];
            if (cum >= target) {
                return b;
            }
        }
        return 32;
    }

    @Override
    public void encode(int[] values, int len, ByteWriter out) {
        int b = chooseBaseBits(values, len);
        long mask = (b == 0) ? 0 : ((b == 32) ? 0xFFFFFFFFL : ((1L << b) - 1));

        int nExc = 0;
        for (int i = 0; i < len; i++) {
            if ((values[i] & ~mask) != 0) {
                nExc++;
            }
        }
        out.putByte(b);
        out.putVarInt(nExc);
        if (nExc > 0) {
            for (int i = 0; i < len; i++) {
                if ((values[i] & ~mask) != 0) {
                    out.putVarInt(i);
                    out.putVarInt(values[i]);
                }
            }
        }
        if (b == 0) {
            return;
        }
        long acc = 0;
        int accBits = 0;
        for (int i = 0; i < len; i++) {
            long packed = ((values[i] & ~mask) != 0) ? 0L : (values[i] & mask);
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

    @Override
    public void decode(ByteBuffer in, int[] out, int len) {
        int b = in.get() & 0xFF;
        int nExc = readVarInt(in);
        int[] excPos = null;
        int[] excVal = null;
        if (nExc > 0) {
            excPos = new int[nExc];
            excVal = new int[nExc];
            for (int e = 0; e < nExc; e++) {
                excPos[e] = readVarInt(in);
                excVal[e] = readVarInt(in);
            }
        }
        BitPackingCodec.unpack(in, out, len, b);
        if (nExc > 0) {
            for (int e = 0; e < nExc; e++) {
                out[excPos[e]] = excVal[e];
            }
        }
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
