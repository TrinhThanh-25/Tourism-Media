const production = process.env.NODE_ENV === "production";

if (production && !process.env.JWT_SECRET) {
  throw new Error("JWT_SECRET is required in production");
}

export const JWT_SECRET = process.env.JWT_SECRET || "dev-secret";
export const ACCESS_EXPIRES_IN = process.env.JWT_EXPIRES_IN || "1h";
export const REFRESH_TTL_DAYS = Number(process.env.REFRESH_TOKEN_TTL_DAYS) || 30;
export const BCRYPT_ROUNDS = Number(process.env.BCRYPT_ROUNDS) || 12;
