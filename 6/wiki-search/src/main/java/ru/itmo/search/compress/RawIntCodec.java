package ru.itmo.search.compress;

import java.nio.ByteBuffer;

public final class RawIntCodec implements IntCodec {

    @Override
    public String name() {
        return "raw";
    }

    @Override
    public void encode(int[] values, int len, ByteWriter out) {
        for (int i = 0; i < len; i++) {
            out.putInt(values[i]);
        }
    }

    @Override
    public void decode(ByteBuffer in, int[] out, int len) {
        for (int i = 0; i < len; i++) {
            int b0 = in.get() & 0xFF;
            int b1 = in.get() & 0xFF;
            int b2 = in.get() & 0xFF;
            int b3 = in.get() & 0xFF;
            out[i] = b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
        }
    }
}
