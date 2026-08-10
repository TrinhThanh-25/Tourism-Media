import express from "express";
import dotenv from "dotenv";
import cors from "cors";
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';

// Load .env relative to this server file so running node from another cwd still
// picks up the backend `.env` (ensures DB_PATH and other settings are correct).
dotenv.config({ path: new URL('./.env', import.meta.url).pathname });
// Import DB dynamically after dotenv is configured so DB can read DB_PATH
const dbModule = await import("./db/connect.js");
const db = dbModule.default;
const dbReady = dbModule.ready;

// Wait for DB creation/migrations before loading routes that may query the DB
await dbReady;

// Dynamic route imports (single pass, avoid duplicate declarations)
const [
  locationRoutesModule,
  challengeRoutesModule,
  meRoutesModule,
  rewardRoutesModule,
  locationImageRoutesModule,
  authRoutesModule,
	reviewRoutesModule,
	tripReviewRoutesModule,
	pointsRoutesModule,
	tripsRoutesModule
] = await Promise.all([
  import('./routes/locationRoutes.js'),
  import('./routes/challengeRoutes.js'),
  import('./routes/meRoutes.js'),
  import('./routes/rewardRoutes.js'),
  import('./routes/locationImageRoutes.js'),
  import('./routes/authRoutes.js'),
	import('./routes/locationReviewRoutes.js'),
	import('./routes/tripReviewRoutes.js'),
	import('./routes/pointsRoutes.js'),
	import('./routes/tripsRoutes.js')
]);

const locationRoutes = locationRoutesModule.default;
const challengeRoutes = challengeRoutesModule.default;
const meRoutes = meRoutesModule.default;
const rewardRoutes = rewardRoutesModule.default;
const locationImageRoutes = locationImageRoutesModule.default;
const authRoutes = authRoutesModule.default;
const reviewRoutes = reviewRoutesModule.default;
const tripReviewRoutes = tripReviewRoutesModule.default;
const pointsRoutes = pointsRoutesModule.default;
const tripsRoutes = tripsRoutesModule.default;

const app = express();
// limit request body size to avoid large payload attacks
app.use(express.json({ limit: '10kb' }));

// basic security headers
app.use(helmet());

const limiterOptions = {
	windowMs: Number(process.env.RATE_LIMIT_WINDOW_MS) || 15 * 60 * 1000,
	max: Number(process.env.RATE_LIMIT_MAX) || 500,
	standardHeaders: true,
	legacyHeaders: false,
};
const globalLimiter = rateLimit(limiterOptions);
app.use(globalLimiter);

// Configure CORS for development. Frontend should set its origin in CORS_ORIGIN
// Example: CORS_ORIGIN=http://localhost:5173
const corsOrigin = process.env.CORS_ORIGIN || "*";
app.use(cors({ origin: corsOrigin }));

// Định nghĩa các nhóm API
app.use("/api/locations", locationRoutes);
// nested images route for locations
app.use('/api/locations/:locationId/images', locationImageRoutes);
app.use("/api/challenges", challengeRoutes);
app.use("/api/me", meRoutes);
// mount auth routes at /auth to match endpoints like /auth/register
app.use("/auth", authRoutes);
app.use("/api/rewards", rewardRoutes);
app.use("/api/reviews", reviewRoutes);
app.use("/api/trip-reviews", tripReviewRoutes);
app.use("/api/admin/points", pointsRoutes);
app.use("/api/trips", tripsRoutes);

// lightweight API root/status endpoint so visiting /api returns useful info
app.get('/api', (req, res) => {
	res.json({
		status: 'ok',
		api: '/api',
		message: 'Travel App API is ready.'
	});
});

// Proxy Chat to external Python Chat API (set CHAT_API_URL in env). If not configured, return 501.
app.post('/api/chat', async (req, res) => {
	const target = process.env.CHAT_API_URL;
	if (!target) {
		return res.status(501).json({ error: 'CHAT_API_URL not configured' });
	}
	try {
		const resp = await fetch(target, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(req.body || {})
		});
		const contentType = resp.headers.get('content-type') || '';
		res.status(resp.status);
		if (contentType.includes('application/json')) {
			const data = await resp.json();
			return res.json(data);
		}
		const text = await resp.text();
		return res.send(text);
	} catch (e) {
		return res.status(502).json({ error: 'Failed to reach Chat API', detail: e.message });
	}
});

const PORT = process.env.PORT || 3000;
export default app;

if (process.env.NODE_ENV !== 'test') {
	app.listen(PORT, () => console.log(`🚀 Server running on port ${PORT} (CORS allowed: ${corsOrigin})`));
}
