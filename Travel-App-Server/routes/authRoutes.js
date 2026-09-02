import express from 'express';
import { register, login, logout, refresh, forgotPassword } from '../controllers/authController.js';
import rateLimit from 'express-rate-limit';
import { validateSchema } from '../middleware/validate.js';
import { registerSchema, loginSchema, refreshTokenSchema, logoutSchema, forgotPasswordSchema } from '../validators/auth.js';

const router = express.Router();

// stricter rate limit for auth endpoints to mitigate brute-force
const authLimiter = rateLimit({ windowMs: 15 * 60 * 1000, max: 30, standardHeaders: true, legacyHeaders: false });

router.post('/register', authLimiter, validateSchema(registerSchema), register);
router.post('/login', authLimiter, validateSchema(loginSchema), login);
router.post('/logout', validateSchema(logoutSchema), logout);
router.post('/refresh', authLimiter, validateSchema(refreshTokenSchema), refresh);
router.post('/forgot-password', authLimiter, validateSchema(forgotPasswordSchema), forgotPassword);

export default router;
