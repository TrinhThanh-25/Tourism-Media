import express from "express";
import { addTransaction } from "../controllers/pointsController.js";
import { authenticateJWT, requireAdmin } from "../middleware/auth.js";
import { validateSchema } from "../middleware/validate.js";
import { pointTransactionSchema } from "../validators/resources.js";

const router = express.Router();
router.post("/transactions", authenticateJWT, requireAdmin, validateSchema(pointTransactionSchema), addTransaction);
export default router;
