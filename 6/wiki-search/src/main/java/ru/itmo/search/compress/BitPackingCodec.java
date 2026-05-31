package ru.itmo.search.compress;

import java.nio.ByteBuffer;

public final class BitPackingCodec implements IntCodec {

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
        out.putByte(bits);
        pack(values, len, bits, out);
    }

    @Override
    public void decode(ByteBuffer in, int[] out, int len) {
        int bits = in.get() & 0xFF;
        unpack(in, out, len, bits);
    }
}
