package ru.itmo.search.compress;

import java.nio.ByteBuffer;

public interface IntCodec {

    String name();

    void encode(int[] values, int len, ByteWriter out);

    void decode(ByteBuffer in, int[] out, int len);
}
