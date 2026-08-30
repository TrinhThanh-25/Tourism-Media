import express from "express";
import {
  getAllChallenges, addChallenge, updateChallenge, deleteChallenge, getChallengeById, joinChallenge,
  completeChallenge, getChallengeProgress,
  setManualProgress, logChallengeActivity, getChallengeLocations, getChallengeRewards
} from "../controllers/challengeController.js";
import { authenticateJWT, requireAdmin } from "../middleware/auth.js";
import { validateSchema } from "../middleware/validate.js";
import { challengeCreateSchema, challengeUpdateSchema, manualProgressSchema, activitySchema } from "../validators/resources.js";

const router = express.Router();
router.get("/", getAllChallenges);
router.post("/", authenticateJWT, requireAdmin, validateSchema(challengeCreateSchema), addChallenge);
router.get("/:id", getChallengeById);
router.get("/:id/locations", getChallengeLocations);
router.get("/:id/rewards", getChallengeRewards);
router.post("/:id/join", authenticateJWT, joinChallenge);
router.post("/:id/complete", authenticateJWT, completeChallenge);
router.get("/:id/progress", authenticateJWT, getChallengeProgress);
router.put("/:id", authenticateJWT, requireAdmin, validateSchema(challengeUpdateSchema), updateChallenge);
router.delete("/:id", authenticateJWT, requireAdmin, deleteChallenge);
router.post("/:id/activity", authenticateJWT, requireAdmin, validateSchema(activitySchema), logChallengeActivity);
router.post("/:id/progress/manual", authenticateJWT, requireAdmin, validateSchema(manualProgressSchema), setManualProgress);
export default router;
