#!/bin/bash

# Ensure script stops on first error
set -e

echo "Triggering E2E test by injecting synthetic bars for sliding window and ta4j analysis..."

# 1. Inject a baseline bar for TEST_CO at $100
echo "Injecting baseline bar: TEST_CO at \$100.00"
curl -X POST http://localhost:8080/api/test/inject \
  -H "Content-Type: application/json" \
  -d '{"symbol": "TEST_CO", "open": 100.0, "high": 100.0, "low": 100.0, "close": 100.0, "volume": 1000, "timestamp": '$(date +%s000)'}'

# Wait a second to ensure order
sleep 1

# 2. Inject a spiked bar for TEST_CO at $150 (50% spike) to trigger the Sliding Window alert
echo -e "\nInjecting spiked bar: TEST_CO at \$150.00 (50% spike for sliding window alert)"
curl -X POST http://localhost:8080/api/test/inject \
  -H "Content-Type: application/json" \
  -d '{"symbol": "TEST_CO", "open": 100.0, "high": 150.0, "low": 100.0, "close": 150.0, "volume": 5000, "timestamp": '$(date +%s000)'}'

echo -e "\nSliding Window Bars injected successfully."

# 3. Inject enough bars to build 35+ bars for MACD/RSI
# For simplicity in this E2E script, we simulate past bars by sending historical timestamps.
# MACD(12, 26, 9) needs at least 35 bars to fully calculate.
echo -e "\nInjecting 40 historical minute bars to trigger a Ta4j MACD alert..."
BASE_TIME=$(($(date +%s) - 3000)) # 50 minutes ago
for i in {1..40}; do
  TIMESTAMP=$((BASE_TIME + i * 60))000
  # Simulate a steady climb to create an uptrend, then a sudden dip to cross MACD down
  if [ $i -lt 38 ]; then
    PRICE=$((100 + i * 2))
  else
    PRICE=$((100 - i * 2)) # sudden drop
  fi
  curl -s -X POST http://localhost:8080/api/test/inject \
    -H "Content-Type: application/json" \
    -d "{\"symbol\": \"TEST_TA4J\", \"open\": $PRICE.0, \"high\": $PRICE.0, \"low\": $PRICE.0, \"close\": $PRICE.0, \"volume\": 1000, \"timestamp\": $TIMESTAMP}" > /dev/null
done

echo -e "\nTa4j historical bars injected successfully. Check your application logs and Telegram for both alerts!"

