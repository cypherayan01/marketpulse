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

## Run locally

```bash
cd backend
./mvnw test                                   # 31 tests
./mvnw -DskipTests package
JOB_TOKEN=local-test ANTHROPIC_API_KEY=sk-ant-... java -jar target/backend-0.0.1-SNAPSHOT.jar
curl -X POST -H "X-Job-Token: local-test" localhost:8080/jobs/news
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
4. Anthropic Console → set a monthly spend limit.

## Next phases

2. Swing end-of-day watchlist (Yahoo daily candles + ta4j + `SwingScorer`)
3. Pre-market brief + event calendar (Claude Opus 5.5)
4. Positional weekly scan · 5. BTST 3 PM scan · 6. React dashboard on Vercel · 7. Signal tracking / paper trading
