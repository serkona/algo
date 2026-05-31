#!/usr/bin/env bash
#
# Build the jar, run the full benchmark sweep on the real Wikipedia corpus, render charts.
# Produces: results/*.csv and charts/*.png
#
# Usage: scripts/run_benchmarks.sh [extra harness args]
#   CORPUS=data/wikipedia.jsonl  MAXDOCS=500000  scripts/run_benchmarks.sh
#
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
PYTHON="${PYTHON:-python3}"

CORPUS="${CORPUS:-data/wikipedia.jsonl}"
MAXDOCS="${MAXDOCS:-500000}"
QUERIES="${QUERIES:-160}"
HEAP="${HEAP:-30g}"

echo ">> packaging"
mvn -q package -DskipTests

mkdir -p results charts

if [[ ! -f "$CORPUS" ]]; then
  echo "Corpus file not found: $CORPUS"
  echo "Download it first with scripts/download_wikipedia.sh 3 20231101.en"
  exit 1
fi

echo ">> running benchmark harness on real corpus $CORPUS (maxDocs=$MAXDOCS)"
CORPUS_ARGS=(--corpus "$CORPUS" --maxDocs "$MAXDOCS")

"$JAVA" -Xmx"$HEAP" -cp target/wiki-search.jar ru.itmo.search.benchmarks.BenchmarkHarness \
  "${CORPUS_ARGS[@]}" --queries "$QUERIES" \
  --out results --work target/bench-index "$@"

echo ">> rendering charts (needs matplotlib/pandas)"
"$PYTHON" scripts/plot.py results charts

echo ">> done. CSVs in results/, charts in charts/"
