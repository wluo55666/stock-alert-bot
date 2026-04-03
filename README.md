# Stock Alert Bot

A Java/Spring Boot stock monitoring bot that polls Yahoo Finance 15-minute candles, scores technical setups, and sends Telegram alerts with AI-generated trading context.

## What it does
- Polls Yahoo Finance for configured symbols on a 1-minute loop
- Promotes only **new completed 15-minute candles** into analysis
- Runs two signal paths:
  - **Sliding-window momentum** for breakout / breakdown detection
  - **ta4j MACD + RSI reversal** detection
- Applies **cooldowns**, **confirmation bars**, and **signal scoring** to reduce noise
- Sends Telegram alerts with concise, actionable AI summaries

## Current behavior
- Symbols come from `APP_SYMBOLS`
- The bot only publishes a bar when a **new 15-minute candle** appears
- Incomplete / zero-volume candles are ignored
- Stale candles are suppressed instead of being rebuilt forever
- Alerts are intentionally selective; quiet days may produce no alerts

## Tech stack
- Java 24
- Spring Boot 3.4
- ta4j
- LangChain4j
- Gemini (alert phrasing)
- Redis (dedupe / cooldown state)
- Docker + GitHub Actions

## Required environment variables
- `APP_SYMBOLS`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_CHAT_ID`
- `GEMINI_API_KEY`
- `TAVILY_API_KEY`

See `.env.example` for a template.

## Local run
```bash
docker compose up -d --build
```

## E2E testing
To quickly test the internal AI alert synthesis and Telegram delivery pipeline without waiting for real stock conditions, you can run the synthetic E2E trigger script:

```bash
APP_TEST_ENDPOINTS_ENABLED=true ./scripts/trigger-e2e.sh
```

This script can:
1. inject synthetic sliding-window bars
2. inject enough synthetic historical bars to exercise the ta4j path
3. directly trigger an AI-generated Telegram alert through a test-only endpoint

### Safety note
- `/api/test/*` endpoints are enabled only when `app.test-endpoints-enabled=true`
- the script can send a **real Telegram message** if your bot credentials are configured
- use this only in local/dev testing

## Key tuning knobs
In `src/main/resources/application.yml`:
- `app.sliding-window.threshold-percent`
- `app.sliding-window.confirmation-bars`
- `app.sliding-window.strong-move-percent`
- `app.sliding-window.minimum-score`
- `app.ta4j.confirmation-bars`
- `app.ta4j.minimum-score`
- cooldown settings for both strategies

## Operational notes
- Redis is used for alert deduplication/cooldowns
- The app logs new 15-minute candle publications and signal decisions
- If no alerts fire on a given day, that can be normal under the current scoring thresholds

## Current limitations / next improvements
- Add clearer per-bar decision summaries for suppressed signals
- Add retry/backoff for transient Yahoo failures
- Add ticker-specific profiles
- Improve support/resistance and multi-timeframe context
