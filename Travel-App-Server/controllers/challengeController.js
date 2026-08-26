import crypto from "crypto";
import { all, get, run, transaction } from "../db/queries.js";

function parseCriteria(value) {
  if (!value) return {};
  if (typeof value === "object") return value;
  try { return JSON.parse(value); } catch { return {}; }
}

function targetFor(challenge) {
  const criteria = parseCriteria(challenge.criteria);
  return Number(criteria.count ?? criteria.points ?? criteria.meters ?? challenge.required_checkins ?? 0);
}

// SQLite stores timestamps both as "2026-01-01T00:00:00.000Z" and "2026-01-01 00:00:00";
// normalising both sides to the same UTC ISO string keeps string comparison valid.
const SQL_UTC = "strftime('%Y-%m-%dT%H:%M:%fZ',%s)";

function toUtcIso(value) {
  if (!value) return null;
  const text = String(value).trim().replace(" ", "T");
  const time = Date.parse(/(Z|[+-]\d{2}:?\d{2})$/.test(text) ? text : `${text}Z`);
  return Number.isNaN(time) ? null : new Date(time).toISOString();
}

// Progress only counts activity inside the challenge window, and never before the user joined.
export function progressWindow(challenge, membership) {
  const starts = [toUtcIso(challenge.start_date),toUtcIso(membership?.joined_at)].filter(Boolean);
  return {
    from:starts.length ? starts.reduce((a,b) => a > b ? a : b) : null,
    to:toUtcIso(challenge.end_date)
  };
}

function windowFilter(column, window) {
  const sql = [], params = [];
  if (window?.from) { sql.push(` AND ${SQL_UTC.replace("%s",column)} >= ?`); params.push(window.from); }
  if (window?.to) { sql.push(` AND ${SQL_UTC.replace("%s",column)} <= ?`); params.push(window.to); }
  return { sql:sql.join(""), params };
}

async function calculateProgress(challenge, userId, queries = { get }, membership = null) {
  const criteria = parseCriteria(challenge.criteria);
  const target = criteria.target;
  const locationIds = Array.isArray(criteria.location_ids)
    ? criteria.location_ids.map(Number).filter(Boolean)
    : [];
  const locationFilter = locationIds.length
    ? ` AND location_id IN (${locationIds.map(() => "?").join(",")})`
    : "";
  const window = progressWindow(challenge,membership);
  const checkinWindow = windowFilter("checked_in_at",window);
  const reviewWindow = windowFilter("created_at",window);
  const activityWindow = windowFilter("created_at",window);

  if (target === "level_points" || challenge.challenge_type === "level") {
    return (await queries.get("SELECT COALESCE(total_point,0) AS value FROM users WHERE id=?", [userId]))?.value || 0;
  }
  if (!membership) return 0;
  if (target === "distinct_locations" || challenge.challenge_type === "collection" && target !== "distinct_categories") {
    return (await queries.get(
      `SELECT COUNT(DISTINCT location_id) AS value FROM user_location WHERE user_id=?${checkinWindow.sql}`,
      [userId,...checkinWindow.params]
    ))?.value || 0;
  }
  if (target === "distinct_categories") {
    const categoryWindow = windowFilter("checkin.checked_in_at",window);
    return (await queries.get(
      `SELECT COUNT(DISTINCT location.category) AS value
       FROM user_location checkin JOIN locations location ON location.id=checkin.location_id
       WHERE checkin.user_id=?${categoryWindow.sql}`, [userId,...categoryWindow.params]
    ))?.value || 0;
  }
  if (target === "location_reviews" || target === "reviews_written") {
    return (await queries.get(
      `SELECT COUNT(*) AS value FROM location_reviews WHERE user_id=?${locationFilter}${reviewWindow.sql}`,
      [userId,...locationIds,...reviewWindow.params]
    ))?.value || 0;
  }
  if (target === "checkins" || challenge.challenge_type === "checkin") {
    // "Visit N places" counts distinct locations; "check in N times at these places"
    // counts the (already deduplicated) daily check-ins.
    const distinct = criteria.count_distinct !== undefined
      ? Boolean(criteria.count_distinct)
      : !locationIds.length || Number(criteria.count ?? 0) <= locationIds.length;
    return (await queries.get(
      `SELECT COUNT(${distinct ? "DISTINCT location_id" : "*"}) AS value
       FROM user_location WHERE user_id=?${locationFilter}${checkinWindow.sql}`,
      [userId,...locationIds,...checkinWindow.params]
    ))?.value || 0;
  }
  if (target === "distance_meters") {
    return (await queries.get(
      `SELECT COALESCE(SUM(CAST(json_extract(meta_json,'$.meters') AS INTEGER)),0) AS value
       FROM user_activity WHERE user_id=? AND type='distance_session'${activityWindow.sql}`,
      [userId,...activityWindow.params]
    ))?.value || 0;
  }
  if (target === "collect_item") {
    return (await queries.get(
      `SELECT COUNT(DISTINCT json_extract(meta_json,'$.item_key')) AS value
       FROM user_activity WHERE user_id=? AND type='collect_item'
       AND json_extract(meta_json,'$.item_key') LIKE ?${activityWindow.sql}`,
      [userId,`${criteria.item_prefix || ""}%`,...activityWindow.params]
    ))?.value || 0;
  }
  if (target === "location_info_reads") {
    return (await queries.get(
      `SELECT COUNT(DISTINCT target_id) AS value FROM user_activity
       WHERE user_id=? AND type='location_info_read'${activityWindow.sql}`,
      [userId,...activityWindow.params]
    ))?.value || 0;
  }

  const activityTypes = {
    photo_upload:"photo_upload", read_tip:"read_tip", watch_video:"watch_video",
    location_info_reads:"location_info_read", quiz_correct:"quiz_correct",
    invites_completed:"invite_completed", shares:"share", reward_redemptions:"reward_redeemed"
  };
  if (activityTypes[target]) {
    return (await queries.get(
      `SELECT COUNT(*) AS value FROM user_activity WHERE user_id=? AND type=?${activityWindow.sql}`,
      [userId,activityTypes[target],...activityWindow.params]
    ))?.value || 0;
  }
  return (await queries.get(
    "SELECT COALESCE(progress,0) AS value FROM user_challenge WHERE user_id=? AND challenge_id=?",
    [userId,challenge.id]
  ))?.value || 0;
}

