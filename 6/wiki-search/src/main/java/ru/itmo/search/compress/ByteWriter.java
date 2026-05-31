package ru.itmo.search.compress;

import java.util.Arrays;

public final class ByteWriter {
    private byte[] buf;
    private int len;

    public ByteWriter() {
        this(64);
    }

    public ByteWriter(int initialCapacity) {
        this.buf = new byte[Math.max(8, initialCapacity)];
    }

    private void ensure(int extra) {
        if (len + extra > buf.length) {
            int newCap = buf.length;
            while (newCap < len + extra) {
                newCap <<= 1;
            }
            buf = Arrays.copyOf(buf, newCap);
        }
    }

    public void putByte(int b) {
        ensure(1);
        buf[len++] = (byte) b;
    }

    public void putInt(int v) {
        ensure(4);
        buf[len++] = (byte) (v);
        buf[len++] = (byte) (v >>> 8);
        buf[len++] = (byte) (v >>> 16);
        buf[len++] = (byte) (v >>> 24);
    }

    public void putBytes(byte[] src, int from, int length) {
        ensure(length);
        System.arraycopy(src, from, buf, len, length);
        len += length;
    }

    public void putVarInt(int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            putByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        putByte(v & 0x7F);
    }

    public int size() {
        return len;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buf, len);
    }

    public byte[] backing() {
        return buf;
    }
}
