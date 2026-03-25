package algo.benchmarks;

import algo.FileSystemHashTable;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class FileSystemHashTableBenchmark {

    @Param({"1024", "4096", "16384"})
    int pageSize;

    private FileSystemHashTable table;
    private Path dir;
    private Random rng;
    private int counter;

    private List<String> keys;
    private List<String> values;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        String datasetDir = System.getProperty("dataset.dir", ".");
        Path csv = Path.of(datasetDir).resolve("archive/corpus_train.csv");
        List<String>[] kv = CsvUtils.readTwoColumns(csv, "title", "abstract");
        keys = kv[0];
        values = kv[1];

        dir = Files.createTempDirectory("fsht_bench");
        table = new FileSystemHashTable(dir, pageSize);
        rng = new Random(42);
        counter = 0;

        int preload = Math.min(1000, keys.size());
        for (int i = 0; i < preload; i++) {
            table.put(keys.get(i), values.get(i));
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        table.destroy();
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    @Benchmark
    public void put() throws IOException {
        int idx = (counter++) % keys.size();
        table.put(keys.get(idx), values.get(idx));
    }

    @Benchmark
    public void getExisting(Blackhole bh) throws IOException {
        bh.consume(table.get(keys.get(rng.nextInt(Math.min(1000, keys.size())))));
    }

    @Benchmark
    public void getSameEntry(Blackhole bh) throws IOException {
        bh.consume(table.get(keys.get(0)));
    }

    @Benchmark
    public void getMissing(Blackhole bh) throws IOException {
        bh.consume(table.get("nonexistent_doc_" + rng.nextInt(100_000)));
    }

    @Benchmark
    public void updateExisting() throws IOException {
        int idx = rng.nextInt(Math.min(1000, keys.size()));
        table.put(keys.get(idx), "updated_" + counter++);
    }

    @Benchmark
    public void deleteAndReinsert() throws IOException {
        int idx = rng.nextInt(Math.min(1000, keys.size()));
        table.delete(keys.get(idx));
        table.put(keys.get(idx), values.get(idx));
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void putAllDataset(Blackhole bh) throws IOException {
        Path d = Files.createTempDirectory("fsht_batch");
        FileSystemHashTable t = new FileSystemHashTable(d, pageSize);
        for (int i = 0; i < keys.size(); i++) {
            t.put(keys.get(i), values.get(i));
        }
        bh.consume(t);
        t.destroy();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void getAllDataset(Blackhole bh) throws IOException {
        for (int i = 0; i < Math.min(1000, keys.size()); i++) {
            bh.consume(table.get(keys.get(i)));
        }
    }
}