function isChallengeActive(challenge, now = Date.now()) {
  return (!challenge.start_date || new Date(challenge.start_date).getTime() <= now)
    && (!challenge.end_date || new Date(challenge.end_date).getTime() >= now);
}

function progressResponse(challenge, membership, progress) {
  const target = targetFor(challenge);
  return {
    ...challenge,
    criteria:parseCriteria(challenge.criteria),
    progress,
    target,
    percent:target > 0 ? Math.min(100,Math.floor(progress / target * 100)) : 0,
    eligible:target > 0 && progress >= target,
    joined:Boolean(membership),
    status:membership?.status || "not_started",
    joined_at:membership?.joined_at || null,
    completed_at:membership?.completed_at || null,
    active:isChallengeActive(challenge)
  };
}

function inWindow(timestamp, window) {
  const value = toUtcIso(timestamp);
  if (!value) return false;
  return (!window.from || value >= window.from) && (!window.to || value <= window.to);
}

function snapshotProgress(challenge, membership, snapshot) {
  const criteria = parseCriteria(challenge.criteria);
  const target = criteria.target;
  if (target === "level_points" || challenge.challenge_type === "level") return snapshot.totalPoint;
  if (!membership) return 0;
  const window = progressWindow(challenge,membership);
  const locationIds = new Set(Array.isArray(criteria.location_ids)
    ? criteria.location_ids.map(Number).filter(Boolean) : []);
  const checkins = snapshot.checkins.filter(item => inWindow(item.checked_in_at,window)
    && (!locationIds.size || locationIds.has(Number(item.location_id))));
  const reviews = snapshot.reviews.filter(item => inWindow(item.created_at,window)
    && (!locationIds.size || locationIds.has(Number(item.location_id))));
  const activities = snapshot.activities.filter(item => inWindow(item.created_at,window));

  if (target === "distinct_locations" || challenge.challenge_type === "collection" && target !== "distinct_categories") {
    return new Set(checkins.map(item => Number(item.location_id))).size;
  }
  if (target === "distinct_categories") {
    return new Set(checkins.map(item => item.category).filter(Boolean)).size;
  }
  if (target === "location_reviews" || target === "reviews_written") return reviews.length;
  if (target === "checkins" || challenge.challenge_type === "checkin") {
    const distinct = criteria.count_distinct !== undefined
      ? Boolean(criteria.count_distinct)
      : !locationIds.size || Number(criteria.count ?? 0) <= locationIds.size;
    return distinct ? new Set(checkins.map(item => Number(item.location_id))).size : checkins.length;
  }
  if (target === "distance_meters") {
    return activities.filter(item => item.type === "distance_session")
      .reduce((sum,item) => sum + Number(parseCriteria(item.meta_json).meters || 0),0);
  }
  if (target === "collect_item") {
    return new Set(activities.filter(item => item.type === "collect_item")
      .map(item => parseCriteria(item.meta_json).item_key)
      .filter(key => key && String(key).startsWith(criteria.item_prefix || ""))).size;
  }
  if (target === "location_info_reads") {
    return new Set(activities.filter(item => item.type === "location_info_read")
      .map(item => Number(item.target_id)).filter(Boolean)).size;
  }
  const activityTypes = {
    photo_upload:"photo_upload", read_tip:"read_tip", watch_video:"watch_video",
    location_info_reads:"location_info_read", quiz_correct:"quiz_correct",
    invites_completed:"invite_completed", shares:"share", reward_redemptions:"reward_redeemed"
  };
  if (activityTypes[target]) return activities.filter(item => item.type === activityTypes[target]).length;
  return Number(membership.progress) || 0;
}

