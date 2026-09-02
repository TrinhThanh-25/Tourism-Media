import bcrypt from "bcryptjs";
import { all, get, run } from "../db/queries.js";
import { BCRYPT_ROUNDS } from "../config/auth.js";
import { buildChallengeProgress } from "./challengeController.js";

export const updateUserProfile = async (req, res) => {
  const allowed = ["username", "email", "avatar_url", "dob", "gender", "phone"];
  const entries = allowed.filter(key => req.body[key] !== undefined).map(key => [key, req.body[key]]);
  if (!entries.length) return res.status(400).json({ error: "No updatable fields provided" });
  try {
    await run(`UPDATE users SET ${entries.map(([key]) => `${key}=?`).join(",")} WHERE id=?`,
      [...entries.map(([,value]) => value),req.user.id]);
    const user = await get(
      `SELECT id,username,email,role,total_point,avatar_url AS avatar,dob,gender,phone
       FROM users WHERE id=?`, [req.user.id]
    );
    res.json({ message:"Profile updated",user });
  } catch (error) {
    res.status(/UNIQUE constraint/i.test(error.message) ? 409 : 500)
      .json({ error:/UNIQUE constraint/i.test(error.message) ? "Username or email already exists" : "Profile update failed" });
  }
};

export const updateUserPassword = async (req, res) => {
  try {
    const user = await get("SELECT password FROM users WHERE id=?", [req.user.id]);
    if (!user) return res.status(404).json({ error:"User not found" });
    const isHash = /^\$2[aby]\$/.test(user.password || "");
    const valid = isHash ? await bcrypt.compare(req.body.old_password,user.password) : req.body.old_password === user.password;
    if (!valid) return res.status(400).json({ error:"Old password is incorrect" });
    const passwordHash = await bcrypt.hash(req.body.new_password,BCRYPT_ROUNDS);
    await run("UPDATE users SET password=? WHERE id=?", [passwordHash,req.user.id]);
    res.json({ message:"Password updated" });
  } catch { res.status(500).json({ error:"Password update failed" }); }
};

export const getCheckedInLocation = async (req, res) => {
  try {
    res.json(await all(
      `SELECT l.*,ul.checked_in_at FROM user_location ul JOIN locations l ON l.id=ul.location_id
       WHERE ul.user_id=? ORDER BY ul.checked_in_at DESC`, [req.user.id]
    ));
  } catch { res.status(500).json({ error:"Could not load check-ins" }); }
};

export const checkInLocation = async (req, res) => {
  try {
    const location = await get("SELECT id FROM locations WHERE id=?", [req.body.location_id]);
    if (!location) return res.status(404).json({error:"Location not found"});
    const result = await run("INSERT INTO user_location (user_id,location_id) VALUES (?,?)", [req.user.id,location.id]);
    const checkin = await get("SELECT id,location_id,checked_in_at FROM user_location WHERE id=?", [result.lastID]);
    res.status(201).json({ message:"Location checked in",checkin });
  } catch { res.status(500).json({ error:"Check-in failed" }); }
};

export const getUserChallenges = async (req, res) => {
  try {
    const challenges = await all(
      `SELECT c.*,COALESCE(uc.status,'not_started') AS status,COALESCE(uc.progress,0) AS progress,
       uc.joined_at,uc.completed_at FROM challenges c LEFT JOIN user_challenge uc
       ON c.id=uc.challenge_id AND uc.user_id=? ORDER BY c.id`, [req.user.id]
    );
    res.json(await Promise.all(challenges.map(async challenge => {
      const result = await buildChallengeProgress(challenge,req.user.id);
      return {
        ...result,
        status: challenge.status === "claimed" ? "claimed"
          : result.joined && result.eligible ? "eligible" : challenge.status
      };
    })));
  } catch { res.status(500).json({ error:"Could not load challenges" }); }
};
