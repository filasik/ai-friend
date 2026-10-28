#!/usr/bin/env bash
# Smoke test: nastartuje čistý Paper s pluginem, ověří že se načetl bez chyb a server vypne.
# Vyžaduje plugin xyz.jpenilla.run-paper (runServer). V CI nastavena proměnná CI=true (potvrzení EULA).
set -uo pipefail
cd "$(dirname "$0")/.."
NAME="$(grep -E '^pluginName=' gradle.properties | cut -d= -f2-)"
LOG="run/logs/latest.log"
rm -rf run/logs

echo ">> build"
./gradlew build -x test || { echo "FAIL: build"; exit 1; }

echo ">> start Paper (max 5 min)"
# `stop` must wait until the server is up ("Done ("), otherwise Paper fails on it with a NullPointerException.
# `timeout` does not exist on macOS, perl is the fallback.
limit() { if command -v timeout >/dev/null; then timeout "$@"; else local t=$1; shift; perl -e 'alarm shift; exec @ARGV' "$t" "$@"; fi; }
( for _ in $(seq 1 140); do sleep 2; grep -qs "Done (" "$LOG" && break; done; sleep 3; echo stop ) \
  | limit 300 ./gradlew runServer --console=plain > smoke-output.txt 2>&1
CODE=$?

[ -f "$LOG" ] || { echo "FAIL: chybí $LOG (server nenastartoval)"; tail -30 smoke-output.txt; exit 1; }

FAILED=0
grep -q "Enabling $NAME" "$LOG" || { echo "FAIL: plugin '$NAME' nebyl povolen"; FAILED=1; }
if grep -E "Could not load|Error occurred while enabling|Exception" "$LOG" | grep -qv "^$"; then
  echo "FAIL: v logu jsou chyby:"; grep -E "Could not load|Error occurred while enabling|Exception" "$LOG" | head -20; FAILED=1
fi
grep -q "Done (" "$LOG" || { echo "FAIL: server nedoběhl do 'Done'"; FAILED=1; }
[ $CODE -eq 124 ] || [ $CODE -eq 142 ] && { echo "FAIL: timeout (server se nevypnul)"; FAILED=1; }

[ $FAILED -eq 0 ] && echo "OK: smoke test prošel" || { tail -40 "$LOG"; exit 1; }