// Constant-size batch for the challenge list: four queries regardless of challenge count.
export async function buildChallengesProgress(challenges, userId) {
  const [checkins,reviews,activities,user] = await Promise.all([
    all(`SELECT checkin.location_id,checkin.checked_in_at,location.category
         FROM user_location checkin LEFT JOIN locations location ON location.id=checkin.location_id
         WHERE checkin.user_id=?`, [userId]),
    all("SELECT location_id,created_at FROM location_reviews WHERE user_id=?", [userId]),
    all("SELECT type,target_id,meta_json,created_at FROM user_activity WHERE user_id=?", [userId]),
    get("SELECT COALESCE(total_point,0) AS total_point FROM users WHERE id=?", [userId])
  ]);
  const snapshot = {checkins,reviews,activities,totalPoint:Number(user?.total_point) || 0};
  return challenges.map(challenge => {
    const joined = Boolean(challenge.joined_at
      || challenge.status && challenge.status !== "not_started");
    const membership = joined ? {
      status:challenge.status, progress:challenge.progress,
      joined_at:challenge.joined_at, completed_at:challenge.completed_at
    } : null;
    return progressResponse(challenge,membership,snapshotProgress(challenge,membership,snapshot));
  });
}

export async function buildChallengeProgress(challenge, userId, queries = { get }) {
  const membership = await queries.get(
    "SELECT status,joined_at,completed_at FROM user_challenge WHERE user_id=? AND challenge_id=?",
    [userId,challenge.id]
  );
  const progress = await calculateProgress(challenge,userId,queries,membership);
  return progressResponse(challenge,membership,progress);
}

export async function getAllChallenges(_req, res) {
  try {
    const rows = await all("SELECT * FROM challenges ORDER BY id");
    res.json(rows.filter(row => isChallengeActive(row))
      .map(row => ({...row,criteria:parseCriteria(row.criteria),active:true})));
  } catch { res.status(500).json({error:"Could not load challenges"}); }
}

export async function getChallengeById(req, res) {
  try {
    const challenge = await get("SELECT * FROM challenges WHERE id=?", [req.params.id]);
    if (!challenge) return res.status(404).json({error:"Challenge not found"});
    const locations = await all(
      `SELECT location.* FROM locations location JOIN challenge_location item
       ON item.location_id=location.id WHERE item.challenge_id=?`, [challenge.id]
    );
    res.json({...challenge,criteria:parseCriteria(challenge.criteria),locations});
  } catch { res.status(500).json({error:"Could not load challenge"}); }
}

async function replaceRelations(queries, challengeId, table, column, values) {
  await queries.run(`DELETE FROM ${table} WHERE challenge_id=?`, [challengeId]);
  for (const value of values) {
    await queries.run(`INSERT INTO ${table} (challenge_id,${column}) VALUES (?,?)`, [challengeId,value]);
  }
}

