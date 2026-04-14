# Stock Alert Bot

A Java/Spring Boot stock monitoring bot that polls Yahoo Finance 15-minute candles, scores technical setups, and sends Telegram alerts with AI-generated trading context.

## What it does
- Polls Yahoo Finance for configured symbols on a 1-minute loop
- Promotes only **new completed 15-minute candles** into analysis
- Runs two signal paths:
  - **Sliding-window momentum** for breakout / breakdown detection
  - **ta4j MACD + RSI reversal** detection
- Applies **cooldowns**, **confirmation bars**, and **signal scoring** to reduce noise
- Uses **selective news enrichment** for stronger alerts instead of forcing web search for every alert
- Sends Telegram alerts in a more human-readable analyst style

## Current behavior
- Symbols come from `APP_SYMBOLS`
- The bot only publishes a bar when a **new 15-minute candle** appears
- Incomplete / zero-volume candles are ignored
- Stale candles are suppressed instead of being rebuilt forever
- Alerts are intentionally selective; quiet days may produce no alerts
- Strong alerts from both TA and sliding-window paths can fetch recent news context before final rendering
- TA alerts are rendered through a structured formatter for more predictable readability
- Sliding-window alerts are aligned to the same structured style

## Structured alerts
Alerts now use a structured rendering flow.

### TA alerts
1. AI generates structured fields:
   - `summary`
   - `whyItMatters`
   - `nextWatch`
   - `invalidation`
   - `newsCatalyst`
2. application code formats the final Telegram message consistently

### Sliding-window alerts
- use the same structured formatter style
- can also include a catalyst line when a strong short-window alert triggers news enrichment

This makes the final alert:
- easier to scan
- more consistent across runs
- clearer when news is involved

## Decision logging
The bot logs explicit per-bar / per-signal decisions so you can tell why a symbol did or did not alert.

Examples:
- `Sliding-window decision symbol=TSLA action=SUPPRESS score=2 minimumScore=3 ...`
- `TA decision symbol=OXY signal=NONE action=NO_TRIGGER ...`
- `TA decision symbol=TSLA signal=BULLISH REVERSAL 📈 action=ALERT score=4 ... newsUsed=true`
- `Sliding-window decision symbol=XYZ signal=BREAKOUT action=ALERT score=4 ... newsUsed=true`

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
- Gemini (structured alert generation)
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
To quickly test the alert pipeline without waiting for real stock conditions, you can run the synthetic E2E trigger script:

```bash
APP_TEST_ENDPOINTS_ENABLED=true ./scripts/trigger-e2e.sh
```

The expanded script now covers multiple paths:
1. **sliding-window ALERT** path
2. **sliding-window low-score / no-trigger** path
3. **TA decision logging** path
4. **direct synthetic alert** path
5. **strong synthetic alert** path for high-score alert behavior

### Safety note
- `/api/test/*` endpoints are enabled only when `app.test-endpoints-enabled=true`
- the script can send **real Telegram messages** if your bot credentials are configured
- use this only in local/dev testing

### Important nuance
The script is designed to cover more branches and decision logs, but the injected TA bar sequence still does not guarantee a real ta4j `ALERT` on every run because crossover state depends on the exact indicator evolution. It is best used to validate:
- endpoint wiring
- log coverage
- Telegram delivery
- decision-summary behavior

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
- strong ta4j alerts: news lookup when score is high enough
- strong sliding-window alerts: news lookup when score is high enough
- weaker alerts: no news lookup

Why:
- lower latency on weak/noisy setups
- fewer external failure points
- lower cost
- more relevant use of news context

## Alert style
The final alert is rendered with a structured layout:
- title
- summary
- why it matters
- optional news catalyst
- watch next
- invalidation
- optional higher-conviction note

## Operational notes
- Redis is used for alert deduplication/cooldowns
- The app logs new 15-minute candle publications and signal decisions
- If no alerts fire on a given day, that can be normal under the current scoring thresholds

## Current limitations / next improvements
- Add volume-aware signal scoring
- Add retry/backoff for transient Yahoo failures
- Add ticker-specific profiles
- Improve support/resistance and multi-timeframe context
