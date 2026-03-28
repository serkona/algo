package algo.benchmarks;

import algo.LSHIndex;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class LSHIndexBenchmark {

    @Param({"claims", "corpus"})
    String dataset;

    private LSHIndex trainIndex;
    private Path tmpDir;
    private List<String> trainClaims;
    private List<String> testClaims;

    private static final int INDEX_POOL = 100_000;
    private int[] randomIndices;
    private int cursor;
    private List<String> shuffledTrain;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        String datasetDir = System.getProperty("dataset.dir", ".");
        Path base = Path.of(datasetDir);

        if ("corpus".equals(dataset)) {
            List<String>[] kv = CsvUtils.readTwoColumns(
                    base.resolve("archive/corpus_train.csv"), "title", "abstract");
            trainClaims = new ArrayList<>(kv[0].size());
            for (int i = 0; i < kv[0].size(); i++) {
                trainClaims.add(kv[0].get(i) + " " + kv[1].get(i));
            }
            testClaims = trainClaims;
        } else {
            trainClaims = CsvUtils.readColumn(base.resolve("archive/claims_train.csv"), "claim");
            testClaims = CsvUtils.readColumn(base.resolve("archive/claims_test.csv"), "claim");
        }

        tmpDir = Files.createTempDirectory("lsh_jmh");
        trainIndex = new LSHIndex(tmpDir.resolve("train"), 3, 20, 5, 0.5);
        for (String claim : trainClaims) {
            trainIndex.add(claim);
        }

        Random rng = new Random(42);
        randomIndices = new int[INDEX_POOL];
        for (int i = 0; i < INDEX_POOL; i++) {
            randomIndices[i] = rng.nextInt(testClaims.size());
        }
        cursor = 0;

        shuffledTrain = new ArrayList<>(trainClaims);
        Collections.shuffle(shuffledTrain, rng);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        if (Files.exists(tmpDir)) {
            try (var stream = Files.walk(tmpDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    @Benchmark
    public void addDocument(Blackhole bh) throws IOException {
        bh.consume(trainIndex.add(testClaims.get(randomIndices[cursor++ % INDEX_POOL])));
    }

    @Benchmark
    public void queryCandidates(Blackhole bh) {
        bh.consume(trainIndex.queryCandidates(testClaims.get(randomIndices[cursor++ % INDEX_POOL])));
    }

    @Benchmark
    public void querySimilar(Blackhole bh) {
        bh.consume(trainIndex.querySimilar(testClaims.get(randomIndices[cursor++ % INDEX_POOL])));
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void findAllDuplicates(Blackhole bh) {
        bh.consume(trainIndex.findAllDuplicates());
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void findAllDuplicatesBruteForce(Blackhole bh) {
        bh.consume(trainIndex.findAllDuplicatesBruteForce(trainClaims));
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void buildIndex(Blackhole bh) throws IOException {
        Path dir = tmpDir.resolve("build_" + (cursor++));
        LSHIndex fresh = new LSHIndex(dir, 3, 20, 5, 0.5);
        for (String claim : shuffledTrain) {
            fresh.add(claim);
        }
        bh.consume(fresh.size());
    }
}