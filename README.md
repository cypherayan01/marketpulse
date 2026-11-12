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

## Phase 5: BTST 3 PM scan (done)

```
cron-job.org (Mon-Fri 14:50 IST)
   └─ POST /jobs/btst → 202, runs in background (~40 s: daily + 15-min bars per stock)
        → skip if no session today unless ?force=true
        → today's session so far: close location in the day's range, day change, volume projected
          to a full session, last-hour momentum (15-min bars), 20-day breakout, trend, RSI,
          strength vs Nifty today, market mood
        → results due before tomorrow's session (Fri → Mon) rule a stock out entirely
        → overnight macro events (e.g. Fed decision tonight) flagged at the top
        → top 3 candidates: tight stop (today's low / 1 ATR), 1.5x target,
          "exit if it opens below the stop or can't hold today's price in the first 15 minutes"
```

Rules are documented on `BtstScorer` and specified by `BtstScorerTest`. Yahoo prices can lag a
few minutes - fine for picking candidates; check the live price in your broker app before buying.

## Phase 6: React dashboard (done)

```
frontend/ (React 19 + TypeScript + Vite, deployed on Vercel)
   └─ GET  /api/dashboard   Authorization: Bearer <DASHBOARD_TOKEN>
   └─ POST /api/run/{news|premarket|eod|positional|btst|track|holdings|predictions|replay}   start a job from the UI
backend: every job saves its latest output in ReportStore (in memory) → one JSON response
```

- Sidebar layout (dark theme, Inter font, lucide icons, phone-friendly). Tabs: Overview (market / VIX / holdings /
  hit-rate tiles, brief with "Write the brief now", latest picks, recent alerts), Analyse a stock, My holdings,
  Swing, Positional, BTST, Predictions, Track record, News ("Check news now"), Events.
- Signal cards: score, plan (entry / stop / target), why, ⚠️ watch-outs, results warning, invalidation.
- `DASHBOARD_TOKEN` is separate from `JOB_TOKEN`; CORS allows only `DASHBOARD_ORIGINS`.
- After a Render restart the store is empty until jobs run - each tab offers "Run … scan now"
  (it behaves like the scheduled run, so it also sends the report to Telegram/email).
- While Render wakes up (~1 min) the UI shows "Waking up the server…" and keeps retrying.

See `frontend/README.md` for local dev and Vercel deployment.

## Phase 7: Signal tracking & paper trading (done)

```
every saved scan ──► SignalRecorder: each bullish setup with a plan → OPEN paper trade
                     (one open trade per stock per style; same day's signal never stored twice)
POST /jobs/eod ────► swing scan, then SignalTracker walks open trades through the new daily candles:
                     stop first if a day hits both · gaps fill at the open · swing exits after 15
                     sessions, positional after 120, BTST at the next open (≈ "exit by 10:30")
GET /api/performance ► per style: win rate, average return per trade (expectancy), avg win / loss,
                     target vs stop vs time exits, split by score band and confidence
```

Also stored in the database now: results dates found in news, and the latest dashboard reports
(so a Render restart no longer empties the dashboard or forgets those dates).

**Database:** Spring JDBC + Flyway (`backend/src/main/resources/db/migration`). Without
`DATABASE_URL` the app uses a local H2 file (`backend/data/`, zero setup, kept across restarts). For production use
free Postgres on **Neon**:
1. neon.tech → new project (region close to Render, e.g. Singapore) → Connection details →
   choose **Java** to get a `jdbc:postgresql://…?sslmode=require` URL.
2. On Render set `DATABASE_URL` (the JDBC URL without user/password), `DATABASE_USERNAME`,
   `DATABASE_PASSWORD`. Migrations run automatically on startup.
3. Neon's free tier suspends when idle; the first query after that takes a few seconds (the pool waits 30 s).

Treat the track record with care: under ~30 closed trades per style it's noise, and paper trades
ignore brokerage, taxes and slippage.

## Holdings health check (done)

Add the stocks you own on the dashboard (**My holdings** tab; `POST /api/holdings` checks the symbol
has NSE price data). After every close, `/jobs/eod` also checks them:

- 🔴 **EXIT** - closed below the active stop: the tighter of *your* stop and a **trailing stop**
  (highest close since you bought minus 2 ATR for swing, 3 ATR for positional), so gains get protected
- 🟡 **REVIEW** - the setup turned bearish, the trend average broke (50-day EMA swing / 40-week
  average positional), or results are due within 7 days
- 🟢 **HOLD** - otherwise; notes such as "target reached"

You get a "💼 Holdings check" message (exits first, P&L in % and ₹), and the tab shows the same.
`POST /jobs/holdings` runs it on its own; adding a holding on the dashboard checks it right away.

## All market caps (Nifty 500)

Swing, positional and BTST scans cover all of **Nifty 500**, reported in sections:

| Section | Index | Stocks |
|---|---|---|
| 🔵 Large cap | Nifty 100 | 100 |
| 🟣 Mid cap | Nifty Midcap 100 | 100 |
| 🟠 Small cap | Nifty Smallcap 100 | 100 |
| ⚪ Other Nifty 500 | rest of Nifty 500 | ~200 |

Each section lists its top 6 setups and top 6 to avoid, with the segment index's own move in the header.
The dashboard has an All / Large / Mid / Small / Other filter, and the track record compares results by segment.

- Index lists are bundled in `backend/src/main/resources/indices` (from niftyindices.com). NSE rebalances
  them in March and September - refresh with `scripts/update-indices.sh`, run the tests, commit.
