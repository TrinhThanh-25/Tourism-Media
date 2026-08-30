import { all, get, transaction } from "../db/queries.js";

export const addTransaction = async (req, res) => {
  const { user_id, points, type, description } = req.body || {};
  if (!Number.isInteger(user_id) || !Number.isInteger(points) || !type) {
    return res.status(400).json({ error: "user_id, integer points and type are required" });
  }
  try {
    const result = await transaction(async queries => {
      const user = await queries.get("SELECT id, total_point FROM users WHERE id = ?", [user_id]);
      if (!user) throw Object.assign(new Error("User not found"), { status: 404 });
      const balance = (user.total_point || 0) + points;
      if (balance < 0) throw Object.assign(new Error("Insufficient points"), { status: 409 });
      const inserted = await queries.run(
        "INSERT INTO points_transactions (user_id, points, type, description) VALUES (?, ?, ?, ?)",
        [user_id, points, type, description || null]
      );
      await queries.run("UPDATE users SET total_point = ? WHERE id = ?", [balance, user_id]);
      return { id: inserted.lastID, user_id, points, type, description, balance };
    });
    res.status(201).json(result);
  } catch (error) {
    res.status(error.status || 500).json({ error: error.status ? error.message : "Point transaction failed" });
  }
};

export const getMyPoints = async (req, res) => {
  try {
    const row = await get("SELECT total_point FROM users WHERE id = ?", [req.user.id]);
    if (!row) return res.status(404).json({ error: "User not found" });
    res.json({ points: row.total_point || 0 });
  } catch { res.status(500).json({ error: "Could not load points" }); }
};

export const listTransactionsForUser = async (req, res) => {
  try {
    const rows = await all(
      `SELECT id, reward_id, points, type, description, created_at
       FROM points_transactions WHERE user_id = ? ORDER BY created_at DESC`, [req.user.id]
    );
    res.json(rows);
  } catch { res.status(500).json({ error: "Could not load point transactions" }); }
};
