import sqlite3 from "sqlite3";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { TABLES, INDEXES } from "./schema.js";
import { runMigrations } from "./migrations.js";

const projectDir = fileURLToPath(new URL("..", import.meta.url));
const templatePath = path.join(projectDir, "travel_app.template.db");
const configuredPath = process.env.DB_PATH || "data/travel_app.db";
const dbPath = configuredPath === ":memory:" ? configuredPath : path.resolve(projectDir, configuredPath);

if (dbPath !== ":memory:") {
  fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  if (!fs.existsSync(dbPath) && fs.existsSync(templatePath)) fs.copyFileSync(templatePath, dbPath);
}

const db = new sqlite3.Database(dbPath, error => {
  if (error) console.error("Database connection failed:", error.message);
  else console.log(`Connected to SQLite database at ${dbPath}`);
});

const execute = (sql, params = []) => new Promise((resolve, reject) => {
  db.run(sql, params, error => error ? reject(error) : resolve());
});

const columns = table => new Promise((resolve, reject) => {
  db.all(`PRAGMA table_info(${table})`, (error, rows) => error ? reject(error) : resolve(rows));
});

async function ensureColumn(table, column, definition) {
  if (!(await columns(table)).some(item => item.name === column)) {
    await execute(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
  }
}

async function initializeDatabase() {
  await execute("PRAGMA foreign_keys=ON");
  for (const statement of TABLES) await execute(statement);
  await ensureColumn("users", "role", "TEXT NOT NULL DEFAULT 'user'");
  await ensureColumn("points_transactions", "reward_id", "INTEGER REFERENCES rewards(id) ON DELETE SET NULL");
  await ensureColumn("user_reward", "created_at", "TEXT");
  await ensureColumn("user_challenge", "progress", "INTEGER DEFAULT 0");
  await ensureColumn("user_challenge", "completed_at", "TEXT");
  await runMigrations(db);
  for (const statement of INDEXES) await execute(statement);
  await execute(`UPDATE user_reward SET created_at=COALESCE(created_at,issued_at,obtained_at,
    strftime('%Y-%m-%dT%H:%M:%fZ','now')) WHERE created_at IS NULL`);
  const adminEmails = (process.env.ADMIN_EMAILS || "").split(",").map(value => value.trim().toLowerCase()).filter(Boolean);
  for (const email of adminEmails) await execute("UPDATE users SET role='admin' WHERE lower(email)=?", [email]);
}

export const ready = initializeDatabase();
export default db;
