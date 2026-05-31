package ru.itmo.search.compress;

import java.nio.ByteBuffer;

public final class DeltaCodec implements IntCodec {

    private final IntCodec inner;

    public DeltaCodec(IntCodec inner) {
        this.inner = inner;
    }

    public IntCodec inner() {
        return inner;
    }

    @Override
    public String name() {
        return "delta-" + inner.name();
    }

    @Override
    public void encode(int[] values, int len, ByteWriter out) {
        int[] gaps = new int[len];
        int prev = 0;
        for (int i = 0; i < len; i++) {
            gaps[i] = values[i] - prev;
            prev = values[i];
        }
        inner.encode(gaps, len, out);
    }

    @Override
    public void decode(ByteBuffer in, int[] out, int len) {
        inner.decode(in, out, len);
        int prev = 0;
        for (int i = 0; i < len; i++) {
            prev += out[i];
            out[i] = prev;
        }
    }
}
