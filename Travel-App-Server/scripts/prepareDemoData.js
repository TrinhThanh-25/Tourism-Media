import path from "path";
import { fileURLToPath } from "url";
import bcrypt from "bcryptjs";
import sqlite3 from "sqlite3";

const projectDir = fileURLToPath(new URL("..", import.meta.url));
const defaultDatabases = [
  path.join(projectDir, "travel_app.template.db"),
  path.join(projectDir, "data", "travel_app.db")
];
const databases = process.argv.slice(2).length
  ? process.argv.slice(2).map(value => path.resolve(value))
  : defaultDatabases;

const open = filename => new Promise((resolve, reject) => {
  const database = new sqlite3.Database(filename, error => error ? reject(error) : resolve(database));
});
const all = (database, sql, params = []) => new Promise((resolve, reject) => {
  database.all(sql, params, (error, rows) => error ? reject(error) : resolve(rows));
});
const run = (database, sql, params = []) => new Promise((resolve, reject) => {
  database.run(sql, params, function (error) {
    if (error) reject(error);
    else resolve({ changes:this.changes });
  });
});
const close = database => new Promise((resolve, reject) => database.close(error => error ? reject(error) : resolve()));

for (const filename of databases) {
  const database = await open(filename);
  try {
    const tripColumns = await all(database, "PRAGMA table_info(trips)");
    if (!tripColumns.some(column => column.name === "published_at")) {
      await run(database, "ALTER TABLE trips ADD COLUMN published_at TEXT");
    }
    await run(database, "UPDATE trips SET published_at=COALESCE(published_at,created_at) WHERE is_post=1");

    // Keep every seeded challenge usable throughout the student demo period.
    await run(database, "UPDATE challenges SET start_date='2026-01-01T00:00:00.000Z',end_date='2027-12-31T23:59:59.999Z'");

    // Five legacy accounts used six-character passwords and were rejected by
    // the login validator. Give only those accounts a documented demo password.
    const legacyUsers = await all(database, "SELECT id,password FROM users WHERE length(password)<8");
    const demoPasswordHash = legacyUsers.length ? await bcrypt.hash("demo_2026", 10) : null;
    for (const user of legacyUsers) {
      await run(database, "UPDATE users SET password=? WHERE id=?", [demoPasswordHash,user.id]);
    }
    console.log(`${filename}: challenges active through 2027, ${legacyUsers.length} legacy password(s) repaired`);
  } finally {
    await close(database);
  }
}