export async function addChallenge(req, res) {
  const { name,description,start_date,end_date,reward_point,challenge_type,criteria,
    required_checkins,location_ids,reward_ids } = req.body;
  try {
    const id = await transaction(async queries => {
      const inserted = await queries.run(
        `INSERT INTO challenges
         (name,description,start_date,end_date,reward_point,challenge_type,criteria,required_checkins)
         VALUES (?,?,?,?,?,?,?,?)`,
        [name,description ?? null,start_date ?? null,end_date ?? null,reward_point,
          challenge_type,JSON.stringify(criteria),required_checkins]
      );
      const criteriaLocations = Array.isArray(criteria.location_ids) ? criteria.location_ids : [];
      await replaceRelations(queries,inserted.lastID,"challenge_location","location_id",[...new Set([...location_ids,...criteriaLocations])]);
      await replaceRelations(queries,inserted.lastID,"challenge_reward","reward_id",reward_ids);
      return inserted.lastID;
    });
    res.status(201).json({id,message:"Challenge created"});
  } catch { res.status(500).json({error:"Challenge creation failed"}); }
}

export async function updateChallenge(req, res) {
  const scalarFields = ["name","description","start_date","end_date","reward_point","challenge_type","required_checkins"];
  const fields = scalarFields.filter(field => req.body[field] !== undefined);
  if (req.body.criteria !== undefined) fields.push("criteria");
  try {
    const challenge = await transaction(async queries => {
      if (!await queries.get("SELECT id FROM challenges WHERE id=?", [req.params.id])) {
        throw Object.assign(new Error("Challenge not found"), {status:404});
      }
      if (fields.length) {
        const values = fields.map(field => field === "criteria" ? JSON.stringify(req.body.criteria) : req.body[field]);
        await queries.run(`UPDATE challenges SET ${fields.map(field => `${field}=?`).join(",")} WHERE id=?`, [...values,req.params.id]);
      }
      if (req.body.location_ids !== undefined)
        await replaceRelations(queries,req.params.id,"challenge_location","location_id",req.body.location_ids);
      if (req.body.reward_ids !== undefined)
        await replaceRelations(queries,req.params.id,"challenge_reward","reward_id",req.body.reward_ids);
      return queries.get("SELECT * FROM challenges WHERE id=?", [req.params.id]);
    });
    res.json({...challenge,criteria:parseCriteria(challenge.criteria)});
  } catch (error) {
    res.status(error.status || 500).json({error:error.status ? error.message : "Could not update challenge"});
  }
}

export async function deleteChallenge(req, res) {
  try {
    const result = await run("DELETE FROM challenges WHERE id=?", [req.params.id]);
    if (!result.changes) return res.status(404).json({error:"Challenge not found"});
    res.json({message:"Challenge deleted"});
  } catch { res.status(500).json({error:"Could not delete challenge"}); }
}

export async function joinChallenge(req, res) {
  try {
    const challenge = await get("SELECT id,start_date,end_date FROM challenges WHERE id=?", [req.params.id]);
    if (!challenge) return res.status(404).json({error:"Challenge not found"});
    const now = Date.now();
    if (challenge.start_date && new Date(challenge.start_date).getTime() > now)
      return res.status(409).json({error:"Challenge has not started"});
    if (challenge.end_date && new Date(challenge.end_date).getTime() < now)
      return res.status(409).json({error:"Challenge has ended"});
    const result = await run(
      `INSERT OR IGNORE INTO user_challenge (user_id,challenge_id,status,joined_at)
       VALUES (?,?,'in_progress',strftime('%Y-%m-%dT%H:%M:%fZ','now'))`,
      [req.user.id,challenge.id]
    );
    if (!result.changes) return res.status(409).json({error:"Challenge already joined"});
    res.status(201).json({message:"Challenge joined"});
  } catch { res.status(500).json({error:"Could not join challenge"}); }
}

export async function getChallengeProgress(req, res) {
  try {
    const challenge = await get("SELECT * FROM challenges WHERE id=?", [req.params.id]);
    if (!challenge) return res.status(404).json({error:"Challenge not found"});
    res.json(await buildChallengeProgress(challenge,req.user.id));
  } catch { res.status(500).json({error:"Could not calculate progress"}); }
}

