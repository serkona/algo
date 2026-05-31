#!/usr/bin/env python3
"""Generate the report charts from the benchmark CSVs.

Usage: python3 plot.py [results_dir] [charts_dir]
Reads compression.csv, blocksize.csv, backend.csv, recall.csv and writes PNGs.
"""
import sys
import os
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

RESULTS = sys.argv[1] if len(sys.argv) > 1 else "results"
CHARTS = sys.argv[2] if len(sys.argv) > 2 else "charts"
os.makedirs(CHARTS, exist_ok=True)


def save(fig, name):
    path = os.path.join(CHARTS, name)
    fig.tight_layout()
    fig.savefig(path, dpi=130)
    plt.close(fig)
    print("wrote", path)


def plot_compression():
    df = pd.read_csv(os.path.join(RESULTS, "compression.csv"))
    df = df[df["profile"] != "raw+raw+raw"].copy()
    df["MB"] = df["postings_bytes"] / 1e6
    df["total_MB"] = df["total_bytes"] / 1e6

    fig, ax = plt.subplots(figsize=(9, 5))
    bars = ax.bar(df["profile"], df["MB"], color="#4C72B0")
    ax.set_ylabel("postings size, MB")
    ax.set_title("On-disk postings size by codec profile (docId+freq+pos)")
    ax.tick_params(axis="x", rotation=20)
    for b, r in zip(bars, df["ratio_vs_raw"]):
        ax.text(b.get_x() + b.get_width() / 2, b.get_height(), f"{r:.2f}x",
                ha="center", va="bottom", fontsize=9)
    save(fig, "compression_size.png")

    fig, ax = plt.subplots(figsize=(9, 5))
    ax.bar(df["profile"], df["bits_per_posting"], color="#55A868")
    ax.set_ylabel("bits per posting (incl. positions)")
    ax.set_title("Compression density by codec profile")
    ax.tick_params(axis="x", rotation=20)
    save(fig, "compression_bits.png")

    # Size vs query speed trade-off (lower-left is best size, higher is faster).
    fig, ax = plt.subplots(figsize=(8, 6))
    if {"and_qps_low", "and_qps_high"}.issubset(df.columns):
        yerr = [df["and_qps"] - df["and_qps_low"], df["and_qps_high"] - df["and_qps"]]
        ax.errorbar(df["MB"], df["and_qps"], yerr=yerr, fmt="o", ms=8,
                    color="#C44E52", capsize=3)
    else:
        ax.scatter(df["MB"], df["and_qps"], s=90, color="#C44E52")
    for _, row in df.iterrows():
        ax.annotate(row["profile"], (row["MB"], row["and_qps"]),
                    textcoords="offset points", xytext=(6, 4), fontsize=8)
    ax.set_xlabel("postings size, MB")
    ax.set_ylabel("AND throughput, qps")
    ax.set_title("Codec trade-off: index size vs AND query throughput (error bars = 95% CI)")
    ax.grid(True, alpha=0.3)
    save(fig, "compression_tradeoff.png")


def plot_blocksize():
    df = pd.read_csv(os.path.join(RESULTS, "blocksize.csv")).sort_values("blockSize")
    fig, ax1 = plt.subplots(figsize=(8, 5))
    ax1.plot(df["blockSize"], df["postings_bytes"] / 1e6, "o-", color="#4C72B0", label="postings size")
    ax1.set_xlabel("block size (postings per block)")
    ax1.set_ylabel("postings size, MB", color="#4C72B0")
    ax1.set_xscale("log", base=2)
    ax2 = ax1.twinx()
    if "and_ci95_ms" in df.columns:
        ax2.errorbar(df["blockSize"], df["and_mean_ms"], yerr=df["and_ci95_ms"],
                     fmt="s--", color="#C44E52", capsize=3, label="AND latency")
    else:
        ax2.plot(df["blockSize"], df["and_mean_ms"], "s--", color="#C44E52", label="AND latency")
    ax2.set_ylabel("AND latency, ms (lower=faster)", color="#C44E52")
    ax1.set_title("Block size: compression vs skip-list query cost (error bars = 95% CI)")
    save(fig, "blocksize.png")


def plot_backend():
    df = pd.read_csv(os.path.join(RESULTS, "backend.csv"))
    backends = df["backend"].unique()
    import numpy as np

    def grouped_bars(ax, types, title):
        x = np.arange(len(types))
        width = 0.8 / max(1, len(backends))
        for i, be in enumerate(backends):
            sub = df[df["backend"] == be].set_index("query_type").reindex(types)
            yerr = sub["ci95_ms"] if "ci95_ms" in sub.columns else sub["std_ms"]
            ax.bar(x + (i - (len(backends) - 1) / 2) * width, sub["mean_ms"], width,
                   label=be, yerr=yerr, capsize=3)
        ax.set_xticks(x)
        ax.set_xticklabels(types)
        ax.set_title(title)
        ax.grid(axis="y", alpha=0.25)

    fig, axes = plt.subplots(1, 2, figsize=(12, 5))
    grouped_bars(axes[0], ["AND", "ADJ", "NEAR"], "Short boolean / positional operations")
    grouped_bars(axes[1], ["OR", "BM25"], "Wide union and ranked retrieval")
    axes[0].set_ylabel("mean latency per batch, ms")
    axes[1].legend(loc="upper right")
    fig.suptitle("Backend latency split by query cost class (error bars = 95% CI)")
    save(fig, "backend_latency.png")


