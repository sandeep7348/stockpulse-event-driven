# NSE AlertPulse frontend

The dashboard for the NSE Stock Price Alert System, built with React, TypeScript, Vite, Tailwind CSS and TanStack Query.

## Preview with sample data

No backend needed. Any email and password work, and a banner marks the data as a demo.

```bash
npm install
npm run dev:mock
```

Open http://localhost:5173.

## Run against the backend

Start the backend on port 8090 (see the main README), then:

```bash
npm run dev
```

The dev server forwards `/api` to `http://localhost:8090`. To use another address, set `API_PROXY_TARGET` in `.env.local`.

## Check and build

```bash
npm test
```

```bash
npm run build
```

Production builds never include sample data behaviour unless `VITE_API_MODE=mock` is set. Host the `dist` folder behind the same domain as the API, or set `VITE_API_BASE_URL` and add CORS to the backend.

## Things to know

- **Prices are rupees.** The API already converts from paise, so the frontend must not divide by 100.
- **The feed badge is earned.** `YAHOO FINANCE` or `UPSTOX LIVE` needs a recent price during market hours. Otherwise it reads `LAST TRADE`, `WAITING` or `STALE`, because the API reports only which feed is configured, not whether it's healthy.
- **Acknowledgements are per browser.** The backend has no read state for notifications, so they're stored in `localStorage` and don't follow you to another device. The unread count covers the latest 100 notifications.
- **Session storage.** The JWT is kept in `localStorage`, so it survives a refresh but could be read by any script injected into the page. Tokens last one hour.
- **Analysis chart.** The chart is plain SVG with no chart library. Price, volume and RSI are separate aligned panels rather than sharing an axis. It shows indicators only, with no buy or sell signals.
- **Stock search.** Search covers all NSE stocks, but prices load only for the watchlist, stocks you pick and stocks with active alerts. The page asks the backend for each of those every 5 seconds, and the backend limits how often it goes to Yahoo.
