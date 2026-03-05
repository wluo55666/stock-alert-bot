# Real-Time Market Alert Bot 📈

A high-performance, reactive Java backend that monitors live market data (Stocks via Finnhub) and triggers alerts based on configurable rules.

## 🛠️ Tech Stack

* **Language:** Java 24
* **Framework:** Spring Boot 3 (WebFlux / Reactive)
* **Real-time:** WebSockets
* **State Management:** Redis (for sliding windows and deduplication)
* **Build:** Gradle
* **Containerization:** Docker & Docker Compose

## 🚀 Features (Implemented)

1. **Ingest:** Connect to live WebSocket feeds (Finnhub US Stocks).
2. **Basic Analysis:** Sliding window analysis backed by Redis ZSET (e.g., "Price dropped 5% in 5 minutes").
3. **Advanced Technical Analysis:** `ta4j` integration that aggregates live trades into OHLC time-based bars (e.g., 1-minute bars) and runs technical indicators (RSI and MACD) to generate smart bullish/bearish signal alerts.
4. **Alert:** Sends notifications via Telegram Bot API with Redis deduplication to prevent spamming.
5. **Testing:** End-to-end (E2E) testing support via synthetic trade injections.
6. **CI/CD:** Automated deployment to a self-hosted home server via GitHub Actions and Docker Compose.

## 🏃‍♂️ How to Run

### Prerequisites

* Docker Desktop installed.
* Java 24 installed.
* A Finnhub API Key.
* A Telegram Bot Token and Chat ID.

### Start the Application

1. Spin up the Redis container: `docker compose up -d`
2. Start the application with environment variables:

   ```bash
   APP_SYMBOLS=AAPL,MSFT,TSLA \
   FINNHUB_API_KEY=your_key \
   TELEGRAM_BOT_TOKEN=your_token \
   TELEGRAM_CHAT_ID=your_chat_id \
   ./gradlew bootRun
   ```

### Running E2E Tests

You can trigger synthetic trades to test the alert mechanisms without waiting for real market movements.
First, make sure the application is running, then run the E2E script:

```bash
./scripts/trigger-e2e.sh
```

This will inject a sudden price spike for the sliding window alert and a series of historical bars to trigger the MACD technical indicator alert. Check your application logs and Telegram for both alerts.
