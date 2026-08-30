import express from "express";
import { getAllRewards, getRewardById, addReward, updateReward, deleteReward, addUserReward, deleteUserReward } from "../controllers/rewardController.js";
import { authenticateJWT, requireAdmin } from "../middleware/auth.js";
import { validateSchema } from "../middleware/validate.js";
import { rewardCreateSchema, rewardUpdateSchema, grantVoucherSchema } from "../validators/resources.js";

const router = express.Router();
router.get("/", getAllRewards);
router.get("/:id", getRewardById);
router.post("/", authenticateJWT, requireAdmin, validateSchema(rewardCreateSchema), addReward);
router.put("/:id", authenticateJWT, requireAdmin, validateSchema(rewardUpdateSchema), updateReward);
router.delete("/:id", authenticateJWT, requireAdmin, deleteReward);
router.post("/vouchers", authenticateJWT, requireAdmin, validateSchema(grantVoucherSchema), addUserReward);
router.delete("/vouchers/:voucherId", authenticateJWT, requireAdmin, deleteUserReward);
export default router;
