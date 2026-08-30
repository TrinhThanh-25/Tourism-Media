import { all, get, run } from "../db/queries.js";
import { createFavoriteController } from "./favoriteControllerFactory.js";

const LOCATION_FIELDS = [
  "name", "category", "type", "price", "image_url", "description", "latitude", "longitude",
  "address", "city", "opening_hours", "closing_hours", "qr_code", "key_highlights"
];

export async function getAllLocations(req, res) {
  const { q = "", category, type, min_price = 0, max_price = 9999999999, sort_by = "name-asc" } = req.query;
  const [column, sortDirection] = sort_by.split("-");
  const direction = sortDirection === "desc" ? "DESC" : "ASC";
  const conditions = ["locations.price BETWEEN ? AND ?"];
  const params = [req.user?.id || -1, min_price, max_price];

  if (q) {
    conditions.push("(locations.name LIKE ? OR locations.description LIKE ?)");
    params.push(`%${q}%`, `%${q}%`);
  }
  if (category) {
    conditions.push("locations.category=?");
    params.push(category);
  }
  if (type) {
    conditions.push("locations.type=?");
    params.push(type);
  }

  try {
    const rows = await all(
      `SELECT locations.*,
       CASE WHEN favorite.location_id IS NULL THEN 0 ELSE 1 END AS is_favorite
       FROM locations
       LEFT JOIN user_favorite_locations favorite
         ON favorite.location_id=locations.id AND favorite.user_id=?
       WHERE ${conditions.join(" AND ")}
       ORDER BY locations.${column} ${direction}`,
      params
    );
    res.json(rows);
  } catch (error) {
    console.error("Could not load locations:", error.message);
    res.status(500).json({ error: "Could not load locations" });
  }
}

export async function getLocationById(req, res) {
  try {
    const location = await get(
      `SELECT location.*,
       CASE WHEN favorite.location_id IS NULL THEN 0 ELSE 1 END AS is_favorite
       FROM locations location
       LEFT JOIN user_favorite_locations favorite
         ON favorite.location_id=location.id AND favorite.user_id=?
       WHERE location.id=?`,
      [req.user?.id || -1, req.params.id]
    );
    if (!location) return res.status(404).json({ error: "Location not found" });
    location.images = await all(
      "SELECT id,url,caption,sort_order FROM location_images WHERE location_id=? ORDER BY sort_order,id",
      [location.id]
    );
    res.json(location);
  } catch (error) {
    console.error("Could not load location:", error.message);
    res.status(500).json({ error: "Could not load location" });
  }
}

export async function addLocation(req, res) {
  const values = LOCATION_FIELDS.map(field => req.body[field] ?? null);
  try {
    const result = await run(
      `INSERT INTO locations (${LOCATION_FIELDS.join(",")}) VALUES (${LOCATION_FIELDS.map(() => "?").join(",")})`,
      values
    );
    res.status(201).json({ id: result.lastID, message: "Location created" });
  } catch (error) {
    console.error("Could not create location:", error.message);
    res.status(500).json({ error: "Could not create location" });
  }
}

export async function updateLocation(req, res) {
  const fields = LOCATION_FIELDS.filter(field => req.body[field] !== undefined);
  try {
    const result = await run(
      `UPDATE locations SET ${fields.map(field => `${field}=?`).join(",")} WHERE id=?`,
      [...fields.map(field => req.body[field]), req.params.id]
    );
    if (!result.changes) return res.status(404).json({ error: "Location not found" });
    res.json(await get("SELECT * FROM locations WHERE id=?", [req.params.id]));
  } catch (error) {
    console.error("Could not update location:", error.message);
    res.status(500).json({ error: "Could not update location" });
  }
}

export async function deleteLocation(req, res) {
  try {
    const result = await run("DELETE FROM locations WHERE id=?", [req.params.id]);
    if (!result.changes) return res.status(404).json({ error: "Location not found" });
    res.json({ message: "Location deleted" });
  } catch (error) {
    console.error("Could not delete location:", error.message);
    res.status(500).json({ error: "Could not delete location" });
  }
}

export async function nearbyLocations(req, res) {
  const { lat, lon, radius, limit, w_distance, w_rating } = req.query;
  const weightTotal = w_distance + w_rating || 1;
  const distanceWeight = w_distance / weightTotal;
  const ratingWeight = w_rating / weightTotal;
  const radians = Math.PI / 180;
  const distanceExpression = `(6371 * acos(max(-1,min(1,
    cos(?*${radians})*cos(latitude*${radians})*cos((longitude*${radians})-(?*${radians}))+
    sin(?*${radians})*sin(latitude*${radians})))))`;

  try {
    const rows = await all(
      `SELECT nearby.*, nearby.distance AS distance_km,
       (?*(COALESCE(nearby.rating,0)/5.0) + ?*(1.0-(nearby.distance/?))) AS score,
       CASE WHEN favorite.location_id IS NULL THEN 0 ELSE 1 END AS is_favorite
       FROM (SELECT *,${distanceExpression} AS distance FROM locations) nearby
       LEFT JOIN user_favorite_locations favorite
         ON favorite.location_id=nearby.id AND favorite.user_id=?
       WHERE nearby.distance<=?
       ORDER BY score DESC,COALESCE(nearby.rating,0) DESC,nearby.distance
       LIMIT ?`,
      [ratingWeight,distanceWeight,radius,lat,lon,lat,req.user?.id || -1,radius,limit]
    );
    res.json(rows.map(row => ({ ...row,
      distance_km: Number(row.distance_km.toFixed(3)), score: Number(row.score.toFixed(4))
    })));
  } catch (error) {
    console.error("Could not load nearby locations:", error.message);
    res.status(500).json({ error: "Could not load nearby locations" });
  }
}

const favorites = createFavoriteController({
  junctionTable: "user_favorite_locations", foreignKey: "location_id",
  resourceTable: "locations", resourceLabel: "Location",
  orderBy: "COALESCE(resource.rating,0) DESC,resource.id DESC"
});

export const getFavoriteLocations = favorites.list;
export const addFavoriteLocation = favorites.add;
export const removeFavoriteLocation = favorites.remove;
