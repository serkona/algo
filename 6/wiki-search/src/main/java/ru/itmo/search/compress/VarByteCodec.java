package ru.itmo.search.compress;

import java.nio.ByteBuffer;

public final class VarByteCodec implements IntCodec {

    @Override
    public String name() {
        return "vbyte";
    }

    @Override
    public void encode(int[] values, int len, ByteWriter out) {
        for (int i = 0; i < len; i++) {
            out.putVarInt(values[i]);
        }
    }

    @Override
    public void decode(ByteBuffer in, int[] out, int len) {
        for (int i = 0; i < len; i++) {
            int v = 0;
            int shift = 0;
            int b;
            do {
                b = in.get() & 0xFF;
                v |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            out[i] = v;
        }
    }
}
