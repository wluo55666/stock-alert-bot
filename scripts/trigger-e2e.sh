#!/bin/bash

# Ensure script stops on first error
set -e

echo "Triggering E2E test by injecting synthetic trades for sliding window and ta4j analysis..."

# 1. Inject a baseline trade for TEST_CO at $100
echo "Injecting baseline trade: TEST_CO at \$100.00"
curl -X POST http://localhost:8080/api/test/inject \
  -H "Content-Type: application/json" \
  -d '{"symbol": "TEST_CO", "price": 100.0, "timestamp": '$(date +%s000)'}'

# Wait a second to ensure order
sleep 1

# 2. Inject a spiked trade for TEST_CO at $150 (50% spike) to trigger the Sliding Window alert
echo -e "\nInjecting spiked trade: TEST_CO at \$150.00 (50% spike for sliding window alert)"
curl -X POST http://localhost:8080/api/test/inject \
  -H "Content-Type: application/json" \
  -d '{"symbol": "TEST_CO", "price": 150.0, "timestamp": '$(date +%s000)'}'

echo -e "\nSliding Window Trades injected successfully."

# 3. Inject enough trades to build 35+ bars for MACD/RSI
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
    -d "{\"symbol\": \"TEST_TA4J\", \"price\": $PRICE.0, \"timestamp\": $TIMESTAMP}" > /dev/null
done

echo -e "\nTa4j historical bars injected successfully. Check your application logs and Telegram for both alerts!"

