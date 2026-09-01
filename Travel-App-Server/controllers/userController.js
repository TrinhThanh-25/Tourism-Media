import { all, get, run } from "../db/queries.js";
import { BCRYPT_ROUNDS } from "../config/auth.js";
import { hashPassword, passwordMatches } from "../db/passwords.js";
import { buildChallengesProgress } from "./challengeController.js";

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
    const valid = await passwordMatches(req.body.old_password,user.password);
    if (!valid) return res.status(400).json({ error:"Old password is incorrect" });
    const passwordHash = await hashPassword(req.body.new_password,BCRYPT_ROUNDS);
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
    // One check-in per user/location/day so repeated taps cannot inflate challenge progress.
    const today = await get(
      `SELECT id,location_id,checked_in_at FROM user_location
       WHERE user_id=? AND location_id=? AND date(checked_in_at)=date('now')
       ORDER BY checked_in_at DESC LIMIT 1`,
      [req.user.id,location.id]
    );
    if (today) {
      return res.json({ message:"You already checked in here today",checkin:today,duplicate:true });
    }
    const result = await run("INSERT INTO user_location (user_id,location_id) VALUES (?,?)", [req.user.id,location.id]);
    const checkin = await get("SELECT id,location_id,checked_in_at FROM user_location WHERE id=?", [result.lastID]);
    res.status(201).json({ message:"Location checked in",checkin,duplicate:false });
  } catch { res.status(500).json({ error:"Check-in failed" }); }
};

export const recordLocationRead = async (req, res) => {
  try {
    const location = await get("SELECT id FROM locations WHERE id=?", [req.body.location_id]);
    if (!location) return res.status(404).json({error:"Location not found"});
    await run(
      `INSERT INTO user_activity (user_id,type,target_id,created_at)
       VALUES (?,'location_info_read',?,strftime('%Y-%m-%dT%H:%M:%fZ','now'))`,
      [req.user.id,location.id]
    );
    res.status(201).json({message:"Location reading recorded"});
  } catch { res.status(500).json({error:"Could not record location reading"}); }
};

export const getUserChallenges = async (req, res) => {
  try {
    const challenges = await all(
      `SELECT c.*,COALESCE(uc.status,'not_started') AS status,COALESCE(uc.progress,0) AS progress,
       uc.joined_at,uc.completed_at FROM challenges c LEFT JOIN user_challenge uc
       ON c.id=uc.challenge_id AND uc.user_id=? ORDER BY c.id`, [req.user.id]
    );
    const progressItems = await buildChallengesProgress(challenges,req.user.id);
    res.json(progressItems.map((result,index) => {
      const challenge = challenges[index];
      return {
        ...result,
        status: challenge.status === "claimed" ? "claimed"
          : result.joined && result.eligible ? "eligible" : challenge.status
      };
    }));
  } catch { res.status(500).json({ error:"Could not load challenges" }); }
};
