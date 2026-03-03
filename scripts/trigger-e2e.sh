#!/bin/bash

# Ensure script stops on first error
set -e

echo "Triggering E2E test by injecting synthetic trades..."

# 1. Inject a baseline trade for TEST_CO at \$100
echo "Injecting baseline trade: TEST_CO at \$100.00"
curl -X POST http://localhost:8080/api/test/inject \
  -H "Content-Type: application/json" \
  -d '{"symbol": "TEST_CO", "price": 100.0, "timestamp": '$(date +%s000)'}'

# Wait a second
sleep 1

# 2. Inject a spiked trade for TEST_CO at \$150 (50% spike)
echo -e "\nInjecting spiked trade: TEST_CO at \$150.00 (50% spike)"
curl -X POST http://localhost:8080/api/test/inject \
  -H "Content-Type: application/json" \
  -d '{"symbol": "TEST_CO", "price": 150.0, "timestamp": '$(date +%s000)'}'

echo -e "\nTrades injected successfully. Check your application logs and Telegram for the alert!"
