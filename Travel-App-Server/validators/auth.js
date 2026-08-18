import Joi from 'joi';

export const registerSchema = Joi.object({
  username: Joi.string().min(3).max(30).required(),
  email: Joi.string().email().required(),
  password: Joi.string().min(8).max(128).required()
});

export const loginSchema = Joi.object({
  email: Joi.string().email().required(),
  password: Joi.string().min(8).max(128).required()
});

export const refreshTokenSchema = Joi.object({
  refreshToken: Joi.string().hex().length(96).required()
});

export const logoutSchema = Joi.object({
  refreshToken: Joi.string().hex().length(96).optional()
});

export const forgotPasswordSchema = Joi.object({
  email: Joi.string().email().required(),
  new_password: Joi.string().min(8).max(128).required()
});
