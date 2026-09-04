import Joi from "joi";

const chatTurnSchema = Joi.object({
  role: Joi.string().valid("user", "assistant").required(),
  content: Joi.string().trim().min(1).max(2000).required()
});

export const aiChatSchema = Joi.object({
  message: Joi.string().trim().min(1).max(1000).required(),
  history: Joi.array().items(chatTurnSchema).max(12).default([])
});