- A 500-stock scan takes ~4 minutes (Yahoo requests are spaced 300 ms apart). Daily candles are cached for
  20 minutes, so tracking and the holdings check after the swing scan reuse them.
- BTST only lists **strong** setups (score 80+), at most 3 per section - overnight risk is real.
- BTST pre-scores all 500 on today's candle and fetches 15-minute bars only for the best 10 per segment -
  **schedule `/jobs/btst` at 14:50** so it finishes well before 15:25.
- Stocks with too little history (new listings, demergers) are skipped and counted in one line. A stock with
  under 200 days of history simply has no 200-day EMA factor (it is not penalised as "below" it).
- Network drops are retried (2 s, then 5 s). If more than 10% of stocks still have no data, the report says
  "⚠️ Data incomplete" (and the subject ends with "(incomplete)") instead of silently showing partial lists.
- Yahoo has no history for the Midcap/Smallcap 100 indices: day moves come from a live quote, and the weekly
  report describes those segments by their stocks' median 6-month return.
- To scan your own list instead, set `marketpulse.swing.universe: [RELIANCE, TCS, ...]`.

## Analyse a stock

Dashboard tab **Analyse a stock** (`GET /api/analyze/{symbol}`) works for any NSE stock, not just Nifty 500:

| Swing (daily) | Positional (weekly) | Verdict for a new buyer |
|---|---|---|
| bullish | bullish | **Buy** - short and long term agree |
| bullish | not bullish / no history | **Buy for a swing** only |
| not bullish | bullish | **Accumulate** - wait for a better entry or buy in parts |
| bearish | bearish / no history | **Avoid** (in the cash market "sell" = don't buy / exit if you hold) |
| otherwise | | **Wait** - mixed signals |

Tick "I already own this stock" (buy price, date, quantity, horizon, optional stop) to get **Keep / Review /
Exit** with the same trailing-stop rules as the holdings check. Also shown: results date, recent scored news,
segment and industry. **Explain with AI** (`POST /api/analyze/{symbol}/explain`) has Claude write a plain-English
summary from the computed numbers - one API call per click. During market hours the price is the live one.

## Prediction tracking (scorecard)

Every bullish (**Buy**) and bearish (**Avoid**) call in the swing, positional and BTST reports is saved with
the price, Nifty's close that day and the news sentiment for the stock (recorded only, not used in scoring yet).
After each close `/jobs/eod` fills in what happened after **1, 2, 3, 5, 10, 20, 40 and 60 sessions**:

- **Right** = beat Nifty over that window (Buy: stock − Nifty > 0, Avoid: Nifty − stock > 0). Beating the
  index is the bar, because in a rising market almost everything goes up.
- **BTST** is also judged on the next morning: did it open higher, and how high did it get during the day.

Dashboard tab **Predictions**: hit rate per checkpoint (50% = coin flip), average edge vs Nifty, breakdowns by
score band, market cap and news agreement, and a day picker ("what did it say on 11 Nov, how did it go?").
The **Track record** tab (paper trades with stop/target) stays as it was - both views are kept.

**History replay** (`POST /jobs/replay`, or the button in the tab; ~4 min, 500 stocks × 250 sessions) rebuilds
the same calls for the past year using only data available on each day, so you get a scorecard without waiting
months. It's an estimate: today's index lists (survivorship bias), no news, and BTST without the last-hour data.
First run (Nov 2026): swing ~50% right at 1-20 days (no edge yet), positional ~53% at 40-60 days (+1.6-1.9 pts),
BTST opened higher 61% of the time with an average next-day high of +1.9%.

## Run locally

```bash
cd backend
./mvnw test                                   # 295 tests (in-memory H2 for the database tests)
# dashboard API: also set DASHBOARD_TOKEN=... (and DASHBOARD_ORIGINS for a non-local frontend)
./mvnw -DskipTests package
JOB_TOKEN=local-test ANTHROPIC_API_KEY=sk-ant-... java -jar target/backend-0.0.1-SNAPSHOT.jar
curl -X POST -H "X-Job-Token: local-test" localhost:8080/jobs/news
curl -X POST -H "X-Job-Token: local-test" "localhost:8080/jobs/eod?force=true"        # report appears in the log
curl -X POST -H "X-Job-Token: local-test" "localhost:8080/jobs/premarket?force=true"  # uses one Opus call
curl -X POST -H "X-Job-Token: local-test" localhost:8080/jobs/positional
curl -X POST -H "X-Job-Token: local-test" "localhost:8080/jobs/btst?force=true"
curl -X POST -H "X-Job-Token: local-test" localhost:8080/jobs/replay                  # past-year scorecard
```

Local secrets go in `backend/.env` (copy `.env.example`, gitignored) - the app reads it on startup, from `backend/`
or the repo root. Real environment variables (e.g. on Render) take priority; tests never use it.

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
   - (`/jobs/eod` also runs paper-trade tracking and the holdings check - no extra jobs needed)
   - fourth job: `POST /jobs/positional`, same header, **Sunday 18:00, timezone Asia/Kolkata**
   - fifth job: `POST /jobs/btst`, same header, **Mon-Fri 14:50, timezone Asia/Kolkata** (500-stock scan takes ~4 min)
4. Anthropic Console → set a monthly spend limit.
5. Vercel → import the repo with Root Directory `frontend`, set `VITE_API_BASE_URL` to the Render URL,
   then put the Vercel URL in Render's `DASHBOARD_ORIGINS`.

## Next phases

- Use the track record to tune weights once each style has ~30+ closed trades
