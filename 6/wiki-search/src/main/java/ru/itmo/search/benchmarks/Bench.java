package ru.itmo.search.benchmarks;

import java.util.function.LongSupplier;

public final class Bench {

    private static final double TARGET_ROUND_MS = 250.0;
    private static final int MAX_REPETITIONS = 20_000;

    public record Timing(double meanMs, double stdMs, double ci95Ms,
                         double qps, double qpsLow, double qpsHigh) {
    }

    private Bench() {
    }

    public static Timing measure(int warmup, int rounds, int opsPerRound, LongSupplier round) {
        long sink = 0;
        for (int i = 0; i < warmup; i++) {
            sink += round.getAsLong();
        }
        int repetitions = calibrateRepetitions(round);
        for (int i = 0; i < warmup; i++) {
            sink += runRepeated(repetitions, round);
        }
        System.gc();
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        double[] ms = new double[rounds];
        for (int i = 0; i < rounds; i++) {
            long t0 = System.nanoTime();
            sink += runRepeated(repetitions, round);
            ms[i] = ((System.nanoTime() - t0) / 1e6) / repetitions;
        }
        if (sink == Long.MIN_VALUE) {
            System.out.print("");
        }
        double mean = 0;
        for (double m : ms) {
            mean += m;
        }
        mean /= rounds;
        double var = 0;
        for (double m : ms) {
            var += (m - mean) * (m - mean);
        }
        double std = rounds > 1 ? Math.sqrt(var / (rounds - 1)) : 0;
        double ci95 = rounds > 1 ? tCritical95(rounds - 1) * std / Math.sqrt(rounds) : 0;
        double qps = opsPerRound / (mean / 1000.0);
        double slowMs = mean + ci95;
        double fastMs = Math.max(1e-9, mean - ci95);
        double qpsLow = opsPerRound / (slowMs / 1000.0);
        double qpsHigh = opsPerRound / (fastMs / 1000.0);
        return new Timing(mean, std, ci95, qps, qpsLow, qpsHigh);
    }

    private static int calibrateRepetitions(LongSupplier round) {
        long t0 = System.nanoTime();
        long sink = round.getAsLong();
        double ms = (System.nanoTime() - t0) / 1e6;
        if (sink == Long.MIN_VALUE) {
            System.out.print("");
        }
        if (ms <= 0) {
            return MAX_REPETITIONS;
        }
        long repetitions = (long) Math.ceil(TARGET_ROUND_MS / ms);
        return (int) Math.max(1, Math.min(MAX_REPETITIONS, repetitions));
    }

    private static long runRepeated(int repetitions, LongSupplier round) {
        long sink = 0;
        for (int i = 0; i < repetitions; i++) {
            sink += round.getAsLong();
        }
        return sink;
    }

    private static double tCritical95(int degreesOfFreedom) {
        return switch (degreesOfFreedom) {
            case 1 -> 12.706;
            case 2 -> 4.303;
            case 3 -> 3.182;
            case 4 -> 2.776;
            case 5 -> 2.571;
            case 6 -> 2.447;
            case 7 -> 2.365;
            case 8 -> 2.306;
            case 9 -> 2.262;
            case 10 -> 2.228;
            default -> 1.96;
        };
    }
}
