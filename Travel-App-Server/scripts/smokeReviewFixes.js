import assert from "node:assert/strict";
import { copyFileSync, existsSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const workDir = mkdtempSync(join(tmpdir(), "tourism-regression-smoke-"));
process.env.DB_PATH = join(workDir, "smoke.db");
process.env.JWT_SECRET = "smoke-test-secret-not-for-runtime";
process.env.NODE_ENV = "test";
process.env.PORT = "3103";
process.env.UPLOAD_DIR = join(workDir, "uploads");
process.env.PUBLIC_BASE_URL = `http://127.0.0.1:${process.env.PORT}`;
copyFileSync(new URL("../travel_app.template.db", import.meta.url), process.env.DB_PATH);

const { default:app } = await import("../server.js");
const { default:db } = await import("../db/connect.js");
const { all,get,run } = await import("../db/queries.js");

const port = Number(process.env.PORT || 3103);
const base = `http://127.0.0.1:${port}`;

async function request(path, method = "GET", body, token, expected = 200) {
  const response = await fetch(base + path, {
    method,
    headers:{
      ...(body ? { "content-type":"application/json" } : {}),
      ...(token ? { authorization:`Bearer ${token}` } : {})
    },
    body:body ? JSON.stringify(body) : undefined
  });
  const data = await response.json();
  assert.equal(response.status, expected, `${method} ${path}: ${JSON.stringify(data)}`);
  return data;
}

async function uploadImage(token) {
  const response = await fetch(base + "/api/uploads/images", {
    method:"POST",
    headers:{authorization:`Bearer ${token}`,"content-type":"image/png"},
    body:Buffer.from([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a])
  });
  const data = await response.json();
  assert.equal(response.status,201,JSON.stringify(data));
  return data;
}

const server = app.listen(port, async () => {
  try {
    const suffix = Date.now();
    const owner = await request("/auth/register", "POST", {
      username:`trip_owner_${suffix}`, email:`owner.${suffix}@example.com`, password:"owner-pass-2026"
    }, null, 201);
    const viewer = await request("/auth/register", "POST", {
      username:`trip_viewer_${suffix}`, email:`viewer.${suffix}@example.com`, password:"viewer-pass-2026"
    }, null, 201);

    const uploaded = await uploadImage(owner.token);
    const uploadedName = uploaded.url.split("/").pop();
    assert.ok(existsSync(join(process.env.UPLOAD_DIR,"images",uploadedName)));
    const downloaded = await fetch(uploaded.url);
    assert.equal(downloaded.status,200);
    assert.equal((await downloaded.arrayBuffer()).byteLength,8);
    await request("/api/me", "PATCH", {avatar_url:uploaded.url}, owner.token);
    assert.equal((await request("/api/me", "GET", null, owner.token)).avatar, uploaded.url);

    const privateTrip = await request("/api/trips", "POST", {
      title:"Chuyến đi kiểm thử riêng tư", total_time:2880,
      url_image:uploaded.url, locations:[]
    }, owner.token, 201);
    assert.equal(privateTrip.is_post, 0);
    assert.equal(privateTrip.url_image, uploaded.url);
    const beforePublish = await request("/api/trips", "GET", null, viewer.token);
    assert.ok(!beforePublish.data.some(item => item.id === privateTrip.id));
    await request(`/api/trips/${privateTrip.id}`, "GET", null, viewer.token, 404);

    const published = await request(`/api/trips/${privateTrip.id}/publish`, "POST", null, owner.token);
    assert.equal(published.is_post, 1);
    assert.ok(published.published_at);
    assert.equal(published.author_username, `trip_owner_${suffix}`);
    const community = await request("/api/trips?min_time=1440&max_time=4320&sort=published_at-desc", "GET", null, viewer.token);
    assert.ok(community.data.some(item => item.id === privateTrip.id && item.author_username === `trip_owner_${suffix}`));
    await request(`/api/trips/${privateTrip.id}/favorite`, "POST", null, viewer.token, 201);
    await request(`/api/trips/${privateTrip.id}/unpublish`, "POST", null, owner.token);
    const savedAfterUnpublish = await request("/api/trips/me/favorites", "GET", null, viewer.token);
    assert.ok(!savedAfterUnpublish.some(item => item.id === privateTrip.id));

    const challenge = (await request("/api/me/challenges", "GET", null, viewer.token))[0];
    assert.equal(challenge.joined, false);
    assert.equal(challenge.active, true);
    await request(`/api/challenges/${challenge.id}/join`, "POST", null, viewer.token, 201);
    const joined = await request(`/api/challenges/${challenge.id}/progress`, "GET", null, viewer.token);
    assert.equal(joined.joined, true);

    const readChallenge = (await request("/api/challenges", "GET"))
      .find(item => item.criteria?.target === "location_info_reads");
    assert.ok(readChallenge);
    await request(`/api/challenges/${readChallenge.id}/join`, "POST", null, viewer.token, 201);
    await request("/api/me/activity/location-read", "POST", {location_id:1}, viewer.token, 201);
    await request("/api/me/activity/location-read", "POST", {location_id:1}, viewer.token, 201);
    await request("/api/me/activity/location-read", "POST", {location_id:2}, viewer.token, 201);
    const readRows = await all(
      "SELECT target_id,created_at FROM user_activity WHERE user_id=? AND type='location_info_read'",
      [viewer.userId]
    );
    assert.equal(new Set(readRows.map(item => item.target_id)).size,2);
    const readProgress = await request(`/api/challenges/${readChallenge.id}/progress`, "GET", null, viewer.token);
    const readMembership = await get(
      "SELECT joined_at FROM user_challenge WHERE user_id=? AND challenge_id=?",
      [viewer.userId,readChallenge.id]
    );
    assert.equal(readProgress.progress,2,JSON.stringify({readRows,readMembership,readProgress}));
    const readListItem = (await request("/api/me/challenges", "GET", null, viewer.token))
      .find(item => item.id === readChallenge.id);
    assert.equal(readListItem.progress,2);

    const reward = await run("INSERT INTO rewards (name,cost) VALUES ('Expired smoke reward',0)");
    await run(
      "INSERT INTO user_reward (user_id,reward_id,code,status,expires_at) VALUES (?,?,?,'active','2020-01-01T00:00:00.000Z')",
      [viewer.userId,reward.lastID,`EXPIRED${suffix}`]
    );
    const vouchers = await request("/api/me/vouchers", "GET", null, viewer.token);
    assert.equal(vouchers.find(item => item.code === `EXPIRED${suffix}`).status, "expired");

    const refreshed = await request("/auth/refresh", "POST", { refreshToken:viewer.refreshToken });
    await request("/auth/refresh", "POST", { refreshToken:viewer.refreshToken }, null, 401);
    await request("/api/me", "GET", null, refreshed.token);
    await request("/auth/login", "POST", { email:"thinh@gmail.com", password:"demo_2026" });

    const expiredChallenges = await get(
      "SELECT COUNT(*) AS count FROM challenges WHERE end_date IS NOT NULL AND julianday(end_date)<julianday('now')"
    );
    assert.equal(expiredChallenges.count, 0);
    const publicChallenges = await request("/api/challenges", "GET");
    assert.ok(publicChallenges.length > 0 && publicChallenges.every(item => item.active === true));
    const migration = await get("SELECT name FROM schema_migrations WHERE version=7");
    assert.equal(migration.name, "add_application_columns");
    console.log("Smoke fixes passed: image upload, trips, location-read challenge, migration v7, vouchers, refresh rotation");
  } finally {
    server.close(() => db.close(() => rmSync(workDir, { recursive:true, force:true })));
  }
});
