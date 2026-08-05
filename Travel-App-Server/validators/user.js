import Joi from 'joi';

export const updateUserProfileSchema = Joi.object({
  username: Joi.string().min(3).max(30).optional(),
  email: Joi.string().email().optional(),
  avatar_url: Joi.string().uri().optional(),
  dob: Joi.string().optional(),
  gender: Joi.string().valid('male','female','other').optional(),
  phone: Joi.string().optional()
});

export const updatePasswordSchema = Joi.object({
  old_password: Joi.string().min(6).max(128).required(),
  new_password: Joi.string().min(8).max(128).required()
});

export const checkInSchema = Joi.object({
  location_id: Joi.number().integer().required()
});
