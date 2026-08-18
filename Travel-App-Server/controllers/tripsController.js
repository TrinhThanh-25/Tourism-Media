import { all, get, run, transaction } from "../db/queries.js";
import { createFavoriteController } from "./favoriteControllerFactory.js";

const TRIP_FIELDS = "id,title,description,rating,review_count,key_highlight,estimate_price,total_time,url_image,user_id,created_at,is_post,published_at";
const EDITABLE_FIELDS = ["title", "description", "estimate_price", "total_time", "url_image", "key_highlight"];
const tripColumns = (alias = "t") => TRIP_FIELDS.split(",").map(field => `${alias}.${field}`).join(",");
const ownerColumns = "owner.username AS author_username,owner.avatar_url AS author_avatar";

function normalizeLocations(locations = []) {
  return locations.map((item, index) => ({
    locationId: Number(item.location_id ?? item.id),
    orderIndex: item.order_index ?? index,
    day: item.day ?? null,
    time: item.time || null
  }));
}

async function saveLocations(queries, tripId, locations, replace = false) {
  const items = normalizeLocations(locations);
  if (new Set(items.map(item => item.locationId)).size !== items.length) {
    throw Object.assign(new Error("A location can appear only once per trip"), { status: 400 });
  }
  if (replace) await queries.run("DELETE FROM trip_locations WHERE trip_id=?", [tripId]);
  for (const item of items) {
    await queries.run(
      "INSERT INTO trip_locations (trip_id,location_id,order_index,day,time) VALUES (?,?,?,?,?)",
      [tripId,item.locationId,item.orderIndex,item.day,item.time]
    );
  }
}

export async function listTrips(req, res) {
  const search = (req.query.q || "").trim();
  const minRating = Number(req.query.min_rating) || 0;
  const maxPrice = Number(req.query.max_price) || 999999999;
  const minTime = Math.max(0, Number(req.query.min_time) || 0);
  const maxTime = Number(req.query.max_time) || 999999999;
  const limit = Math.min(100, Math.max(1, Number.parseInt(req.query.limit) || 20));
  const offset = Math.max(0, Number.parseInt(req.query.offset) || 0);
  let [column, direction] = (req.query.sort || "rating-desc").toLowerCase().split("-");
  if (!["rating","review_count","estimate_price","total_time","title","id","published_at"].includes(column)) column = "rating";
  direction = direction === "asc" ? "ASC" : "DESC";

  try {
    const like = `%${search}%`;
    const rows = await all(
      `SELECT ${tripColumns()},${ownerColumns},
       CASE WHEN favorite.trip_id IS NULL THEN 0 ELSE 1 END AS is_favorite
       FROM trips t LEFT JOIN users owner ON owner.id=t.user_id
       LEFT JOIN user_favorite_trips favorite
         ON favorite.trip_id=t.id AND favorite.user_id=?
       WHERE t.is_post=1 AND (?='' OR t.title LIKE ? OR t.description LIKE ? OR t.key_highlight LIKE ?)
       AND COALESCE(t.rating,0)>=? AND COALESCE(t.estimate_price,0)<=?
       AND COALESCE(t.total_time,0)>=? AND COALESCE(t.total_time,0)<=?
       ORDER BY t.${column} ${direction} LIMIT ? OFFSET ?`,
      [req.user?.id || -1,search,like,like,like,minRating,maxPrice,minTime,maxTime,limit,offset]
    );
    res.json({ data: rows, nextOffset: rows.length === limit ? offset + rows.length : null, count: rows.length });
  } catch (error) {
    console.error("Could not load trips:", error.message);
    res.status(500).json({ error: "Could not load trips" });
  }
}

export async function getTrip(req, res) {
  try {
    const userId = req.user?.id || -1;
    const trip = await get(
      `SELECT ${tripColumns()},${ownerColumns},
       CASE WHEN favorite.trip_id IS NULL THEN 0 ELSE 1 END AS is_favorite
       FROM trips t LEFT JOIN users owner ON owner.id=t.user_id
       LEFT JOIN user_favorite_trips favorite
         ON favorite.trip_id=t.id AND favorite.user_id=?
       WHERE t.id=? AND (t.is_post=1 OR t.user_id=?)`,
      [userId,req.params.id,userId]
    );
    if (!trip) return res.status(404).json({ error: "Trip not found" });
    trip.locations = await all(
      `SELECT location.*,item.order_index,item.day,item.time
       FROM trip_locations item JOIN locations location ON location.id=item.location_id
       WHERE item.trip_id=? ORDER BY COALESCE(item.order_index,9999),item.location_id`,
      [trip.id]
    );
    trip.images = await all("SELECT id,url,sort_order,day,time FROM trip_images WHERE trip_id=? ORDER BY sort_order,id", [trip.id]);
    trip.itinerary_by_day = trip.locations.reduce((result, item) => {
      const day = String(item.day ?? "unassigned");
      (result[day] ??= []).push(item);
      return result;
    }, {});
    res.json(trip);
  } catch (error) {
    console.error("Could not load trip:", error.message);
    res.status(500).json({ error: "Could not load trip" });
  }
}