export async function completeChallenge(req, res) {
  try {
    const result = await transaction(async queries => {
      const challenge = await queries.get("SELECT * FROM challenges WHERE id=?", [req.params.id]);
      if (!challenge) throw Object.assign(new Error("Challenge not found"), {status:404});
      const membership = await queries.get(
        "SELECT status,joined_at FROM user_challenge WHERE user_id=? AND challenge_id=?",
        [req.user.id,challenge.id]
      );
      if (!membership) throw Object.assign(new Error("Join the challenge first"), {status:409});
      if (membership.status === "claimed") throw Object.assign(new Error("Challenge reward already claimed"), {status:409});
      if (challenge.end_date && new Date(challenge.end_date).getTime() < Date.now())
        throw Object.assign(new Error("Challenge has ended"), {status:409});
      const progress = await calculateProgress(challenge,req.user.id,queries,membership);
      const target = targetFor(challenge);
      if (!target || progress < target) throw Object.assign(new Error("Not enough progress"), {status:409});
      const points = Number(challenge.reward_point) || 0;
      await queries.run("UPDATE users SET total_point=total_point+? WHERE id=?", [points,req.user.id]);
      await queries.run(
        `UPDATE user_challenge SET status='claimed',progress=?,
         completed_at=strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE user_id=? AND challenge_id=?`,
        [progress,req.user.id,challenge.id]
      );
      await queries.run(
        "INSERT INTO points_transactions (user_id,points,type,description) VALUES (?,?,'credit',?)",
        [req.user.id,points,`Challenge #${challenge.id}: ${challenge.name}`]
      );
      const rewards = await queries.all(
        `SELECT reward.* FROM rewards reward JOIN challenge_reward item
         ON item.reward_id=reward.id WHERE item.challenge_id=?`, [challenge.id]
      );
      const issued = [];
      for (const reward of rewards) {
        if (await queries.get("SELECT id FROM user_reward WHERE user_id=? AND reward_id=?", [req.user.id,reward.id])) continue;
        const code = crypto.randomBytes(6).toString("hex").toUpperCase();
        await queries.run(
          `INSERT INTO user_reward (user_id,reward_id,code,status,expires_at,created_at)
           VALUES (?,?,?,'active',?,strftime('%Y-%m-%dT%H:%M:%fZ','now'))`,
          [req.user.id,reward.id,code,reward.expires_at || null]
        );
        issued.push({reward_id:reward.id,code});
      }
      return {message:`Challenge claimed! +${points} points`,progress,target,rewards_issued:issued};
    });
    res.json(result);
  } catch (error) {
    res.status(error.status || 500).json({error:error.status ? error.message : "Challenge claim failed"});
  }
}

export async function setManualProgress(req, res) {
  try {
    const result = await run(
      "UPDATE user_challenge SET progress=?,status='in_progress',completed_at=NULL WHERE user_id=? AND challenge_id=?",
      [req.body.progress,req.body.user_id,req.params.id]
    );
    if (!result.changes) return res.status(404).json({error:"User challenge not found"});
    res.json({message:"Progress updated",progress:req.body.progress});
  } catch { res.status(500).json({error:"Could not set progress"}); }
}

export async function logChallengeActivity(req, res) {
  const {user_id,type,target_id,meta} = req.body;
  try {
    await run(
      "INSERT INTO user_activity (user_id,challenge_id,type,target_id,meta_json) VALUES (?,?,?,?,?)",
      [user_id,req.params.id,type,target_id || null,meta ? JSON.stringify(meta) : null]
    );
    res.status(201).json({message:"Trusted activity recorded"});
  } catch { res.status(500).json({error:"Could not record activity"}); }
}

export async function getChallengeLocations(req, res) {
  try {
    res.json(await all(
      `SELECT location.* FROM locations location JOIN challenge_location item
       ON item.location_id=location.id WHERE item.challenge_id=?`, [req.params.id]
    ));
  } catch { res.status(500).json({error:"Could not load challenge locations"}); }
}

export async function getChallengeRewards(req, res) {
  try {
    res.json(await all(
      `SELECT reward.* FROM rewards reward JOIN challenge_reward item
       ON item.reward_id=reward.id WHERE item.challenge_id=?`, [req.params.id]
    ));
  } catch { res.status(500).json({error:"Could not load challenge rewards"}); }
}
