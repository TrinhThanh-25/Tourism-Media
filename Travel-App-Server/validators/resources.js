import Joi from "joi";

const nullableText = Joi.string().max(5000).allow("", null);
const nullableUrl = Joi.string().uri().allow("", null);
const isoDate = Joi.string().isoDate().allow(null);

export const locationCreateSchema = Joi.object({
  name: Joi.string().trim().min(1).max(200).required(),
  category: Joi.string().max(100).allow("", null), type: Joi.string().max(100).allow("", null),
  price: Joi.number().min(0).default(0), image_url: nullableUrl, description: nullableText,
  latitude: Joi.number().min(-90).max(90).allow(null), longitude: Joi.number().min(-180).max(180).allow(null),
  address: Joi.string().max(500).allow("", null), city: Joi.string().max(100).allow("", null),
  opening_hours: Joi.string().max(100).allow("", null), closing_hours: Joi.string().max(100).allow("", null),
  qr_code: Joi.string().max(500).allow("", null), key_highlights: nullableText
});
export const locationUpdateSchema = locationCreateSchema.fork("name", field => field.optional()).min(1);
export const locationQuerySchema = Joi.object({
  q: Joi.string().max(200).allow(""), category: Joi.string().max(100), type: Joi.string().max(100),
  min_price: Joi.number().min(0), max_price: Joi.number().min(0),
  sort_by: Joi.string().valid("name-asc","name-desc","price-asc","price-desc","rating-asc","rating-desc","review_count-asc","review_count-desc")
});
export const nearbyQuerySchema = Joi.object({
  lat: Joi.number().min(-90).max(90).required(), lon: Joi.number().min(-180).max(180).required(),
  radius: Joi.number().positive().max(500).default(5), limit: Joi.number().integer().min(1).max(100).default(20),
  w_distance: Joi.number().min(0).default(0.6), w_rating: Joi.number().min(0).default(0.4)
});

const tripLocationSchema = Joi.object({
  location_id: Joi.number().integer().positive(), id: Joi.number().integer().positive(),
  order_index: Joi.number().integer().min(0), day: Joi.number().integer().min(1).allow(null),
  time: Joi.string().max(20).allow("", null)
}).or("location_id", "id");
export const tripCreateSchema = Joi.object({
  title: Joi.string().trim().min(1).max(200).required(), description: nullableText,
  estimate_price: Joi.number().min(0).allow(null), total_time: Joi.number().min(0).allow(null),
  url_image: nullableUrl, key_highlight: nullableText,
  locations: Joi.array().items(tripLocationSchema).default([])
});
export const tripUpdateSchema = tripCreateSchema.fork("title", field => field.optional()).min(1);

const challengeTypes = ["action", "checkin", "collection", "content", "level"];
export const challengeCreateSchema = Joi.object({
  name: Joi.string().trim().min(1).max(200).required(), description: nullableText,
  start_date: isoDate, end_date: isoDate, reward_point: Joi.number().integer().min(0).default(0),
  challenge_type: Joi.string().valid(...challengeTypes).required(), criteria: Joi.object().default({}),
  required_checkins: Joi.number().integer().min(0).default(0),
  location_ids: Joi.array().items(Joi.number().integer().positive()).unique().default([]),
  reward_ids: Joi.array().items(Joi.number().integer().positive()).unique().default([])
});
export const challengeUpdateSchema = challengeCreateSchema.fork(["name","challenge_type"], field => field.optional()).min(1);
export const manualProgressSchema = Joi.object({ user_id:Joi.number().integer().positive().required(), progress:Joi.number().integer().min(0).required() });
export const activitySchema = Joi.object({
  user_id:Joi.number().integer().positive().required(), type:Joi.string().max(100).required(),
  target_id:Joi.number().integer().positive().allow(null), meta:Joi.object().allow(null)
});

export const rewardCreateSchema = Joi.object({
  name:Joi.string().trim().min(1).max(200).required(), start_date:isoDate, end_date:isoDate,
  description:nullableText, cost:Joi.number().integer().min(0).default(0), expires_at:isoDate,
  point_reward:Joi.number().integer().min(0).default(0), max_uses:Joi.number().integer().positive().allow(null),
  per_user_limit:Joi.number().integer().positive().default(1), percent:Joi.number().min(0).max(100).default(0)
});
export const rewardUpdateSchema = rewardCreateSchema.fork("name", field => field.optional()).min(1);
export const grantVoucherSchema = Joi.object({
  user_id:Joi.number().integer().positive().required(), reward_id:Joi.number().integer().positive().required(), expires_at:isoDate
});
export const locationImageCreateSchema = Joi.object({
  url:Joi.string().uri(), url_image:Joi.string().uri(), sort_order:Joi.number().integer().min(0).default(0),
  caption:Joi.string().max(1000).allow("",null)
}).or("url","url_image");
export const locationImageUpdateSchema = Joi.object({
  url:Joi.string().uri(), url_image:Joi.string().uri(), sort_order:Joi.number().integer().min(0),
  caption:Joi.string().max(1000).allow("",null)
}).min(1);
export const pointTransactionSchema = Joi.object({
  user_id:Joi.number().integer().positive().required(), points:Joi.number().integer().invalid(0).required(),
  type:Joi.string().valid("credit","debit").required(), description:Joi.string().max(1000).allow("",null)
}).custom((value, helpers) => {
  if (value.type === "credit" && value.points < 0) return helpers.error("any.invalid");
  if (value.type === "debit" && value.points > 0) return helpers.error("any.invalid");
  return value;
}, "point sign validation");
