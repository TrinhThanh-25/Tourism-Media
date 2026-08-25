import assert from "node:assert/strict";
import { copyFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const workDir = mkdtempSync(join(tmpdir(), "tourism-review-smoke-"));
process.env.DB_PATH = join(workDir, "smoke.db");
copyFileSync(new URL("../travel_app.template.db", import.meta.url), process.env.DB_PATH);

// Import after DB_PATH is set because the database connection is created at module load time.
const { default:app } = await import("../server.js");
const { default:db } = await import("../db/connect.js");
const { get,run } = await import("../db/queries.js");

const port = Number(process.env.PORT || 3104);
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
  let failed = false;
  try {
    const suffix = Date.now();
    const owner = await request("/auth/register", "POST", {
      username:`priv_owner_${suffix}`, email:`priv.owner.${suffix}@example.com`, password:"owner-pass-2026"
    }, null, 201);
    const viewer = await request("/auth/register", "POST", {
      username:`priv_viewer_${suffix}`, email:`priv.viewer.${suffix}@example.com`, password:"viewer-pass-2026"
    }, null, 201);

    // ---------------------------------------------------------------- BE-09
    const trip = await request("/api/trips", "POST", {
      title:"Chuyến đi riêng tư cho smoke test", total_time:1440, locations:[]
    }, owner.token, 201);
    assert.equal(trip.is_post, 0);

    await request("/api/trip-reviews", "POST", { trip_id:trip.id, rating:5, comment:"Ghi lén" }, viewer.token, 403);
    await request(`/api/trip-reviews/trip/${trip.id}`, "GET", null, viewer.token, 403);
    await request(`/api/trip-reviews/trip/${trip.id}`, "GET", null, null, 403);
    assert.deepEqual(await request(`/api/trip-reviews/trip/${trip.id}`, "GET", null, owner.token), []);
    const untouched = await get("SELECT rating,review_count FROM trips WHERE id=?", [trip.id]);
    assert.equal(untouched.review_count, 0);

    await request(`/api/trips/${trip.id}/publish`, "POST", null, owner.token);
    const review = await request("/api/trip-reviews", "POST",
      { trip_id:trip.id, rating:4, comment:"Rất hay" }, viewer.token, 201);
    assert.equal((await request(`/api/trip-reviews/trip/${trip.id}`, "GET")).length, 1);

    await request(`/api/trips/${trip.id}/unpublish`, "POST", null, owner.token);
    await request(`/api/trip-reviews/trip/${trip.id}`, "GET", null, null, 403);
    assert.equal((await request(`/api/trip-reviews/trip/${trip.id}`, "GET", null, owner.token)).length, 1);
    await request(`/api/trip-reviews/${review.id}`, "PUT", { rating:1, comment:"Sửa lén" }, viewer.token, 403);
    await request("/api/trip-reviews/trip/999999", "GET", null, owner.token, 404);

    // Location reviews stay public and unrestricted.
    await request("/api/reviews", "POST", { location_id:1, rating:5, comment:"Đẹp" }, viewer.token, 201);
    assert.ok((await request("/api/reviews/location/1", "GET")).length > 0);

    // ---------------------------------------------------------------- BE-10
    // Join first: challenge progress only counts activity from the moment the user joins.
    const distinctChallenge = await get("SELECT id FROM challenges WHERE criteria LIKE '%\"checkins\"%' AND criteria NOT LIKE '%location_ids%' ORDER BY id LIMIT 1");
    await request(`/api/challenges/${distinctChallenge.id}/join`, "POST", null, viewer.token, 201);
    const first = await request("/api/me/locations", "POST", { location_id:1 }, viewer.token, 201);
    assert.equal(first.duplicate, false);
    const repeat = await request("/api/me/locations", "POST", { location_id:1 }, viewer.token, 200);
    assert.equal(repeat.duplicate, true);
    assert.equal(repeat.checkin.id, first.checkin.id);
    assert.equal((await request("/api/me/locations", "GET", null, viewer.token)).length, 1);

    // "Check-in 5 địa điểm" counts distinct locations, not repeated taps.
    for (const locationId of [2,3,4,5]) {
      await request("/api/me/locations", "POST", { location_id:locationId }, viewer.token, 201);
      await request("/api/me/locations", "POST", { location_id:locationId }, viewer.token, 200);
    }
    const progress = await request(`/api/challenges/${distinctChallenge.id}/progress`, "GET", null, viewer.token);
    assert.equal(progress.progress, 5, "5 distinct check-ins should count once each");
    assert.equal(progress.eligible, true);

    // Activity from before the user joined (or before the challenge window) must not count.
    const stale = await get("SELECT joined_at FROM user_challenge WHERE user_id=? AND challenge_id=?",
      [viewer.userId,distinctChallenge.id]);
    assert.ok(stale.joined_at.endsWith("Z"), `joined_at should be ISO-UTC, got ${stale.joined_at}`);
    await run("INSERT INTO user_location (user_id,location_id,checked_in_at) VALUES (?,?,?)",
      [viewer.userId,12,"2025-01-01T00:00:00.000Z"]);
    await run("INSERT INTO user_location (user_id,location_id,checked_in_at) VALUES (?,?,?)",
      [viewer.userId,13,"2030-01-01 00:00:00"]);
    const bounded = await request(`/api/challenges/${distinctChallenge.id}/progress`, "GET", null, viewer.token);
    assert.equal(bounded.progress, 5, "check-ins outside the challenge window must be ignored");

    const claimed = await request(`/api/challenges/${distinctChallenge.id}/complete`, "POST", null, viewer.token);
    assert.equal(claimed.progress, 5);

    console.log("Smoke passed: trip review privacy (BE-09), check-in dedup and windowed challenge progress (BE-10)");
  } catch (error) {
    failed = true;
    console.error(error);
  } finally {
    server.close(() => db.close(() => {
      rmSync(workDir, { recursive:true, force:true });
      process.exit(failed ? 1 : 0);
    }));
  }
});
