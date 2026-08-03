const exists = (db, name) => new Promise((resolve, reject) => {
  db.get("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", [name],
    (error, row) => error ? reject(error) : resolve(Boolean(row)));
});

const execute = (db, sql, params = []) => new Promise((resolve, reject) => {
  db.run(sql, params, error => error ? reject(error) : resolve());
});

const migrations = [
  {
    version: 1,
    name: "remove_non_travel_tables",
    up: async db => {
      for (const table of ["shop_images", "Rentals", "Motorbikes", "Shops", "favorites", "location_reward", "payments"])
        await execute(db, `DROP TABLE IF EXISTS "${table}"`);
    }
  },
  {
    version: 2,
    name: "rename_trip_locations",
    up: async db => {
      if (await exists(db, "tripsLocation")) {
        await execute(db, `INSERT OR IGNORE INTO trip_locations (trip_id,location_id,order_index,day,time)
          SELECT trip_id,location_id,order_index,day,time FROM tripsLocation`);
        await execute(db, "DROP TABLE tripsLocation");
      }
    }
  },
  {
    version: 3,
    name: "checkin_history_and_review_uniqueness",
    up: async db => {
      const columns = await new Promise((resolve, reject) =>
        db.all("PRAGMA table_info(user_location)", (error, rows) => error ? reject(error) : resolve(rows)));
      if (!columns.some(column => column.name === "id")) {
        await execute(db, "ALTER TABLE user_location RENAME TO user_location_legacy");
        await execute(db, `CREATE TABLE user_location (
          id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, location_id INTEGER NOT NULL,
          checked_in_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
          FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
          FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE CASCADE)`);
        await execute(db, `INSERT INTO user_location (user_id,location_id,checked_in_at)
          SELECT user_id,location_id,
          CASE WHEN instr(checked_in_at,'T')=0 THEN replace(checked_in_at,' ','T')||'Z' ELSE checked_in_at END
          FROM user_location_legacy`);
        await execute(db, "DROP TABLE user_location_legacy");
      }
      await execute(db, `DELETE FROM location_reviews WHERE id NOT IN
        (SELECT MAX(id) FROM location_reviews GROUP BY user_id,location_id)`);
      await execute(db, `DELETE FROM trip_reviews WHERE id NOT IN
        (SELECT MAX(id) FROM trip_reviews GROUP BY user_id,trip_id)`);
      await execute(db, "CREATE UNIQUE INDEX IF NOT EXISTS uq_location_reviews_user_resource ON location_reviews(user_id,location_id)");
      await execute(db, "CREATE UNIQUE INDEX IF NOT EXISTS uq_trip_reviews_user_resource ON trip_reviews(user_id,trip_id)");
    }
  },
  {
    version: 4,
    name: "normalize_timestamps",
    up: async db => {
      const fields = [
        ["trips", "created_at"], ["user_location", "checked_in_at"],
        ["user_reward", "obtained_at"], ["user_reward", "issued_at"],
        ["user_reward", "created_at"], ["user_reward", "claimed_at"],
        ["user_reward", "used_at"], ["user_challenge", "joined_at"],
        ["user_challenge", "completed_at"], ["user_activity", "created_at"],
        ["points_transactions", "created_at"], ["user_refresh_tokens", "created_at"],
        ["user_refresh_tokens", "expires_at"], ["location_reviews", "created_at"],
        ["trip_reviews", "created_at"]
      ];
      for (const [table, column] of fields) {
        await execute(db, `UPDATE ${table} SET ${column}=replace(${column},' ','T')||'Z'
          WHERE ${column} IS NOT NULL AND instr(${column},'T')=0`);
      }
    }
  },
  {
    version: 5,
    name: "remove_streak_challenges",
    up: async db => {
      await execute(db, `DELETE FROM challenges WHERE challenge_type='streak'
        OR (json_valid(criteria) AND json_extract(criteria,'$.target')='streak_days')`);
    }
  },
  {
    version: 6,
    name: "normalize_voucher_expiry",
    up: async db => {
      await execute(db, `UPDATE user_reward SET expires_at=replace(expires_at,' ','T')||'Z'
        WHERE expires_at IS NOT NULL AND instr(expires_at,'T')=0 AND length(expires_at)>10`);
    }
  }
];

export async function runMigrations(db) {
  await execute(db, `CREATE TABLE IF NOT EXISTS schema_migrations (
    version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')))`);
  for (const migration of migrations) {
    const applied = await new Promise((resolve, reject) => db.get(
      "SELECT 1 FROM schema_migrations WHERE version=?", [migration.version],
      (error, row) => error ? reject(error) : resolve(Boolean(row))
    ));
    if (applied) continue;
    await execute(db, "BEGIN IMMEDIATE");
    try {
      await migration.up(db);
      await execute(db, "INSERT INTO schema_migrations (version,name) VALUES (?,?)", [migration.version,migration.name]);
      await execute(db, "COMMIT");
    } catch (error) {
      await execute(db, "ROLLBACK").catch(() => {});
      throw error;
    }
  }
}
