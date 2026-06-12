package ru.itmo.stress;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import ru.itmo.map.ConcurrentHashMapImpl;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {

    private static final int THREADS = 8;

    @RepeatedTest(5)
    void noLostUpdates() throws InterruptedException {
        ConcurrentHashMapImpl<Integer, Integer> map = new ConcurrentHashMapImpl<>();
        int perThread = 1000;
        runConcurrently(THREADS, threadId -> {
            int base = threadId * perThread;
            for (int i = 0; i < perThread; i++) map.put(base + i, threadId);
        });

        int expected = THREADS * perThread;
        assertEquals(expected, map.size());
        for (int i = 0; i < expected; i++) assertNotNull(map.get(i));
    }

    @RepeatedTest(20)
    void writeVisibility() throws InterruptedException {
        ConcurrentHashMapImpl<String, String> map = new ConcurrentHashMapImpl<>();
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch done    = new CountDownLatch(1);

        new Thread(() -> { map.put("x", "hello"); written.countDown(); }).start();
        new Thread(() -> {
            try {
                written.await();
                assertEquals("hello", map.get("x"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        }).start();

        assertTrue(done.await(5, TimeUnit.SECONDS));
    }

    @RepeatedTest(5)
    void sizeConsistency() throws InterruptedException {
        ConcurrentHashMapImpl<Integer, Integer> map = new ConcurrentHashMapImpl<>();
        int perThread = 2000;
        runConcurrently(THREADS, threadId -> {
            int base = threadId * perThread;
            for (int i = 0; i < perThread; i++) map.put(base + i, i);
        });
        assertEquals(THREADS * perThread, map.size());
    }

    @RepeatedTest(5)
    void mergeAtomicCounter() throws InterruptedException {
        ConcurrentHashMapImpl<String, Integer> map = new ConcurrentHashMapImpl<>();
        int count = 500;
        runConcurrently(THREADS, threadId -> {
            for (int i = 0; i < count; i++) map.merge("counter", 1, Integer::sum);
        });
        assertEquals(THREADS * count, map.get("counter"));
    }

    @RepeatedTest(3)
    void iteratorSafety() throws InterruptedException {
        ConcurrentHashMapImpl<Integer, Integer> map = new ConcurrentHashMapImpl<>();
        for (int i = 0; i < 500; i++) map.put(i, i);

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger(0);

        List<Thread> mutators = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            int base = 500 + t * 500;
            mutators.add(new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 500; i++) map.put(base + i, i);
                } catch (Exception e) { errors.incrementAndGet(); }
            }));
        }

        Thread iter = new Thread(() -> {
            try {
                start.await();
                for (int rep = 0; rep < 20; rep++) {
                    Iterator<Map.Entry<Integer, Integer>> it = map.entrySet().iterator();
                    while (it.hasNext()) {
                        it.next();
                    }
                }
            } catch (Exception e) { errors.incrementAndGet(); }
        });

        mutators.forEach(Thread::start);
        iter.start();
        start.countDown();
        for (Thread t : mutators) t.join(5000);
        iter.join(5000);

        assertEquals(0, errors.get());
    }

    @RepeatedTest(3)
    void clearSafety() throws InterruptedException {
        ConcurrentHashMapImpl<Integer, Integer> map = new ConcurrentHashMapImpl<>();
        AtomicInteger errors = new AtomicInteger(0);

        List<Thread> inserters = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            inserters.add(new Thread(() -> {
                for (int i = 0; i < 2000; i++) {
                    try { map.put(i, i); } catch (Exception e) { errors.incrementAndGet(); }
                }
            }));
        }
        Thread clearer = new Thread(() -> {
            for (int i = 0; i < 20; i++) {
                try { Thread.sleep(1); map.clear(); } catch (Exception e) { errors.incrementAndGet(); }
            }
        });

        inserters.forEach(Thread::start);
        clearer.start();
        for (Thread t : inserters) t.join(5000);
        clearer.join(5000);

        map.put(999, 999);
        assertEquals(999, map.get(999));
        assertEquals(0, errors.get());
    }

    @Test
    void resizeSafety() throws InterruptedException {
        ConcurrentHashMapImpl<Integer, Integer> map = new ConcurrentHashMapImpl<>(4);
        int total = 50_000;
        int perThread = total / THREADS;

        runConcurrently(THREADS, threadId -> {
            int base = threadId * perThread;
            for (int i = 0; i < perThread; i++) map.put(base + i, i);
        });

        assertEquals(total, map.size());
        for (int i = 0; i < total; i++) assertNotNull(map.get(i));
    }

    @FunctionalInterface
    interface ThreadTask {
        void run(int threadId) throws Exception;
    }

    private void runConcurrently(int threads, ThreadTask task) throws InterruptedException {
        CountDownLatch start  = new CountDownLatch(1);
        CountDownLatch done   = new CountDownLatch(threads);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int t = 0; t < threads; t++) {
            int id = t;
            new Thread(() -> {
                try { start.await(); task.run(id); }
                catch (Throwable e) { errors.add(e); }
                finally { done.countDown(); }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS));
        if (!errors.isEmpty()) {
            AssertionError ae = new AssertionError("исключение в рабочем потоке");
            errors.forEach(ae::addSuppressed);
            throw ae;
        }
    }
}
