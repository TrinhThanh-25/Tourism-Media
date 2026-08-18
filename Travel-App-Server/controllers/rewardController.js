import crypto from "crypto";
import { all, get, run, transaction } from "../db/queries.js";

const voucherCode = () => crypto.randomBytes(6).toString("hex").toUpperCase();

export const getAllRewards = async (_req, res) => {
  try { res.json(await all("SELECT * FROM rewards ORDER BY id")); }
  catch { res.status(500).json({ error: "Could not load rewards" }); }
};

export const getRewardById = async (req, res) => {
  try {
    const reward = await get("SELECT * FROM rewards WHERE id = ?", [req.params.id]);
    if (!reward) return res.status(404).json({ error: "Reward not found" });
    res.json(reward);
  } catch { res.status(500).json({ error: "Could not load reward" }); }
};

export const addReward = async (req, res) => {
  const { name, start_date, end_date, description, cost = 0, expires_at,
    point_reward = 0, max_uses, per_user_limit = 1, percent = 0 } = req.body || {};
  if (!name || cost < 0) return res.status(400).json({ error: "Valid name and cost are required" });
  try {
    const result = await run(
      `INSERT INTO rewards (name,start_date,end_date,description,cost,expires_at,
       point_reward,max_uses,per_user_limit,percent) VALUES (?,?,?,?,?,?,?,?,?,?)`,
      [name,start_date||null,end_date||null,description||null,cost,expires_at||null,
        point_reward,max_uses||null,per_user_limit,percent]
    );
    res.status(201).json({ id: result.lastID, name });
  } catch { res.status(500).json({ error: "Could not create reward" }); }
};

const rewardFields = ["name","start_date","end_date","description","cost","expires_at","point_reward","max_uses","per_user_limit","percent"];

export const updateReward = async (req, res) => {
  const fields = rewardFields.filter(field => req.body[field] !== undefined);
  try {
    const result = await run(
      `UPDATE rewards SET ${fields.map(field => `${field}=?`).join(",")} WHERE id=?`,
      [...fields.map(field => req.body[field]),req.params.id]
    );
    if (!result.changes) return res.status(404).json({error:"Reward not found"});
    res.json(await get("SELECT * FROM rewards WHERE id=?", [req.params.id]));
  } catch { res.status(500).json({error:"Could not update reward"}); }
};

export const deleteReward = async (req, res) => {
  try {
    const result = await run("DELETE FROM rewards WHERE id=?", [req.params.id]);
    if (!result.changes) return res.status(404).json({error:"Reward not found"});
    res.json({message:"Reward deleted"});
  } catch { res.status(500).json({error:"Could not delete reward"}); }
};

export const getEligibleCatalog = async (req, res) => {
  try {
    const user = await get("SELECT total_point FROM users WHERE id = ?", [req.user.id]);
    const rewards = await all(
      `SELECT *, CASE WHEN cost <= ? THEN 1 ELSE 0 END AS eligible
       FROM rewards WHERE (start_date IS NULL OR julianday(start_date) <= julianday('now'))
       AND (end_date IS NULL OR julianday(end_date) >= julianday('now')) ORDER BY cost, id`,
      [user?.total_point || 0]
    );
    res.json({ points: user?.total_point || 0, rewards });
  } catch { res.status(500).json({ error: "Could not load reward catalog" }); }
};

export const redeemReward = async (req, res) => {
  const rewardId = Number(req.params.rewardId || req.body?.reward_id);
  if (!rewardId) return res.status(400).json({ error: "rewardId is required" });
  try {
    const result = await transaction(async queries => {
      const user = await queries.get("SELECT total_point FROM users WHERE id = ?", [req.user.id]);
      const reward = await queries.get("SELECT * FROM rewards WHERE id = ?", [rewardId]);
      if (!user || !reward) throw Object.assign(new Error("User or reward not found"), { status: 404 });
      const now = Date.now();
      if (reward.start_date && new Date(reward.start_date).getTime() > now)
        throw Object.assign(new Error("Reward is not active"), { status: 409 });
      if (reward.end_date && new Date(reward.end_date).getTime() < now)
        throw Object.assign(new Error("Reward has expired"), { status: 409 });
      const owned = await queries.get("SELECT COUNT(*) AS count FROM user_reward WHERE user_id=? AND reward_id=?", [req.user.id, rewardId]);
      if (owned.count >= (reward.per_user_limit || 1))
        throw Object.assign(new Error("Per-user reward limit reached"), { status: 409 });
      const issued = await queries.get("SELECT COUNT(*) AS count FROM user_reward WHERE reward_id=?",[rewardId]);
      if(reward.max_uses!=null&&issued.count>=reward.max_uses)
        throw Object.assign(new Error("Reward usage limit reached"),{status:409});
      if ((user.total_point || 0) < (reward.cost || 0))
        throw Object.assign(new Error("Insufficient points"), { status: 409 });
      const code = voucherCode();
      const voucher = await queries.run(
        `INSERT INTO user_reward (user_id,reward_id,code,status,expires_at,created_at)
         VALUES (?,?,?,'active',?,strftime('%Y-%m-%dT%H:%M:%fZ','now'))`, [req.user.id,rewardId,code,reward.expires_at||null]
      );
      await queries.run("UPDATE users SET total_point=total_point-? WHERE id=?", [reward.cost||0,req.user.id]);
      await queries.run(
        `INSERT INTO points_transactions (user_id,reward_id,points,type,description)
         VALUES (?,?,?,'debit',?)`, [req.user.id,rewardId,-(reward.cost||0),`Redeemed ${reward.name}`]
      );
      return { voucherId: voucher.lastID, code, remainingPoints: (user.total_point||0)-(reward.cost||0) };
    });
    res.status(201).json(result);
  } catch (error) {
    res.status(error.status || 500).json({ error: error.status ? error.message : "Reward redemption failed" });
  }
};

