#!/usr/bin/env python3
"""Prepare a Wikipedia (English) subset as JSONL for the search engine.

Streams the Hugging Face `wikimedia/wikipedia` dataset (no full download needed) and writes
one {"title","text"} object per line until the target raw size is reached (<= 3 GB by the
assignment). Each indexed document is an article.

Requires:  pip install datasets
Usage:     python3 prepare_wikipedia.py --out data/wikipedia.jsonl --max-gb 3 --config 20231101.en
"""
import argparse
import json
import os


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="data/wikipedia.jsonl")
    ap.add_argument("--config", default="20231101.en", help="HF wikipedia dump/config")
    ap.add_argument("--max-gb", type=float, default=3.0)
    ap.add_argument("--max-docs", type=int, default=0, help="0 = unlimited (size-bounded)")
    args = ap.parse_args()

    try:
        from datasets import load_dataset
    except ImportError:
        raise SystemExit("Please install the datasets library:  pip install datasets")

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    budget = int(args.max_gb * (1 << 30))
    written = 0
    docs = 0
    ds = load_dataset("wikimedia/wikipedia", args.config, split="train", streaming=True)
    with open(args.out, "w", encoding="utf-8") as f:
        for row in ds:
            text = row.get("text") or ""
            title = row.get("title") or f"doc-{docs}"
            if not text:
                continue
            line = json.dumps({"title": title, "text": text}, ensure_ascii=False)
            f.write(line)
            f.write("\n")
            written += len(line.encode("utf-8")) + 1
            docs += 1
            if docs % 10000 == 0:
                print(f"  {docs} docs, {written/1e9:.2f} GB")
            if written >= budget:
                break
            if args.max_docs and docs >= args.max_docs:
                break
    print(f"wrote {docs} docs, {written/1e9:.2f} GB to {args.out}")


if __name__ == "__main__":
    main()
