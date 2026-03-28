package algo.benchmarks;

import algo.PerfectHashTable;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class PerfectHashTableBenchmark {

    @Param({"1000", "10000", "45000"})
    int size;

    private PerfectHashTable<String> pht;
    private String[] keys;

    private static final int INDEX_POOL = 100_000;
    private int[] randomIndices;
    private String[] missingKeys;
    private int cursor;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        String datasetDir = System.getProperty("dataset.dir", ".");
        Path csv = Path.of(datasetDir).resolve("archive/corpus_train.csv");
        List<String>[] kv = CsvUtils.readTwoColumns(csv, "title", "abstract");

        Set<String> seen = new LinkedHashSet<>();
        List<String> uniqueKeys = new ArrayList<>();
        List<String> uniqueVals = new ArrayList<>();
        for (int i = 0; i < kv[0].size() && uniqueKeys.size() < size; i++) {
            if (seen.add(kv[0].get(i))) {
                uniqueKeys.add(kv[0].get(i));
                uniqueVals.add(kv[1].get(i));
            }
        }

        keys = uniqueKeys.toArray(new String[0]);
        String[] values = uniqueVals.toArray(new String[0]);
        pht = new PerfectHashTable<>(keys, values);
        cursor = 0;

        Random rng = new Random(99);
        randomIndices = new int[INDEX_POOL];
        missingKeys = new String[INDEX_POOL];
        for (int i = 0; i < INDEX_POOL; i++) {
            randomIndices[i] = rng.nextInt(keys.length);
            missingKeys[i] = "nonexistent_doc_" + rng.nextLong();
        }
    }

    @Benchmark
    public void getExisting(Blackhole bh) {
        bh.consume(pht.get(keys[randomIndices[cursor++ % INDEX_POOL]]));
    }

    @Benchmark
    public void getMissing(Blackhole bh) {
        bh.consume(pht.get(missingKeys[cursor++ % INDEX_POOL]));
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(iterations = 5, batchSize = 1)
    @Measurement(iterations = 20, batchSize = 1)
    public void buildIndex(Blackhole bh) {
        String[] vals = new String[keys.length];
        for (int i = 0; i < keys.length; i++) vals[i] = "v" + i;
        bh.consume(new PerfectHashTable<>(keys, vals));
    }
}