import express from "express";
import { getAllLocations, addLocation, updateLocation, deleteLocation, getLocationById, getFavoriteLocations, addFavoriteLocation, removeFavoriteLocation, nearbyLocations } from "../controllers/locationController.js";
import { authenticateJWT, optionalJWT, requireAdmin } from '../middleware/auth.js';
import { validateSchema } from "../middleware/validate.js";
import { locationCreateSchema, locationUpdateSchema, locationQuerySchema, nearbyQuerySchema } from "../validators/resources.js";

const router = express.Router();

// Collection + special endpoints first
router.get("/", optionalJWT, validateSchema(locationQuerySchema,"query"), getAllLocations);
router.get("/nearby", optionalJWT, validateSchema(nearbyQuerySchema,"query"), nearbyLocations);
// Authenticated favorites endpoints (user-scoped)
router.get("/me/favorites", authenticateJWT, getFavoriteLocations);
router.post("/:id/favorite", authenticateJWT, addFavoriteLocation);
router.delete("/:id/favorite", authenticateJWT, removeFavoriteLocation);
// Single-location resource by id last
router.get("/:id", optionalJWT, getLocationById);
router.post("/", authenticateJWT, requireAdmin, validateSchema(locationCreateSchema), addLocation);
router.put("/:id", authenticateJWT, requireAdmin, validateSchema(locationUpdateSchema), updateLocation);
router.delete("/:id", authenticateJWT, requireAdmin, deleteLocation);

export default router;
