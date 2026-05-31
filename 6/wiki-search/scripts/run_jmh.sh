#!/usr/bin/env bash
#
# Run the JMH micro-benchmark of the integer codecs (warmup + 5 measured iterations).
# Results are written to results/jmh_codec.json.
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

[ -f target/wiki-search.jar ] || mvn -q package -DskipTests
mkdir -p results

"$JAVA" -cp target/wiki-search.jar org.openjdk.jmh.Main \
  "ru.itmo.search.benchmarks.CodecBenchmark" \
  -rf json -rff results/jmh_codec.json "$@"

echo ">> JMH results in results/jmh_codec.json"
