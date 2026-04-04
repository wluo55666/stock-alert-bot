#!/bin/bash

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"

if [ "${APP_TEST_ENDPOINTS_ENABLED:-false}" != "true" ]; then
  echo "APP_TEST_ENDPOINTS_ENABLED is not true. Refusing to hit /api/test endpoints."
  echo "Set APP_TEST_ENDPOINTS_ENABLED=true in your local/dev environment first."
  exit 1
fi

echo "Triggering expanded E2E test suite..."

post_inject() {
  local symbol="$1"
  local open="$2"
  local high="$3"
  local low="$4"
  local close="$5"
  local volume="$6"
  local timestamp_ms="$7"

  curl -s -X POST "$BASE_URL/api/test/inject" \
    -H "Content-Type: application/json" \
    -d "{\"symbol\": \"$symbol\", \"open\": $open, \"high\": $high, \"low\": $low, \"close\": $close, \"volume\": $volume, \"timestamp\": $timestamp_ms}" > /dev/null
}

# 1. Sliding-window ALERT path
echo "[1/5] Sliding-window ALERT path"
NOW_MS=$(date +%s000)
post_inject "TEST_CO" 100.0 100.0 100.0 100.0 1000 "$NOW_MS"
sleep 1
post_inject "TEST_CO" 100.0 150.0 100.0 150.0 5000 "$(date +%s000)"
echo "  -> expected: sliding-window ALERT"

# 2. Sliding-window NO_TRIGGER / SUPPRESS path
echo "[2/5] Sliding-window low-score path"
BASE_MS=$(($(date +%s) * 1000))
post_inject "TEST_WEAK" 100.0 100.0 100.0 100.0 1000 "$BASE_MS"
post_inject "TEST_WEAK" 100.0 101.0 100.0 101.0 1000 "$((BASE_MS + 60000))"
echo "  -> expected: below-threshold or suppressed sliding-window decision"

# 3. TA path decision coverage (may not guarantee ALERT, but should generate TA decisions)
echo "[3/5] TA decision coverage"
BASE_TIME=$(($(date +%s) - 3000))
for i in {1..40}; do
  TIMESTAMP=$((BASE_TIME + i * 60))000
  if [ $i -lt 20 ]; then
    PRICE=$((120 - i))
  elif [ $i -lt 35 ]; then
    PRICE=$((100 + i))
  else
    PRICE=$((145 - (i - 35) * 8))
  fi
  post_inject "TEST_TA4J" "$PRICE.0" "$PRICE.0" "$PRICE.0" "$PRICE.0" 1000 "$TIMESTAMP"
done
echo "  -> expected: TA decision logs (NO_TRIGGER / SUPPRESS / maybe ALERT depending on indicator state)"

# 4. Direct synthetic alert path
echo "[4/5] Direct synthetic alert endpoint"
curl -s -X POST "$BASE_URL/api/test/alert" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "FAKE_E2E",
    "signal": "BULLISH REVERSAL 📈",
    "price": 185.0,
    "rsi": 35.0,
    "confirmationBars": 2,
    "score": 4,
    "technicalExplanation": "Artificial E2E Injection: Fake MACD crossover context here."
  }' > /dev/null
echo "  -> expected: Telegram send success"

# 5. News-enriched direct synthetic alert path (documents desired strong-alert behavior)
echo "[5/5] Strong synthetic alert path (score=4)"
curl -s -X POST "$BASE_URL/api/test/alert" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "FAKE_E2E_STRONG",
    "signal": "BULLISH REVERSAL 📈",
    "price": 220.0,
    "rsi": 28.0,
    "confirmationBars": 3,
    "score": 4,
    "technicalExplanation": "Synthetic strong-alert path for E2E validation."
  }' > /dev/null
echo "  -> expected: strong synthetic alert send success"

echo
echo "Expanded E2E test completed."
echo "Now check logs for:"
echo "  - Sliding-window decision ... action=ALERT"
echo "  - Sliding-window decision ... action=NO_TRIGGER/SUPPRESS"
echo "  - TA decision ..."
echo "  - Alert sent to Telegram successfully"
