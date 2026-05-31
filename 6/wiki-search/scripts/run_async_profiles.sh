#!/usr/bin/env bash
#
# Run balanced build/query profile scenarios under async-profiler.
# Balanced means the report/demo profile: pfor/bitpack/pfor, blockSize=256.
#
# Outputs:
#   profiles/build-balanced-cpu.html
#   profiles/build-balanced-alloc.html
#   profiles/query-balanced-and-cpu.html
#   profiles/query-balanced-and-alloc.html
#   ... one pair for OR/ADJ/NEAR/BM25
#
# Usage:
#   scripts/run_async_profiles.sh
#   CORPUS=data/wikipedia.jsonl MAXDOCS=500000 PROFILE_SECONDS=120 scripts/run_async_profiles.sh
#   PROFILE_MODE=cpu OPS="and near bm25" scripts/run_async_profiles.sh
#
# Notes:
#   * CPU and allocation profiles are collected in separate JVM runs. This keeps the
#     profiles interpretable and avoids allocation instrumentation biasing the CPU profile.
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
ASPROF="${ASPROF:-$(command -v asprof || true)}"
PYTHON="${PYTHON:-python3}"

CORPUS="${CORPUS:-data/wikipedia.jsonl}"
MAXDOCS="${MAXDOCS:-500000}"
QUERIES="${QUERIES:-160}"
HEAP="${HEAP:-30g}"
PROFILE_DIR="${PROFILE_DIR:-profiles}"
PROFILE_SECONDS="${PROFILE_SECONDS:-30}"
PROFILE_ATTACH_DELAY="${PROFILE_ATTACH_DELAY:-5}"
PROFILE_MODE="${PROFILE_MODE:-both}"   # both | cpu | alloc
PROFILE_TARGET="${PROFILE_TARGET:-all}" # all | build | query
OPS="${OPS:-and or adj near bm25}"
INDEX_DIR="${INDEX_DIR:-target/profile-balanced-v2-pfor-bitpack-bs256-index}"
CPU_INTERVAL="${CPU_INTERVAL:-1ms}"
ALLOC_INTERVAL="${ALLOC_INTERVAL:-512k}"
BALANCED_ARGS=(--docIdCodec pfor --freqCodec bitpack --posCodec pfor --blockSize 256)

if [[ -z "$ASPROF" ]]; then
  echo "async-profiler CLI 'asprof' was not found."
  echo "Install it (for example: brew install async-profiler) or set ASPROF=/path/to/asprof."
  exit 1
fi

echo ">> packaging"
mvn -q package -DskipTests

mkdir -p "$PROFILE_DIR"

if [[ ! -f "$CORPUS" ]]; then
  echo "Corpus file not found: $CORPUS"
  echo "Download it first with scripts/download_wikipedia.sh 3 20231101.en"
  exit 1
fi

run_profile() {
  local name="$1"
  local event="$2"
  local output="$3"
  local log="$4"
  shift 4
  local cmd_args=("$@")
  local profiler_args=()
  if [[ "$event" == "cpu" ]]; then
    profiler_args=(-i "$CPU_INTERVAL")
  else
    profiler_args=(--alloc "$ALLOC_INTERVAL")
  fi

  echo ">> starting $name"
  "$JAVA" -Xmx"$HEAP" -cp target/wiki-search.jar ru.itmo.search.benchmarks.ProfileHarness \
    "${cmd_args[@]}" >"$log" 2>&1 &
  local pid=$!

  sleep "$PROFILE_ATTACH_DELAY"
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "profile JVM exited before profiler could attach; see $log"
    wait "$pid"
    return
  fi

  echo ">> collecting $event profile for ${PROFILE_SECONDS}s -> $output"
  set +e
  "$ASPROF" collect -e "$event" "${profiler_args[@]}" -d "$PROFILE_SECONDS" -o flamegraph -f "$output" "$pid"
  local prof_status=$?
  set -e
  if [[ "$prof_status" -ne 0 ]]; then
    echo "async-profiler returned $prof_status for $name; process continues, see $log"
  fi

  echo ">> waiting for profile JVM ($name) to finish"
  wait "$pid"
}

profile_modes=()
case "$PROFILE_MODE" in
  both) profile_modes=(cpu alloc) ;;
  cpu) profile_modes=(cpu) ;;
  alloc|memory|mem) profile_modes=(alloc) ;;
  *) echo "Unknown PROFILE_MODE=$PROFILE_MODE; expected both, cpu or alloc"; exit 1 ;;
esac

run_named_profile() {
  local label="$1"
  shift
  for mode in "${profile_modes[@]}"; do
    if [[ "$mode" == "cpu" ]]; then
      run_profile "$label-cpu" cpu "$PROFILE_DIR/$label-cpu.html" "$PROFILE_DIR/$label-cpu.log" \
        "$@"
    else
      run_profile "$label-alloc" alloc "$PROFILE_DIR/$label-alloc.html" "$PROFILE_DIR/$label-alloc.log" \
        "$@"
    fi
  done
}

if [[ "$PROFILE_TARGET" == "all" || "$PROFILE_TARGET" == "build" ]]; then
  BUILD_PROFILE_INDEX="${BUILD_PROFILE_INDEX:-$INDEX_DIR-build-profile-$(date +%Y%m%d%H%M%S)}"
  run_named_profile build-balanced build --corpus "$CORPUS" --maxDocs "$MAXDOCS" \
    --out "$BUILD_PROFILE_INDEX" "${BALANCED_ARGS[@]}"
fi

if [[ "$PROFILE_TARGET" == "all" || "$PROFILE_TARGET" == "query" ]]; then
  if [[ ! -d "$INDEX_DIR" ]]; then
    echo ">> building reusable balanced query index at $INDEX_DIR"
    "$JAVA" -Xmx"$HEAP" -cp target/wiki-search.jar ru.itmo.search.benchmarks.ProfileHarness \
      build --corpus "$CORPUS" --maxDocs "$MAXDOCS" --out "$INDEX_DIR" "${BALANCED_ARGS[@]}"
  fi
  for op in $OPS; do
    run_named_profile "query-balanced-$op" query --index "$INDEX_DIR" --op "$op" \
      --queries "$QUERIES" --seconds "$PROFILE_SECONDS"
  done
fi

for html in "$PROFILE_DIR"/*.html; do
  [[ -f "$html" ]] || continue
  "$PYTHON" scripts/render_profile_images.py "$html" "${html%.html}.png"
done

echo ">> async-profiler output in $PROFILE_DIR/"
