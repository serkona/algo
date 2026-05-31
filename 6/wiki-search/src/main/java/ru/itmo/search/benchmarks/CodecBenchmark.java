package ru.itmo.search.benchmarks;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import ru.itmo.search.compress.ByteWriter;
import ru.itmo.search.compress.Codecs;
import ru.itmo.search.compress.IntCodec;

@State(Scope.Thread)
@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Measurement(iterations = 5, time = 1)
@org.openjdk.jmh.annotations.Warmup(iterations = 3, time = 1)
public class CodecBenchmark {

    @Param({"raw", "vbyte", "bitpack", "pfor", "delta-vbyte", "delta-bitpack", "delta-pfor"})
    public String codecName;

    private IntCodec codec;
    private int[] values;
    private byte[] encoded;
    private int[] decodeBuf;
    private final int len = 128;

    @Setup
    public void setup() {
        codec = Codecs.byName(codecName);
        Random rng = new Random(1);
        values = new int[len];
        boolean delta = codecName.startsWith("delta-");
        int acc = 0;
        for (int i = 0; i < len; i++) {
            int gap = 1 + rng.nextInt(40);
            if (delta) {
                acc += gap;
                values[i] = acc;
            } else {
                values[i] = gap;
            }
        }
        ByteWriter w = new ByteWriter();
        codec.encode(values, len, w);
        encoded = w.toByteArray();
        decodeBuf = new int[len];
    }

    @Benchmark
    public void encode(Blackhole bh) {
        ByteWriter w = new ByteWriter(256);
        codec.encode(values, len, w);
        bh.consume(w.size());
        bh.consume(w.backing());
    }

    @Benchmark
    public void decode(Blackhole bh) {
        ByteBuffer in = ByteBuffer.wrap(encoded);
        codec.decode(in, decodeBuf, len);
        bh.consume(decodeBuf);
    }
}
