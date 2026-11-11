# MarketPulse dashboard (React + TypeScript + Vite)

Reads `GET /api/dashboard` from the Spring Boot backend and shows the latest brief, swing / positional /
BTST setups, scored news and upcoming events. Mobile-first.

```bash
npm install
cp .env.example .env.local      # point VITE_API_BASE_URL at the backend
npm run dev                     # http://localhost:5173
npm test                        # Vitest + React Testing Library
npm run build && npm run lint
```

Log in with the backend's `DASHBOARD_TOKEN`; it is kept in this browser's localStorage only.

## Deploy on Vercel (free)

1. vercel.com → Add New Project → import `cypherayan01/marketpulse`.
2. **Root Directory: `frontend`** (framework preset: Vite is detected).
3. Environment variable `VITE_API_BASE_URL` = your Render URL, e.g. `https://marketpulse-backend.onrender.com`.
4. Deploy, then copy the Vercel URL into the backend's `DASHBOARD_ORIGINS` on Render (otherwise CORS blocks it).
