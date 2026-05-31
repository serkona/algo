package ru.itmo.search.compress;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CodecTest {

    private static int[] roundTrip(IntCodec codec, int[] values) {
        ByteWriter out = new ByteWriter();
        codec.encode(values, values.length, out);
        ByteBuffer in = ByteBuffer.wrap(out.toByteArray());
        int[] decoded = new int[values.length];
        codec.decode(in, decoded, values.length);
        return decoded;
    }

    private static final IntCodec[] BASE = {
            new RawIntCodec(), new VarByteCodec(), new BitPackingCodec(), new PForDeltaCodec()
    };

    @Test
    void baseCodecsRoundTripRandom() {
        Random rng = new Random(7);
        for (IntCodec codec : BASE) {
            for (int trial = 0; trial < 200; trial++) {
                int n = 1 + rng.nextInt(300);
                int[] values = new int[n];
                for (int i = 0; i < n; i++) {
                    values[i] = rng.nextInt(1 << (1 + rng.nextInt(30)));
                }
                assertArrayEquals(values, roundTrip(codec, values), codec.name());
            }
        }
    }

    @Test
    void edgeCasesAllZerosAndMax() {
        int[][] cases = {
                {0}, {0, 0, 0, 0}, {Integer.MAX_VALUE}, {0, Integer.MAX_VALUE, 1},
                {1}, {1, 2, 3, 4, 5}, new int[129]
        };
        for (IntCodec codec : BASE) {
            for (int[] c : cases) {
                assertArrayEquals(c, roundTrip(codec, c), codec.name());
            }
        }
    }

    @Test
    void deltaCodecsRoundTripSorted() {
        Random rng = new Random(11);
        IntCodec[] delta = {
                new DeltaCodec(new VarByteCodec()),
                new DeltaCodec(new BitPackingCodec()),
                new DeltaCodec(new PForDeltaCodec())
        };
        for (IntCodec codec : delta) {
            for (int trial = 0; trial < 200; trial++) {
                int n = 1 + rng.nextInt(300);
                int[] values = new int[n];
                int acc = rng.nextInt(100);
                for (int i = 0; i < n; i++) {
                    acc += rng.nextInt(50);
                    values[i] = acc;
                }
                assertArrayEquals(values, roundTrip(codec, values), codec.name());
            }
        }
    }

    @Test
    void deltaShrinksSortedDocIds() {
        int n = 4096;
        int[] docIds = new int[n];
        int acc = 0;
        Random rng = new Random(1);
        for (int i = 0; i < n; i++) {
            acc += 1 + rng.nextInt(8);
            docIds[i] = acc;
        }
        int rawBytes = encodedSize(new RawIntCodec(), docIds);
        int deltaPfor = encodedSize(new DeltaCodec(new PForDeltaCodec()), docIds);
        int deltaBitpack = encodedSize(new DeltaCodec(new BitPackingCodec()), docIds);
        assertTrue(deltaPfor < rawBytes / 3, "pfor=" + deltaPfor + " raw=" + rawBytes);
        assertTrue(deltaBitpack < rawBytes / 3, "bitpack=" + deltaBitpack + " raw=" + rawBytes);
    }

    @Test
    void pforBeatsBitpackWithOutliers() {
        int n = 1024;
        int[] gaps = new int[n];
        Random rng = new Random(3);
        for (int i = 0; i < n; i++) {
            gaps[i] = rng.nextInt(4);
        }
        for (int i = 0; i < 20; i++) {
            gaps[rng.nextInt(n)] = 1_000_000 + rng.nextInt(1000);
        }
        int bitpack = encodedSize(new BitPackingCodec(), gaps);
        int pfor = encodedSize(new PForDeltaCodec(), gaps);
        assertTrue(pfor < bitpack, "pfor=" + pfor + " bitpack=" + bitpack);
    }

    private static int encodedSize(IntCodec codec, int[] values) {
        ByteWriter out = new ByteWriter();
        codec.encode(values, values.length, out);
        return out.size();
    }
}
