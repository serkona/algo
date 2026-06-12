package ru.itmo.bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import ru.itmo.map.ConcurrentHashMapImpl;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class HashMapBenchmark {

    private static final int KEY_RANGE = 10_000;
    private static final int INITIAL_ENTRIES = 5_000;

    @State(Scope.Benchmark)
    public static class ConcurrentState {
        ConcurrentHashMapImpl<Integer, Integer> map;

        @Setup(Level.Iteration)
        public void setup() {
            map = new ConcurrentHashMapImpl<>(KEY_RANGE * 2);
            for (int i = 0; i < INITIAL_ENTRIES; i++) map.put(i, i);
        }
    }

    @State(Scope.Benchmark)
    public static class JdkConcurrentState {
        ConcurrentHashMap<Integer, Integer> map;

        @Setup(Level.Iteration)
        public void setup() {
            map = new ConcurrentHashMap<>(KEY_RANGE * 2);
            for (int i = 0; i < INITIAL_ENTRIES; i++) map.put(i, i);
        }
    }

    @State(Scope.Benchmark)
    public static class HashMapState {
        HashMap<Integer, Integer> map;

        @Setup(Level.Iteration)
        public void setup() {
            map = new HashMap<>(KEY_RANGE * 2);
            for (int i = 0; i < INITIAL_ENTRIES; i++) map.put(i, i);
        }
    }

    @Benchmark
    @Threads(1)
    public void singleThread_put_concurrent(ConcurrentState s) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        s.map.put(k, k);
    }

    @Benchmark
    @Threads(1)
    public void singleThread_put_jdk(JdkConcurrentState s) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        s.map.put(k, k);
    }

    @Benchmark
    @Threads(1)
    public void singleThread_put_hashmap(HashMapState s) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        s.map.put(k, k);
    }

    @Benchmark
    @Threads(1)
    public Integer singleThread_get_concurrent(ConcurrentState s) {
        return s.map.get(ThreadLocalRandom.current().nextInt(KEY_RANGE));
    }

    @Benchmark
    @Threads(1)
    public Integer singleThread_get_jdk(JdkConcurrentState s) {
        return s.map.get(ThreadLocalRandom.current().nextInt(KEY_RANGE));
    }

    @Benchmark
    @Threads(1)
    public Integer singleThread_get_hashmap(HashMapState s) {
        return s.map.get(ThreadLocalRandom.current().nextInt(KEY_RANGE));
    }

    @Benchmark
    @Threads(2)
    public void multiThread2_put(ConcurrentState s) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        s.map.put(k, k);
    }

    @Benchmark
    @Threads(4)
    public void multiThread4_put(ConcurrentState s) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        s.map.put(k, k);
    }

    @Benchmark
    @Threads(8)
    public void multiThread8_put(ConcurrentState s) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        s.map.put(k, k);
    }

    @Benchmark
    @Threads(2)
    public void multiThread2_put_jdk(JdkConcurrentState s) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        s.map.put(k, k);
    }

    @Benchmark
    @Threads(4)
    public void multiThread4_put_jdk(JdkConcurrentState s) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        s.map.put(k, k);
    }

    @Benchmark
    @Threads(8)
    public void multiThread8_put_jdk(JdkConcurrentState s) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        s.map.put(k, k);
    }

    @Benchmark
    @Threads(2)
    public Integer multiThread2_get(ConcurrentState s) {
        return s.map.get(ThreadLocalRandom.current().nextInt(KEY_RANGE));
    }

    @Benchmark
    @Threads(4)
    public Integer multiThread4_get(ConcurrentState s) {
        return s.map.get(ThreadLocalRandom.current().nextInt(KEY_RANGE));
    }

    @Benchmark
    @Threads(8)
    public Integer multiThread8_get(ConcurrentState s) {
        return s.map.get(ThreadLocalRandom.current().nextInt(KEY_RANGE));
    }

    @Benchmark
    @Threads(2)
    public Integer multiThread2_get_jdk(JdkConcurrentState s) {
        return s.map.get(ThreadLocalRandom.current().nextInt(KEY_RANGE));
    }

    @Benchmark
    @Threads(4)
    public Integer multiThread4_get_jdk(JdkConcurrentState s) {
        return s.map.get(ThreadLocalRandom.current().nextInt(KEY_RANGE));
    }

    @Benchmark
    @Threads(8)
    public Integer multiThread8_get_jdk(JdkConcurrentState s) {
        return s.map.get(ThreadLocalRandom.current().nextInt(KEY_RANGE));
    }

    @State(Scope.Thread)
    public static class ThreadRng {
        int counter;
    }

    @Benchmark
    @Threads(4)
    public Object readHeavy4_concurrent(ConcurrentState s, ThreadRng rng) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (rng.counter++ % 10 == 0) { s.map.put(k, k); return null; }
        return s.map.get(k);
    }

    @Benchmark
    @Threads(4)
    public Object readHeavy4_jdk(JdkConcurrentState s, ThreadRng rng) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (rng.counter++ % 10 == 0) { s.map.put(k, k); return null; }
        return s.map.get(k);
    }

    @Benchmark
    @Threads(8)
    public Object readHeavy8_concurrent(ConcurrentState s, ThreadRng rng) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (rng.counter++ % 10 == 0) { s.map.put(k, k); return null; }
        return s.map.get(k);
    }

    @Benchmark
    @Threads(8)
    public Object readHeavy8_jdk(JdkConcurrentState s, ThreadRng rng) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (rng.counter++ % 10 == 0) { s.map.put(k, k); return null; }
        return s.map.get(k);
    }

    @Benchmark
    @Threads(4)
    public Object writeHeavy4_concurrent(ConcurrentState s, ThreadRng rng) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (rng.counter++ % 5 != 0) { s.map.put(k, k); return null; }
        return s.map.get(k);
    }

    @Benchmark
    @Threads(4)
    public Object writeHeavy4_jdk(JdkConcurrentState s, ThreadRng rng) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (rng.counter++ % 5 != 0) { s.map.put(k, k); return null; }
        return s.map.get(k);
    }

    @Benchmark
    @Threads(8)
    public Object writeHeavy8_concurrent(ConcurrentState s, ThreadRng rng) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (rng.counter++ % 5 != 0) { s.map.put(k, k); return null; }
        return s.map.get(k);
    }

    @Benchmark
    @Threads(8)
    public Object writeHeavy8_jdk(JdkConcurrentState s, ThreadRng rng) {
        int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (rng.counter++ % 5 != 0) { s.map.put(k, k); return null; }
        return s.map.get(k);
    }

    @Benchmark
    @Threads(4)
    public Integer merge4_concurrent(ConcurrentState s) {
        int k = ThreadLocalRandom.current().nextInt(100);
        return s.map.merge(k, 1, Integer::sum);
    }

    @Benchmark
    @Threads(4)
    public Integer merge4_jdk(JdkConcurrentState s) {
        int k = ThreadLocalRandom.current().nextInt(100);
        return s.map.merge(k, 1, Integer::sum);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(HashMapBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
