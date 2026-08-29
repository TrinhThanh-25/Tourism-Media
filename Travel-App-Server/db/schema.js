export const TABLES = [
  `CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE,
    email TEXT UNIQUE, password TEXT, total_point INTEGER DEFAULT 0,
    avatar_url TEXT, dob TEXT, gender TEXT, phone TEXT, role TEXT NOT NULL DEFAULT 'user'
  )`,
  `CREATE TABLE IF NOT EXISTS locations (
    id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, category TEXT, type TEXT,
    price REAL DEFAULT 0, description TEXT, latitude REAL, longitude REAL, address TEXT,
    city TEXT, opening_hours TEXT, closing_hours TEXT, image_url TEXT,
    rating REAL, review_count INTEGER DEFAULT 0, qr_code TEXT, key_highlights TEXT
  )`,
  `CREATE TABLE IF NOT EXISTS trips (
    id INTEGER PRIMARY KEY, title TEXT NOT NULL, description TEXT, rating REAL,
    key_highlight TEXT, estimate_price INTEGER, total_time INTEGER, url_image TEXT,
    review_count INTEGER DEFAULT 0, user_id INTEGER, created_at TEXT,
    is_post INTEGER DEFAULT 0, published_at TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
  )`,
  `CREATE TABLE IF NOT EXISTS trip_locations (
    trip_id INTEGER NOT NULL, location_id INTEGER NOT NULL, order_index INTEGER,
    day INTEGER, time TEXT, PRIMARY KEY (trip_id, location_id),
    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE CASCADE
  )`,
  `CREATE TABLE IF NOT EXISTS location_images (
    id INTEGER PRIMARY KEY AUTOINCREMENT, location_id INTEGER NOT NULL, url TEXT NOT NULL,
    caption TEXT, sort_order INTEGER DEFAULT 0,
    FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE CASCADE
  )`,
  `CREATE TABLE IF NOT EXISTS user_location (
    id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, location_id INTEGER NOT NULL,
    checked_in_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE CASCADE
  )`,
  `CREATE TABLE IF NOT EXISTS challenges (
    id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT,
    start_date TEXT, end_date TEXT, duration INTEGER, reward_point INTEGER DEFAULT 0,
    reward_type TEXT, rules TEXT, required_checkins INTEGER DEFAULT 0,
    metadata TEXT, challenge_type TEXT, criteria TEXT
  )`,
  `CREATE TABLE IF NOT EXISTS rewards (
    id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, start_date TEXT,
    end_date TEXT, description TEXT, cost INTEGER DEFAULT 0, expires_at TEXT,
    point_reward INTEGER DEFAULT 0, max_uses INTEGER, per_user_limit INTEGER DEFAULT 1,
    metadata TEXT, percent INTEGER DEFAULT 0, code TEXT
  )`,
  `CREATE TABLE IF NOT EXISTS challenge_reward (
    challenge_id INTEGER NOT NULL, reward_id INTEGER NOT NULL,
    PRIMARY KEY (challenge_id, reward_id),
    FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE,
    FOREIGN KEY (reward_id) REFERENCES rewards(id) ON DELETE CASCADE
  )`,
  `CREATE TABLE IF NOT EXISTS user_reward (
    id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, reward_id INTEGER NOT NULL,
    obtained_at TEXT DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')), code TEXT UNIQUE,
    status TEXT DEFAULT 'active', issued_at TEXT DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    expires_at TEXT, claimed_at TEXT, used_at TEXT,
    created_at TEXT DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (reward_id) REFERENCES rewards(id) ON DELETE CASCADE
  )`,
  `CREATE TABLE IF NOT EXISTS challenge_location (
    challenge_id INTEGER NOT NULL, location_id INTEGER NOT NULL,
    PRIMARY KEY (challenge_id, location_id),
    FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE,
    FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE CASCADE
  )`,
  `CREATE TABLE IF NOT EXISTS user_challenge (
    user_id INTEGER NOT NULL, challenge_id INTEGER NOT NULL,
    joined_at TEXT DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')), status TEXT DEFAULT 'in_progress',
    progress INTEGER DEFAULT 0, completed_at TEXT, PRIMARY KEY (user_id, challenge_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE
  )`,
  `CREATE TABLE IF NOT EXISTS user_activity (
    id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, challenge_id INTEGER,
    type TEXT NOT NULL, target_id INTEGER, meta_json TEXT,
    created_at TEXT DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE SET NULL
  )`,
  `CREATE TABLE IF NOT EXISTS points_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, reward_id INTEGER,
    points INTEGER NOT NULL, type TEXT NOT NULL, description TEXT,
    created_at TEXT DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (reward_id) REFERENCES rewards(id) ON DELETE SET NULL
  )`,
  `CREATE TABLE IF NOT EXISTS user_refresh_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, token TEXT NOT NULL UNIQUE,
    expires_at TEXT, revoked INTEGER DEFAULT 0,
    created_at TEXT DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
  )`,
  `CREATE TABLE IF NOT EXISTS location_reviews (
    id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, location_id INTEGER NOT NULL,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5), comment TEXT,
    created_at TEXT DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE CASCADE
  )`,
  `CREATE TABLE IF NOT EXISTS trip_reviews (
    id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, trip_id INTEGER NOT NULL,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5), comment TEXT,
    created_at TEXT DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
  )`,
  `CREATE TABLE IF NOT EXISTS trip_images (
    id INTEGER PRIMARY KEY AUTOINCREMENT, trip_id INTEGER NOT NULL, url TEXT NOT NULL,
    sort_order INTEGER DEFAULT 0, day TEXT, time TEXT,
    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
  )`,
  `CREATE TABLE IF NOT EXISTS user_favorite_locations (
    user_id INTEGER NOT NULL, location_id INTEGER NOT NULL, PRIMARY KEY (user_id, location_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE CASCADE
  )`,
  `CREATE TABLE IF NOT EXISTS user_favorite_trips (
    user_id INTEGER NOT NULL, trip_id INTEGER NOT NULL, PRIMARY KEY (user_id, trip_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
  )`
];

export const INDEXES = [
  "CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)",
  "CREATE INDEX IF NOT EXISTS idx_locations_city ON locations(city)",
  "CREATE INDEX IF NOT EXISTS idx_locations_latlon ON locations(latitude,longitude)",
  "CREATE INDEX IF NOT EXISTS idx_challenges_dates ON challenges(start_date,end_date)",
  "CREATE INDEX IF NOT EXISTS idx_rewards_cost ON rewards(cost)",
  "CREATE INDEX IF NOT EXISTS idx_points_transactions_user ON points_transactions(user_id)",
  "CREATE INDEX IF NOT EXISTS idx_location_images_location_sort ON location_images(location_id,sort_order)",
  "CREATE INDEX IF NOT EXISTS idx_trip_locations_order ON trip_locations(trip_id,order_index)",
  "CREATE INDEX IF NOT EXISTS idx_user_location_user_time ON user_location(user_id,checked_in_at)",
  "CREATE INDEX IF NOT EXISTS idx_user_fav_trips_user ON user_favorite_trips(user_id)",
  "CREATE INDEX IF NOT EXISTS idx_user_fav_trips_trip ON user_favorite_trips(trip_id)"
];
