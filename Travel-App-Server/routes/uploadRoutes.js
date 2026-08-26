import express from "express";
import { uploadImage } from "../controllers/uploadController.js";
import { authenticateJWT } from "../middleware/auth.js";

const router = express.Router();
router.post("/images", authenticateJWT,
  express.raw({type:["image/jpeg","image/png","image/webp"],limit:"8mb"}), uploadImage);
export default router;
