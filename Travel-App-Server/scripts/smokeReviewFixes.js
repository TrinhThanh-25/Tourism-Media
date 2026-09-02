import assert from "node:assert/strict";
import app from "../server.js";
import db from "../db/connect.js";
import { get, run } from "../db/queries.js";

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

const server = app.listen(port, async () => {
  try {
    const suffix = Date.now();
    const owner = await request("/auth/register", "POST", {
      username:`trip_owner_${suffix}`, email:`owner.${suffix}@example.com`, password:"owner-pass-2026"
    }, null, 201);
    const viewer = await request("/auth/register", "POST", {
      username:`trip_viewer_${suffix}`, email:`viewer.${suffix}@example.com`, password:"viewer-pass-2026"
    }, null, 201);

    const privateTrip = await request("/api/trips", "POST", {
      title:"Chuyến đi kiểm thử riêng tư", total_time:2880, locations:[]
    }, owner.token, 201);
    assert.equal(privateTrip.is_post, 0);
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
    console.log("Smoke fixes passed: private/public trips, author/time, filters, challenge membership, vouchers, refresh rotation, seed login");
  } finally {
    server.close(() => db.close());
  }
});
