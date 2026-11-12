#!/usr/bin/env bash
# Refresh the bundled NSE index lists after a rebalance (NSE Indices reconstitutes them in March and September).
# Usage: scripts/update-indices.sh   (then run the backend tests and commit the changed CSVs)
set -euo pipefail
cd "$(dirname "$0")/../backend/src/main/resources/indices"
UA="Mozilla/5.0 (compatible; MarketPulse/1.0)"
for name in nifty100 niftymidcap100 niftysmallcap100 nifty500; do
  curl -fsS -A "$UA" "https://www.niftyindices.com/IndexConstituent/ind_${name}list.csv" -o "${name}.csv.new"
  head -1 "${name}.csv.new" | grep -q "Symbol" || { echo "unexpected format for ${name}"; exit 1; }
  mv "${name}.csv.new" "${name}.csv"
  echo "${name}: $(($(grep -c . "${name}.csv") - 1)) stocks"
done
