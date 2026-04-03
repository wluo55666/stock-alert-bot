# Stock Alert Bot

A Java/Spring Boot stock monitoring bot that polls Yahoo Finance 15-minute candles, scores technical setups, and sends Telegram alerts with AI-generated trading context.

## What it does
- Polls Yahoo Finance for configured symbols on a 1-minute loop
- Promotes only **new completed 15-minute candles** into analysis
- Runs two signal paths:
  - **Sliding-window momentum** for breakout / breakdown detection
  - **ta4j MACD + RSI reversal** detection
- Applies **cooldowns**, **confirmation bars**, and **signal scoring** to reduce noise
- Uses **selective news enrichment** for stronger TA alerts instead of forcing web search for every alert
- Sends Telegram alerts with concise, actionable AI summaries

## Current behavior
- Symbols come from `APP_SYMBOLS`
- The bot only publishes a bar when a **new 15-minute candle** appears
- Incomplete / zero-volume candles are ignored
- Stale candles are suppressed instead of being rebuilt forever
- Alerts are intentionally selective; quiet days may produce no alerts
- Strong TA alerts can fetch recent news context before AI synthesis

## Decision logging
The bot now logs explicit per-bar / per-signal decisions so you can tell why a symbol did or did not alert.

Examples:
- `Sliding-window decision symbol=TSLA action=SUPPRESS score=2 minimumScore=3 ...`
- `TA decision symbol=OXY signal=NONE action=NO_TRIGGER ...`
- `TA decision symbol=TSLA signal=BULLISH REVERSAL 📈 action=ALERT score=4 ... newsUsed=true`

This makes it easier to distinguish:
- no trigger
- suppressed by low score
- suppressed by missing confirmation
- suppressed by cooldown
- alert sent
- whether news enrichment ran

## Tech stack
- Java 24
- Spring Boot 3.4
- ta4j
- LangChain4j
- Gemini (alert phrasing)
- Tavily (selective news enrichment)
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

## Selective news enrichment
The bot does **not** force web search on every alert.

Current approach:
- sliding-window alerts: no news enrichment
- ta4j alerts: news lookup only for stronger alerts (currently score >= 4)

Why:
- lower latency
- fewer external failure points
- lower cost
- more relevant use of news context

## Operational notes
- Redis is used for alert deduplication/cooldowns
- The app logs new 15-minute candle publications and signal decisions
- If no alerts fire on a given day, that can be normal under the current scoring thresholds

## Current limitations / next improvements
- Add volume-aware signal scoring
- Add retry/backoff for transient Yahoo failures
- Add ticker-specific profiles
- Improve support/resistance and multi-timeframe context