def plot_recall():
    df = pd.read_csv(os.path.join(RESULTS, "recall.csv"))
    wand = df[df["wand_factor"] != "exhaustive"].copy()
    wand["wand_factor"] = wand["wand_factor"].astype(float)
    exh = df[df["wand_factor"] == "exhaustive"]

    # Pareto front: recall vs qps. Scatter all WAND points, then draw the upper-right
    # envelope (no other point beats it on BOTH recall and qps).
    pts = [(r.qps, r.recall_at10, f"F={r.wand_factor:.2f}") for r in wand.itertuples()]
    if not exh.empty:
        pts.append((float(exh["qps"].iloc[0]), 1.0, "exhaustive"))
    frontier = []
    best_recall = -1
    for qps, rec, lbl in sorted(pts, key=lambda p: -p[0]):  # high qps first
        if rec > best_recall:
            frontier.append((qps, rec, lbl))
            best_recall = rec
    frontier.sort(key=lambda p: p[0])

    fig, ax = plt.subplots(figsize=(8, 6))
    ax.scatter(wand["qps"], wand["recall_at10"], s=70, color="#4C72B0", label="WAND configs", zorder=3)
    for qps, rec, lbl in pts:
        ax.annotate(lbl, (qps, rec), textcoords="offset points", xytext=(5, -9), fontsize=7)
    if not exh.empty:
        ax.scatter(exh["qps"], [1.0], marker="*", s=240, color="#C44E52",
                   label="exhaustive (recall=1)", zorder=4)
    fx = [p[0] for p in frontier]
    fy = [p[1] for p in frontier]
    ax.plot(fx, fy, "-", color="#55A868", lw=2, label="Pareto frontier", zorder=2)
    ax.set_xlabel("throughput, qps")
    ax.set_ylabel("recall@10 vs exhaustive")
    ax.set_title("Recall / QPS Pareto front (WAND dynamic pruning)")
    ax.grid(True, alpha=0.3)
    ax.legend()
    save(fig, "recall_pareto.png")

    fig, ax1 = plt.subplots(figsize=(8, 5))
    ax1.plot(wand["wand_factor"], wand["recall_at10"], "o-", color="#4C72B0", label="recall@10")
    ax1.set_xlabel("WAND threshold factor F")
    ax1.set_ylabel("recall@10", color="#4C72B0")
    ax2 = ax1.twinx()
    if {"qps_low", "qps_high"}.issubset(wand.columns):
        yerr = [wand["qps"] - wand["qps_low"], wand["qps_high"] - wand["qps"]]
        ax2.errorbar(wand["wand_factor"], wand["qps"], yerr=yerr, fmt="s--",
                     color="#C44E52", capsize=3, label="qps")
    else:
        ax2.plot(wand["wand_factor"], wand["qps"], "s--", color="#C44E52", label="qps")
    ax2.set_ylabel("qps", color="#C44E52")
    ax1.set_title("WAND factor: recall vs throughput (error bars = 95% CI)")
    save(fig, "recall_vs_factor.png")


def plot_jmh():
    import json
    path = os.path.join(RESULTS, "jmh_codec.json")
    with open(path) as f:
        data = json.load(f)
    rows = {}
    for b in data:
        bench = b["benchmark"].split(".")[-1]   # encode / decode
        codec = b["params"]["codecName"]
        rows.setdefault(codec, {})[bench] = b["primaryMetric"]["score"]
    codecs = list(rows.keys())
    import numpy as np
    x = np.arange(len(codecs))
    width = 0.4
    fig, ax = plt.subplots(figsize=(10, 5))
    ax.bar(x - width / 2, [rows[c].get("encode", 0) for c in codecs], width, label="encode", color="#4C72B0")
    ax.bar(x + width / 2, [rows[c].get("decode", 0) for c in codecs], width, label="decode", color="#C44E52")
    ax.set_xticks(x)
    ax.set_xticklabels(codecs, rotation=20)
    ax.set_ylabel("ns / 128-int block (lower=faster)")
    ax.set_title("JMH codec throughput (encode/decode of one posting block)")
    ax.legend()
    save(fig, "codec_speed.png")


def main():
    funcs = [plot_compression, plot_blocksize, plot_backend, plot_recall, plot_jmh]
    for f in funcs:
        try:
            f()
        except FileNotFoundError as e:
            print("skip", f.__name__, "-", e)


if __name__ == "__main__":
    main()
