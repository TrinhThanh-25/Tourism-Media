import express from 'express';
import { getUserTrip, listTrips, getTrip, createTrip, updateTrip, deleteTrip, getFavoriteTrips, addFavoriteTrip, removeFavoriteTrip, publishTrip, unpublishTrip } from '../controllers/tripsController.js';
import { authenticateJWT, optionalJWT } from '../middleware/auth.js';
import { validateSchema } from '../middleware/validate.js';
import { tripCreateSchema, tripUpdateSchema } from '../validators/resources.js';

const router = express.Router();

// Public trip listing
router.get('/', optionalJWT, listTrips);

// User-scoped trip listing and favorites (define before :id)
router.get('/me', authenticateJWT, getUserTrip);
router.get('/me/favorites', authenticateJWT, getFavoriteTrips);

// Trip favorites by id
router.post('/:id/favorite', authenticateJWT, addFavoriteTrip);
router.delete('/:id/favorite', authenticateJWT, removeFavoriteTrip);

// Trip CRUD and publish/unpublish by id
router.get('/:id', optionalJWT, getTrip);
router.post('/', authenticateJWT, validateSchema(tripCreateSchema), createTrip);
router.put('/:id', authenticateJWT, validateSchema(tripUpdateSchema), updateTrip);
router.delete('/:id', authenticateJWT, deleteTrip);
router.post('/:id/publish', authenticateJWT, publishTrip);
router.post('/:id/unpublish', authenticateJWT, unpublishTrip);

export default router;
