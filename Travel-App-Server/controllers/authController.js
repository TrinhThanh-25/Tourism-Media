import jwt from "jsonwebtoken";
import crypto from "crypto";
import bcrypt from "bcryptjs";
import { get, run, transaction } from "../db/queries.js";
import { ACCESS_EXPIRES_IN, BCRYPT_ROUNDS, JWT_SECRET, REFRESH_TTL_DAYS } from "../config/auth.js";

const hashToken = token => crypto.createHash("sha256").update(token).digest("hex");
const createRefreshToken = () => crypto.randomBytes(48).toString("hex");
const signAccessToken = user => jwt.sign(
  { id: user.id, username: user.username, role: user.role || "user" },
  JWT_SECRET,
  { expiresIn: ACCESS_EXPIRES_IN }
);

async function storeRefreshToken(userId, rawToken, queries = { run }) {
  return queries.run(
    `INSERT INTO user_refresh_tokens (user_id, token, expires_at)
     VALUES (?, ?, strftime('%Y-%m-%dT%H:%M:%fZ','now',?))`,
    [userId, hashToken(rawToken), `+${REFRESH_TTL_DAYS} days`]
  );
}

export const register = async (req, res) => {
  try {
    const { username, email, password } = req.body;
    const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS);
    const refreshToken = createRefreshToken();
    const user = await transaction(async queries => {
      const result = await queries.run(
        "INSERT INTO users (username,email,password,role) VALUES (?,?,?,'user')",
        [username,email.toLowerCase(),passwordHash]
      );
      const created={id:result.lastID,username,role:"user"};
      await storeRefreshToken(created.id,refreshToken,queries);
      return created;
    });
    res.status(201).json({ token: signAccessToken(user), refreshToken, userId: user.id, username });
  } catch (error) {
    const status = /UNIQUE constraint/i.test(error.message) ? 409 : 500;
    res.status(status).json({ error: status === 409 ? "Username or email already exists" : "Registration failed" });
  }
};

export const login = async (req, res) => {
  try {
    const { email, password } = req.body;
    const user = await get("SELECT * FROM users WHERE email = ?", [email.toLowerCase()]);
    if (!user) return res.status(401).json({ error: "Invalid credentials" });

    const isHash = /^\$2[aby]\$/.test(user.password || "");
    const valid = isHash ? await bcrypt.compare(password, user.password) : password === user.password;
    if (!valid) return res.status(401).json({ error: "Invalid credentials" });
    if (!isHash) {
      const upgradedHash = await bcrypt.hash(password, BCRYPT_ROUNDS);
      await run("UPDATE users SET password = ? WHERE id = ?", [upgradedHash, user.id]);
    }

    const refreshToken = createRefreshToken();
    await storeRefreshToken(user.id, refreshToken);
    res.json({
      token: signAccessToken(user), refreshToken, userId: user.id,
      username: user.username, role: user.role || "user", points: user.total_point || 0
    });
  } catch {
    res.status(500).json({ error: "Login failed" });
  }
};

export const logout = async (req, res) => {
  const { refreshToken } = req.body || {};
  if (refreshToken) {
    await run("UPDATE user_refresh_tokens SET revoked = 1 WHERE token IN (?, ?)",
      [hashToken(refreshToken), refreshToken]).catch(() => {});
  }
  res.json({ message: "Logout successful" });
};

// Demo-friendly password reset. A production app must verify an emailed OTP or
// signed reset link before accepting the new password.
export const forgotPassword = async (req, res) => {
  try {
    const email = req.body.email.toLowerCase();
    const user = await get("SELECT id FROM users WHERE lower(email) = ?", [email]);
    if (!user) return res.status(404).json({ error: "Không tìm thấy tài khoản với email này" });
    const passwordHash = await bcrypt.hash(req.body.new_password, BCRYPT_ROUNDS);
    await transaction(async queries => {
      await queries.run("UPDATE users SET password = ? WHERE id = ?", [passwordHash, user.id]);
      await queries.run("UPDATE user_refresh_tokens SET revoked = 1 WHERE user_id = ?", [user.id]);
    });
    res.json({ message: "Mật khẩu đã được đặt lại" });
  } catch {
    res.status(500).json({ error: "Không thể đặt lại mật khẩu" });
  }
};

export const me = async (req, res) => {
  try {
    const user = await get(
      `SELECT id, username, email, role, total_point, avatar_url AS avatar, dob, gender, phone
       FROM users WHERE id = ?`, [req.user.id]
    );
    if (!user) return res.status(404).json({ error: "User not found" });
    res.json(user);
  } catch { res.status(500).json({ error: "Could not load profile" }); }
};

export const refresh = async (req, res) => {
  const { refreshToken } = req.body || {};
  if (!refreshToken) return res.status(400).json({ error: "refreshToken required" });
  try {
    const digest = hashToken(refreshToken);
    const result = await transaction(async queries => {
      const stored = await queries.get(
        `SELECT * FROM user_refresh_tokens WHERE token IN (?, ?) AND revoked = 0
         AND (expires_at IS NULL OR julianday(expires_at) > julianday('now'))`, [digest, refreshToken]
      );
      if (!stored) throw Object.assign(new Error("Invalid refresh token"), { status: 401 });
      const user = await queries.get("SELECT id, username, role FROM users WHERE id = ?", [stored.user_id]);
      if (!user) throw Object.assign(new Error("User not found"), { status: 401 });
      const nextRefreshToken = createRefreshToken();
      await queries.run("UPDATE user_refresh_tokens SET revoked = 1 WHERE id = ?", [stored.id]);
      await storeRefreshToken(user.id, nextRefreshToken, queries);
      return { token: signAccessToken(user), refreshToken: nextRefreshToken };
    });
    res.json(result);
  } catch (error) {
    res.status(error.status || 500).json({ error: error.status ? error.message : "Token refresh failed" });
  }
};
