import express from "express";
import { authenticateJWT } from "../middleware/auth.js";
import { validateSchema } from "../middleware/validate.js";
import { me } from "../controllers/authController.js";
import { updateUserProfile, updateUserPassword,
  getCheckedInLocation, checkInLocation, recordLocationRead, getUserChallenges } from "../controllers/userController.js";
import { getMyPoints, listTransactionsForUser } from "../controllers/pointsController.js";
import { getEligibleCatalog, redeemReward, getUserInventory, useUserReward } from "../controllers/rewardController.js";
import { updateUserProfileSchema, updatePasswordSchema, checkInSchema } from "../validators/user.js";

const router = express.Router();
router.use(authenticateJWT);
router.get("/", me);
router.patch("/", validateSchema(updateUserProfileSchema), updateUserProfile);
router.post("/password", validateSchema(updatePasswordSchema), updateUserPassword);
router.get("/locations", getCheckedInLocation);
router.post("/locations", validateSchema(checkInSchema), checkInLocation);
router.post("/activity/location-read", validateSchema(checkInSchema), recordLocationRead);
router.get("/challenges", getUserChallenges);
router.get("/points", getMyPoints);
router.get("/point-transactions", listTransactionsForUser);
router.get("/rewards", getEligibleCatalog);
router.post("/rewards/:rewardId/redeem", redeemReward);
router.get("/vouchers", getUserInventory);
router.post("/vouchers/:voucherId/use", useUserReward);
export default router;
