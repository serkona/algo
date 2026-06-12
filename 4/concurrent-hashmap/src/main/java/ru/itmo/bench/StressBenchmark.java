package ru.itmo.bench;

import org.openjdk.jmh.annotations.*;
import ru.itmo.map.ConcurrentHashMapImpl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Fork(3)
@State(Scope.Benchmark)
public class StressBenchmark {

    static final class Collider implements Comparable<Collider> {
        final int v;
        Collider(int v) { this.v = v; }
        @Override public int hashCode() { return 0; }
        @Override public boolean equals(Object o) {
            return o instanceof Collider c && c.v == v;
        }
        @Override public int compareTo(Collider o) { return Integer.compare(v, o.v); }
    }

    static final int COLLISIONS = 5_000;

    @State(Scope.Benchmark)
    public static class OurCollisionState {
        ConcurrentHashMapImpl<Collider, Integer> map;
        Collider[] keys;
        @Setup(Level.Trial)
        public void setup() {
            map = new ConcurrentHashMapImpl<>(64);
            keys = new Collider[COLLISIONS];
            for (int i = 0; i < COLLISIONS; i++) { keys[i] = new Collider(i); map.put(keys[i], i); }
        }
    }

    @State(Scope.Benchmark)
    public static class JdkCollisionState {
        ConcurrentHashMap<Collider, Integer> map;
        Collider[] keys;
        @Setup(Level.Trial)
        public void setup() {
            map = new ConcurrentHashMap<>(64);
            keys = new Collider[COLLISIONS];
            for (int i = 0; i < COLLISIONS; i++) { keys[i] = new Collider(i); map.put(keys[i], i); }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(1)
    public Integer collisionGet_concurrent(OurCollisionState s) {
        return s.map.get(s.keys[ThreadLocalRandom.current().nextInt(COLLISIONS)]);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(1)
    public Integer collisionGet_jdk(JdkCollisionState s) {
        return s.map.get(s.keys[ThreadLocalRandom.current().nextInt(COLLISIONS)]);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(1)
    public Integer collisionPut_concurrent(OurCollisionState s) {
        int i = ThreadLocalRandom.current().nextInt(COLLISIONS);
        return s.map.put(s.keys[i], i);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(1)
    public Integer collisionPut_jdk(JdkCollisionState s) {
        int i = ThreadLocalRandom.current().nextInt(COLLISIONS);
        return s.map.put(s.keys[i], i);
    }

    static final int BULK_KEYS = 500_000;

    @State(Scope.Benchmark)
    public static class OurBulkState {
        ConcurrentHashMapImpl<Integer, Integer> map;
        @Setup(Level.Invocation)
        public void setup() { map = new ConcurrentHashMapImpl<>(16); }
    }

    @State(Scope.Benchmark)
    public static class JdkBulkState {
        ConcurrentHashMap<Integer, Integer> map;
        @Setup(Level.Invocation)
        public void setup() { map = new ConcurrentHashMap<>(16); }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(8)
    public void bulkLoad8_concurrent(OurBulkState s) {
        for (int i = 0; i < BULK_KEYS; i++) s.map.put(i, i);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(8)
    public void bulkLoad8_jdk(JdkBulkState s) {
        for (int i = 0; i < BULK_KEYS; i++) s.map.put(i, i);
    }
}
