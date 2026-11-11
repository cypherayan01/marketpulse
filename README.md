# MarketPulse

Personal market-intelligence app for NSE swing / BTST / positional trading.
Signals and explanations only - **not investment advice, no order placement**.

## Phase 1: News alerter (done)

```
cron-job.org (every 15 min, 24/7)
   └─ POST /jobs/news  (X-Job-Token header)
        └─ RSS feeds (ET, Business Standard, Mint, CNBC, BBC)
             → keep news < 45 min old, not seen before
             → keyword / watchlist pre-filter          (free)
             → Claude Haiku 5.5 scores impact 1-10     (batches of 20)
             → impact >= 7 → Telegram + email (Resend)
```

Tune in `backend/src/main/resources/application.yml`: feeds, keywords, watchlist, `impact-threshold`.
Every scored headline is logged (`Scored 5/10 BULLISH - ...`), so check the logs before changing the threshold.

## Phase 2: Swing end-of-day watchlist (done)

```
cron-job.org (Mon-Fri 16:30 IST)
   └─ POST /jobs/eod  (X-Job-Token header)  → 202, runs in background (~20 s)
        └─ Yahoo daily candles (~2 years) for Nifty 50 + ^NSEI
             → skip if no session today (holiday) unless ?force=true
             → ta4j: EMA 20/50/200, RSI 14, MACD 12/26/9 (+ fresh crossovers), ATR 14,
                     20-day high/low, volume vs 20-day average, 52-week high, 3-month return
             → SwingScorer: 0-100 score, classification, confidence (signal agreement),
                            why / watch-out factors, entry · stop · target (1:2), invalidation level
             → report: top 5 bullish setups + top 5 "avoid / review if holding"
```

Scoring rules are documented on `SwingScorer` and specified by `SwingScorerTest`.
Bearish stocks get no trade plan: overnight shorts aren't possible in the cash market.
Stock list: `marketpulse.swing.universe` in `application.yml`.

## Phase 3: Pre-market brief + event calendar (done)

```
cron-job.org (Mon-Fri 08:30 IST)
   └─ POST /jobs/premarket → 202, runs in background (~30-60 s)
        → skip weekends and NSE holidays (marketpulse.events.holidays) unless ?force=true
        → global cues from Yahoo (US, Asia, crude, gold, dollar, INR, US 10Y, India VIX, Nifty)
          with % change computed by the app
        → relevant headlines since the previous session's 15:30 IST close
        → events from the calendar (today + 3 days)
        → Claude Opus 5.5 writes the brief from ONLY those facts (server-side refusal fallback on)
        → exact numbers appended below; if Claude fails, a numbers-only brief is still sent
```

**Event calendar** (`marketpulse.events` in `application.yml`):
- `macro`: RBI / Fed / Budget dates (seeded through Oct 2027 - add CPI etc. as you like)
- `results`: manual results dates, e.g. `- { symbol: TCS, date: 2027-01-08 }`
- results dates announced in news ("X board meeting on <date> to consider results") are
  extracted by Haiku automatically - kept in memory, so they're lost on restart/redeploy
- `holidays`: NSE trading holidays - **add the 2027 list when NSE publishes it**

The swing report now warns `⚠️ Results on 17 Nov (in 6 days)` for setups with results inside
`results-warning-days` (15), and lists the next 7 days' events.

## Phase 4: Positional weekly scan (done)

```
cron-job.org (Sunday 18:00 IST)
   └─ POST /jobs/positional → 202, runs in background (~20 s)
        → the same 2 years of daily Yahoo candles, rolled up into ISO weeks (~104 weekly bars)
        → ta4j on the weekly chart: 40-week SMA (+ its 4-week slope), EMA 10/30, RSI 14, MACD,
          ATR 10, 26-week (6-month) high/low, weekly volume vs 10-week average, 52-week high
        → PositionalScorer: trend, momentum, 6-month breakout, distance from 52-week high,
          6-month strength vs Nifty (weighted most) · stop from weekly ATR / 8-week low · 1:3 target
        → report: top 5 positional setups + top 5 "avoid / review long-term holdings" + next 7 days' events
```

Swing and positional scorers share `signals.Factors` (points → 0-100 score, why/watch-out split,
confidence) and return the same `TradeSignal`. Rules are documented on `PositionalScorer` and
specified by `PositionalScorerTest`. Universe: `marketpulse.positional.universe` (empty = swing's list).

## Run locally

```bash
cd backend
./mvnw test                                   # 111 tests
./mvnw -DskipTests package
JOB_TOKEN=local-test ANTHROPIC_API_KEY=sk-ant-... java -jar target/backend-0.0.1-SNAPSHOT.jar
curl -X POST -H "X-Job-Token: local-test" localhost:8080/jobs/news
curl -X POST -H "X-Job-Token: local-test" "localhost:8080/jobs/eod?force=true"        # report appears in the log
curl -X POST -H "X-Job-Token: local-test" "localhost:8080/jobs/premarket?force=true"  # uses one Opus call
curl -X POST -H "X-Job-Token: local-test" localhost:8080/jobs/positional
```

Without Telegram/email configured, alerts are printed to the log.

> `.mvn/maven.config` points Maven at a project-local settings file (Maven Central),
> so the global `~/.m2/settings.xml` (client Artifactory) is not used for this project.

## Set up alert channels (free)

**Telegram**: message `@BotFather` → `/newbot` → copy token → `TELEGRAM_BOT_TOKEN`.
Send any message to your bot, open `https://api.telegram.org/bot<TOKEN>/getUpdates`, copy `chat.id` → `TELEGRAM_CHAT_ID`.

**Email**: sign up at resend.com → API key → `RESEND_API_KEY`, `ALERT_EMAIL_TO` = your address.
The default sender `onboarding@resend.dev` can only email the address you signed up with - fine for personal use.

## Deploy (free)

1. Push this repo to GitHub (private is fine).
2. Render → New → Blueprint → select the repo (uses `render.yaml`). Fill in the secret env vars.
   Copy the generated `JOB_TOKEN` from the service's Environment tab.
3. cron-job.org → new job:
   - URL `https://<your-service>.onrender.com/jobs/news`, method **POST**
   - header `X-Job-Token: <JOB_TOKEN>`
   - every 15 minutes, timeout 60s+ (first call after sleep takes ~1 min to wake Render)
   - second job: `POST /jobs/eod`, same header, **Mon-Fri 16:30, timezone Asia/Kolkata**
   - third job: `POST /jobs/premarket`, same header, **Mon-Fri 08:30, timezone Asia/Kolkata**
   - fourth job: `POST /jobs/positional`, same header, **Sunday 18:00, timezone Asia/Kolkata**
4. Anthropic Console → set a monthly spend limit.

## Next phases

5. BTST 3 PM scan · 6. React dashboard on Vercel · 7. Signal tracking / paper trading