export async function createTrip(req, res) {
  const { title,description,estimate_price,total_time,url_image,key_highlight,locations } = req.body;
  try {
    const trip = await transaction(async queries => {
      const inserted = await queries.run(
        `INSERT INTO trips (title,description,rating,review_count,key_highlight,estimate_price,total_time,url_image,user_id,created_at,is_post)
         VALUES (?,?,NULL,0,?,?,?,?,?,strftime('%Y-%m-%dT%H:%M:%fZ','now'),0)`,
        [title,description ?? null,key_highlight ?? null,estimate_price ?? null,total_time ?? null,url_image ?? null,req.user.id]
      );
      await saveLocations(queries, inserted.lastID, locations);
      return queries.get(
        `SELECT ${tripColumns()},${ownerColumns},0 AS is_favorite
         FROM trips t LEFT JOIN users owner ON owner.id=t.user_id WHERE t.id=?`,
        [inserted.lastID]
      );
    });
    res.status(201).json(trip);
  } catch (error) {
    console.error("Could not create trip:", error.message);
    res.status(error.status || 500).json({ error: error.status ? error.message : "Could not create trip" });
  }
}

export async function updateTrip(req, res) {
  const fields = EDITABLE_FIELDS.filter(field => req.body[field] !== undefined);
  try {
    const trip = await transaction(async queries => {
      const owned = await queries.get("SELECT id FROM trips WHERE id=? AND user_id=?", [req.params.id,req.user.id]);
      if (!owned) throw Object.assign(new Error("Trip not found or not owned by user"), { status: 404 });
      if (fields.length) {
        await queries.run(
          `UPDATE trips SET ${fields.map(field => `${field}=?`).join(",")} WHERE id=?`,
          [...fields.map(field => req.body[field]),owned.id]
        );
      }
      if (req.body.locations !== undefined) await saveLocations(queries,owned.id,req.body.locations,true);
      return queries.get(
        `SELECT ${tripColumns()},${ownerColumns},0 AS is_favorite
         FROM trips t LEFT JOIN users owner ON owner.id=t.user_id WHERE t.id=?`,
        [owned.id]
      );
    });
    res.json(trip);
  } catch (error) {
    console.error("Could not update trip:", error.message);
    res.status(error.status || 500).json({ error: error.status ? error.message : "Could not update trip" });
  }
}

export async function deleteTrip(req, res) {
  try {
    const result = await run("DELETE FROM trips WHERE id=? AND user_id=?", [req.params.id,req.user.id]);
    if (!result.changes) return res.status(404).json({ error: "Trip not found or not owned by user" });
    res.json({ message: "Trip deleted" });
  } catch (error) {
    console.error("Could not delete trip:", error.message);
    res.status(500).json({ error: "Could not delete trip" });
  }
}

export async function getUserTrip(req, res) {
  try {
    res.json(await all(
      `SELECT ${tripColumns()},${ownerColumns},0 AS is_favorite
       FROM trips t LEFT JOIN users owner ON owner.id=t.user_id
       WHERE t.user_id=? ORDER BY t.created_at DESC,t.id DESC`,
      [req.user.id]
    ));
  } catch (error) {
    console.error("Could not load user trips:", error.message);
    res.status(500).json({ error: "Could not load user trips" });
  }
}

async function setPublished(req, res, value) {
  try {
    const trip = await transaction(async queries => {
      const owned = await queries.get("SELECT id FROM trips WHERE id=? AND user_id=?", [req.params.id,req.user.id]);
      if (!owned) throw Object.assign(new Error("Trip not found or not owned by user"), { status:404 });
      await queries.run(
        `UPDATE trips SET is_post=?,published_at=${value ? "strftime('%Y-%m-%dT%H:%M:%fZ','now')" : "NULL"}
         WHERE id=?`,
        [value,owned.id]
      );
      if (!value) await queries.run("DELETE FROM user_favorite_trips WHERE trip_id=?", [owned.id]);
      return queries.get(
        `SELECT ${tripColumns()},${ownerColumns},0 AS is_favorite
         FROM trips t LEFT JOIN users owner ON owner.id=t.user_id WHERE t.id=?`,
        [owned.id]
      );
    });
    res.json(trip);
  } catch (error) {
    console.error("Could not update publication status:", error.message);
    res.status(error.status || 500).json({ error:error.status ? error.message : "Could not update publication status" });
  }
}

const tripFavorites = createFavoriteController({
  junctionTable:"user_favorite_trips", foreignKey:"trip_id", resourceTable:"trips", resourceLabel:"Trip",
  availabilityCondition:"resource.is_post=1", availabilityError:"Published trip not found",
  orderBy:"COALESCE(resource.rating,0) DESC,resource.id DESC",
  selectExtras:"owner.username AS author_username,owner.avatar_url AS author_avatar",
  joins:"LEFT JOIN users owner ON owner.id=resource.user_id"
});

export const getFavoriteTrips = tripFavorites.list;
export const addFavoriteTrip = tripFavorites.add;
export const removeFavoriteTrip = tripFavorites.remove;
export const publishTrip = (req,res) => setPublished(req,res,1);
export const unpublishTrip = (req,res) => setPublished(req,res,0);
