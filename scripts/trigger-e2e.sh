#!/bin/bash

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"

if [ "${APP_TEST_ENDPOINTS_ENABLED:-false}" != "true" ]; then
  echo "APP_TEST_ENDPOINTS_ENABLED is not true. Refusing to hit /api/test endpoints."
  echo "Set APP_TEST_ENDPOINTS_ENABLED=true in your local/dev environment first."
  exit 1
fi

echo "Triggering E2E test by injecting synthetic bars for sliding window and ta4j analysis..."

# 1. Inject a baseline bar for TEST_CO at $100
echo "Injecting baseline bar: TEST_CO at \$100.00"
curl -X POST "$BASE_URL/api/test/inject" \
  -H "Content-Type: application/json" \
  -d '{"symbol": "TEST_CO", "open": 100.0, "high": 100.0, "low": 100.0, "close": 100.0, "volume": 1000, "timestamp": '$(date +%s000)'}'

sleep 1

# 2. Inject a spiked bar for TEST_CO at $150
echo -e "\nInjecting spiked bar: TEST_CO at \$150.00 (50% spike for sliding window alert)"
curl -X POST "$BASE_URL/api/test/inject" \
  -H "Content-Type: application/json" \
  -d '{"symbol": "TEST_CO", "open": 100.0, "high": 150.0, "low": 100.0, "close": 150.0, "volume": 5000, "timestamp": '$(date +%s000)'}'

echo -e "\nSliding Window bars injected successfully."

# 3. Inject enough bars to build 35+ bars for MACD/RSI
echo -e "\nInjecting 40 historical minute bars to trigger a Ta4j MACD alert..."
BASE_TIME=$(($(date +%s) - 3000))
for i in {1..40}; do
  TIMESTAMP=$((BASE_TIME + i * 60))000
  if [ $i -lt 38 ]; then
    PRICE=$((100 + i * 2))
  else
    PRICE=$((100 - i * 2))
  fi
  curl -s -X POST "$BASE_URL/api/test/inject" \
    -H "Content-Type: application/json" \
    -d "{\"symbol\": \"TEST_TA4J\", \"open\": $PRICE.0, \"high\": $PRICE.0, \"low\": $PRICE.0, \"close\": $PRICE.0, \"volume\": 1000, \"timestamp\": $TIMESTAMP}" > /dev/null
done

echo -e "\nTa4j historical bars injected successfully."

# 4. Directly trigger an AI + Telegram alert
echo -e "\nDirectly triggering a synthetic E2E alert via /api/test/alert..."
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
  }'

echo -e "\n\nDirect alert triggered successfully. Check your application logs and Telegram for synthetic E2E alerts."
