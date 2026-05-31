#!/usr/bin/env bash
#
# Fetch a <=3 GB English Wikipedia subset into data/wikipedia.jsonl using the Hugging Face
# streaming API (so the full multi-hundred-GB dump is never downloaded at once).
#
# Usage: scripts/download_wikipedia.sh [max_gb] [hf_config]
set -euo pipefail
cd "$(dirname "$0")/.."

MAX_GB="${1:-3}"
CONFIG="${2:-20231101.en}"
PYTHON="${PYTHON:-python3}"

if ! "$PYTHON" -c "import datasets" 2>/dev/null; then
  echo "Installing the 'datasets' library..."
  "$PYTHON" -m pip install --quiet datasets
fi

mkdir -p data
"$PYTHON" scripts/prepare_wikipedia.py --out data/wikipedia.jsonl --max-gb "$MAX_GB" --config "$CONFIG"
echo ">> Wikipedia subset ready at data/wikipedia.jsonl"
echo ">> Build an index with:  ./run.sh balanced   (set CORPUS=data/wikipedia.jsonl)"
