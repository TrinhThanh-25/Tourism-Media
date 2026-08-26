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

async function initializeDatabase() {
  await execute("PRAGMA foreign_keys=ON");
  for (const statement of TABLES) await execute(statement);
  await runMigrations(db);
  for (const statement of INDEXES) await execute(statement);
  await execute(`UPDATE user_reward SET created_at=COALESCE(created_at,issued_at,obtained_at,
    strftime('%Y-%m-%dT%H:%M:%fZ','now')) WHERE created_at IS NULL`);
  await execute("UPDATE trips SET published_at=COALESCE(published_at,created_at) WHERE is_post=1 AND published_at IS NULL");
  const adminEmails = (process.env.ADMIN_EMAILS || "").split(",").map(value => value.trim().toLowerCase()).filter(Boolean);
  for (const email of adminEmails) await execute("UPDATE users SET role='admin' WHERE lower(email)=?", [email]);
}

export const ready = initializeDatabase();
export default db;
