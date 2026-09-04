import express from "express";
import rateLimit from "express-rate-limit";
import { chat } from "../controllers/aiController.js";
import { authenticateJWT } from "../middleware/auth.js";
import { validateSchema } from "../middleware/validate.js";
import { aiChatSchema } from "../validators/ai.js";

const router = express.Router();
const aiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: Number(process.env.AI_RATE_LIMIT_MAX) || 20,
  standardHeaders: true,
  legacyHeaders: false
});

router.post("/chat", authenticateJWT, aiLimiter, validateSchema(aiChatSchema), chat);

export default router;
