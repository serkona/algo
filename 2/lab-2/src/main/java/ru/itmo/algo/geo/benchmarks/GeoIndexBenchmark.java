package ru.itmo.algo.geo.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ru.itmo.algo.geo.GeoObject;
import ru.itmo.algo.geo.index.BruteForceIndex;
import ru.itmo.algo.geo.index.kd.KDTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(3)
public class GeoIndexBenchmark {

    @Param({"1000", "10000", "50000"})
    int datasetSize;

    private KDTree kdTree;
    private BruteForceIndex bruteForce;
    private List<GeoObject> dataset;
    private double[] queryLats;
    private double[] queryLngs;
    private Random rng;

    private static final int QUERY_POOL = 1000;
    private static final double RADIUS_KM = 100.0;

    @Setup(Level.Trial)
    public void setup() {
        Random gen = new Random(42);
        dataset = new ArrayList<>(datasetSize);
        for (int i = 0; i < datasetSize; i++) {
            double lat = gen.nextDouble() * 180 - 90;
            double lng = gen.nextDouble() * 360 - 180;
            dataset.add(new GeoObject(lat, lng, "p" + i));
        }

        kdTree = new KDTree();
        bruteForce = new BruteForceIndex();
        for (GeoObject obj : dataset) {
            kdTree.insert(obj);
            bruteForce.insert(obj);
        }

        queryLats = new double[QUERY_POOL];
        queryLngs = new double[QUERY_POOL];
        for (int i = 0; i < QUERY_POOL; i++) {
            queryLats[i] = gen.nextDouble() * 180 - 90;
            queryLngs[i] = gen.nextDouble() * 360 - 180;
        }

        rng = new Random(42);
    }

    @Benchmark
    public void findNearest_KDTree(Blackhole bh) {
        int idx = rng.nextInt(QUERY_POOL);
        bh.consume(kdTree.findNearest(queryLats[idx], queryLngs[idx]));
    }

    @Benchmark
    public void findNearest_BruteForce(Blackhole bh) {
        int idx = rng.nextInt(QUERY_POOL);
        bh.consume(bruteForce.findNearest(queryLats[idx], queryLngs[idx]));
    }

    @Benchmark
    public void findInRadius_KDTree(Blackhole bh) {
        int idx = rng.nextInt(QUERY_POOL);
        bh.consume(kdTree.findInRadius(queryLats[idx], queryLngs[idx], RADIUS_KM));
    }

    @Benchmark
    public void findInRadius_BruteForce(Blackhole bh) {
        int idx = rng.nextInt(QUERY_POOL);
        bh.consume(bruteForce.findInRadius(queryLats[idx], queryLngs[idx], RADIUS_KM));
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void buildIndex_KDTree(Blackhole bh) {
        KDTree tree = new KDTree();
        for (GeoObject obj : dataset) {
            tree.insert(obj);
        }
        bh.consume(tree.size());
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void buildIndex_BruteForce(Blackhole bh) {
        BruteForceIndex bf = new BruteForceIndex();
        for (GeoObject obj : dataset) {
            bf.insert(obj);
        }
        bh.consume(bf.size());
    }
}
