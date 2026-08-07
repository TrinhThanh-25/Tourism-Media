import express from 'express';
import { editReview, createReview, listReviewsForLocation, deleteReview } from '../controllers/locationReviewController.js';
import { authenticateJWT } from '../middleware/auth.js';
import { validateSchema } from '../middleware/validate.js';
import Joi from 'joi';

const router = express.Router();

const createSchema = Joi.object({ location_id: Joi.number().integer().positive().required(), rating: Joi.number().integer().min(1).max(5).required(), comment: Joi.string().max(2000).allow('', null) });
const updateSchema = Joi.object({ rating: Joi.number().integer().min(1).max(5).required(), comment: Joi.string().max(2000).allow('', null) });

router.post('/', authenticateJWT, validateSchema(createSchema), createReview);
router.get('/location/:locationId', listReviewsForLocation);
router.delete('/:id', authenticateJWT, deleteReview);
router.put('/:id', authenticateJWT, validateSchema(updateSchema), editReview);

export default router;