export const getUserInventory = async (req, res) => {
  try {
    res.json(await all(
      `SELECT ur.id AS voucher_id, ur.code,
       CASE WHEN ur.status='active' AND ur.expires_at IS NOT NULL
         AND julianday(ur.expires_at) <= julianday('now') THEN 'expired'
         ELSE ur.status END AS status,
       ur.used_at, ur.expires_at,
       ur.created_at, r.id AS reward_id, r.name, r.description, r.percent
       FROM user_reward ur JOIN rewards r ON r.id=ur.reward_id
       WHERE ur.user_id=? ORDER BY ur.created_at DESC`, [req.user.id]
    ));
  } catch { res.status(500).json({ error: "Could not load vouchers" }); }
};

export const useUserReward = async (req, res) => {
  try {
    const result = await transaction(async queries => {
      const voucher = await queries.get("SELECT ur.*,r.name,r.point_reward FROM user_reward ur JOIN rewards r ON r.id=ur.reward_id WHERE ur.id=? AND ur.user_id=?", [req.params.voucherId,req.user.id]);
      if (!voucher) throw Object.assign(new Error("Voucher not found"), { status: 404 });
      if (voucher.status !== "active") throw Object.assign(new Error("Voucher is not active"), { status: 409 });
      if (voucher.expires_at && new Date(voucher.expires_at).getTime() <= Date.now())
        throw Object.assign(new Error("Voucher has expired"), { status: 409 });
      await queries.run("UPDATE user_reward SET status='used',used_at=strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE id=?", [voucher.id]);
      await queries.run("INSERT INTO user_activity (user_id,type,meta_json) VALUES (?,'reward_redeemed',?)",
        [req.user.id,JSON.stringify({ voucher_id:voucher.id,reward_id:voucher.reward_id })]);
      const pointReward=Number(voucher.point_reward)||0;
      if(pointReward){
        await queries.run("UPDATE users SET total_point=total_point+? WHERE id=?",[pointReward,req.user.id]);
        await queries.run("INSERT INTO points_transactions (user_id,reward_id,points,type,description) VALUES (?,?,?,'credit',?)",[req.user.id,voucher.reward_id,pointReward,`Used ${voucher.name}`]);
      }
      const user=await queries.get("SELECT total_point FROM users WHERE id=?",[req.user.id]);
      return { message:"Voucher used",voucherId:voucher.id,pointsAwarded:pointReward,balance:user.total_point };
    });
    res.json(result);
  } catch (error) { res.status(error.status || 500).json({ error:error.status ? error.message : "Could not use voucher" }); }
};

export const addUserReward = async (req, res) => {
  const { user_id, reward_id, expires_at } = req.body || {};
  if (!Number.isInteger(user_id) || !Number.isInteger(reward_id)) return res.status(400).json({ error:"user_id and reward_id required" });
  try {
    const code = voucherCode();
    const result = await run(
      `INSERT INTO user_reward (user_id,reward_id,code,status,expires_at,created_at)
       VALUES (?,?,?,'active',?,strftime('%Y-%m-%dT%H:%M:%fZ','now'))`, [user_id,reward_id,code,expires_at||null]
    );
    res.status(201).json({ voucherId:result.lastID,code });
  } catch { res.status(500).json({ error:"Could not grant voucher" }); }
};

export const deleteUserReward = async (req, res) => {
  try {
    const result = await run("DELETE FROM user_reward WHERE id=?", [req.params.voucherId]);
    if (!result.changes) return res.status(404).json({ error:"Voucher not found" });
    res.json({ message:"Voucher deleted" });
  } catch { res.status(500).json({ error:"Could not delete voucher" }); }
};
