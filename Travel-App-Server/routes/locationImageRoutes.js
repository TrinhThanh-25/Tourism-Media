import express from 'express';
import { listLocationImages, addLocationImage, updateLocationImage, deleteLocationImage } from '../controllers/locationImageController.js';
import { authenticateJWT, requireAdmin } from '../middleware/auth.js';
import { validateSchema } from '../middleware/validate.js';
import { locationImageCreateSchema, locationImageUpdateSchema } from '../validators/resources.js';

const router = express.Router({ mergeParams: true });

router.get('/', listLocationImages);
router.post('/', authenticateJWT, requireAdmin, validateSchema(locationImageCreateSchema), addLocationImage);
router.patch('/:imageId', authenticateJWT, requireAdmin, validateSchema(locationImageUpdateSchema), updateLocationImage);
router.delete('/:imageId', authenticateJWT, requireAdmin, deleteLocationImage);

export default router;
