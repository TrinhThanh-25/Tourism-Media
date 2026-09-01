import express from "express";
import dotenv from "dotenv";
import cors from "cors";
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { UPLOAD_DIR } from "./config/uploads.js";

// Configuration lives at the repository root so collaborators can find it
// immediately. Resolving from this file keeps `npm start` independent of cwd.
dotenv.config({ path: new URL('../.env', import.meta.url).pathname });
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
  tripsRoutesModule,
  uploadRoutesModule
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
	import('./routes/tripsRoutes.js'),
  import('./routes/uploadRoutes.js')
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
const uploadRoutes = uploadRoutesModule.default;

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
app.use("/uploads", express.static(UPLOAD_DIR, {
	setHeaders:response => response.setHeader("Cross-Origin-Resource-Policy","cross-origin")
}));

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
app.use("/api/uploads", uploadRoutes);

// lightweight API root/status endpoint so visiting /api returns useful info
app.get('/api', (req, res) => {
	res.json({
		status: 'ok',
		api: '/api',
		message: 'Travel App API is ready.'
	});
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`🚀 Server running on port ${PORT} (CORS allowed: ${corsOrigin})`));
