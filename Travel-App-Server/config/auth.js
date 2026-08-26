const configuredSecret = process.env.JWT_SECRET?.trim();
if (!configuredSecret || configuredSecret === "replace-with-a-long-random-secret") {
  throw new Error("JWT_SECRET is required. Copy .env.example to .env and set a private value.");
}

export const JWT_SECRET = configuredSecret;
export const ACCESS_EXPIRES_IN = process.env.JWT_EXPIRES_IN || "1h";
export const REFRESH_TTL_DAYS = Number(process.env.REFRESH_TOKEN_TTL_DAYS) || 30;
export const BCRYPT_ROUNDS = Number(process.env.BCRYPT_ROUNDS) || 12;
