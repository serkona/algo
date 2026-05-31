#!/usr/bin/env bash
#
# Test stand launcher. Builds (once) a compressed on-disk index and opens the interactive
# search shell pre-configured for one of two operating points from the real-Wikipedia benchmarks:
#
#   balanced    vbyte/bitpack/vbyte postings (best measured size + high AND QPS), blockSize 128,
#               WAND F=1.0 (lossless top-K)
#   max-recall  same index profile, exhaustive scoring
#
# On the real corpus WAND's recall/QPS curve is steep (see charts/recall_pareto.png): F=1.0 keeps
# recall=1; raise :wandf in the shell to trade recall for throughput.
#
# Usage:
#   ./run.sh [balanced|max-recall]
# Env overrides:
#   CORPUS=/path/to/wiki.jsonl   index a real corpus (default: data/wikipedia.jsonl if present)
#   MAXDOCS=500000               cap indexed documents
#   IDX=dir                      index location (default target/stand-index-<profile>-vbyte-bitpack)
set -euo pipefail
cd "$(dirname "$0")"

PROFILE="${1:-balanced}"
JAR=target/wiki-search.jar
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

[ -f "$JAR" ] || mvn -q package -DskipTests

case "$PROFILE" in
  balanced)
    CODEC=(--docIdCodec vbyte --freqCodec bitpack --posCodec vbyte --blockSize 128)
    MODE=wand; WANDF=1.0 ;;
  max-recall)
    CODEC=(--docIdCodec vbyte --freqCodec bitpack --posCodec vbyte --blockSize 128)
    MODE=exhaustive; WANDF=1.0 ;;
  *) echo "usage: $0 [balanced|max-recall]"; exit 1 ;;
esac

CORPUS="${CORPUS:-data/wikipedia.jsonl}"
IDX="${IDX:-target/stand-index-$PROFILE-vbyte-bitpack}"
if [ ! -d "$IDX" ]; then
  if [ ! -f "$CORPUS" ]; then
    echo "Corpus file not found: $CORPUS"
    echo "Download it first with scripts/download_wikipedia.sh 3 20231101.en"
    exit 1
  fi
  SRC=(--corpus "$CORPUS" --maxDocs "${MAXDOCS:-500000}")
  echo ">> building index ($PROFILE) at $IDX"
  "$JAVA" -Xmx30g -jar "$JAR" build --out "$IDX" "${SRC[@]}" "${CODEC[@]}"
fi

echo ">> launching test stand: profile=$PROFILE mode=$MODE wandF=$WANDF"
exec "$JAVA" -jar "$JAR" shell --index "$IDX" --mode "$MODE" --wandf "$WANDF" --k 10
