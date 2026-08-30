import { all, get, run } from "../db/queries.js";

export const createFavoriteController = ({
  junctionTable,
  foreignKey,
  resourceTable,
  resourceLabel,
  availabilityCondition = "1=1",
  availabilityError = `${resourceLabel} not found`,
  orderBy = "resource.id DESC"
}) => {
  const list = async (req, res) => {
    try {
      const rows = await all(
        `SELECT resource.*, 1 AS is_favorite
         FROM ${resourceTable} resource
         JOIN ${junctionTable} favorite ON favorite.${foreignKey}=resource.id
         WHERE favorite.user_id=? AND ${availabilityCondition}
         ORDER BY ${orderBy}`,
        [req.user.id]
      );
      res.json(rows);
    } catch {
      res.status(500).json({ error: `Could not load favorite ${resourceLabel.toLowerCase()}s` });
    }
  };

  const add = async (req, res) => {
    const resourceId = Number(req.params.id);
    if (!Number.isInteger(resourceId) || resourceId <= 0) {
      return res.status(400).json({ error: `Invalid ${resourceLabel.toLowerCase()} id` });
    }
    try {
      const resource = await get(
        `SELECT id FROM ${resourceTable} resource WHERE resource.id=? AND ${availabilityCondition}`,
        [resourceId]
      );
      if (!resource) return res.status(404).json({ error: availabilityError });
      const result = await run(
        `INSERT OR IGNORE INTO ${junctionTable} (user_id,${foreignKey}) VALUES (?,?)`,
        [req.user.id, resourceId]
      );
      res.status(result.changes ? 201 : 200).json({
        message: result.changes ? `${resourceLabel} added to favorites` : `${resourceLabel} already in favorites`
      });
    } catch {
      res.status(500).json({ error: "Could not add favorite" });
    }
  };

  const remove = async (req, res) => {
    const resourceId = Number(req.params.id);
    if (!Number.isInteger(resourceId) || resourceId <= 0) {
      return res.status(400).json({ error: `Invalid ${resourceLabel.toLowerCase()} id` });
    }
    try {
      const result = await run(
        `DELETE FROM ${junctionTable} WHERE user_id=? AND ${foreignKey}=?`,
        [req.user.id, resourceId]
      );
      res.status(result.changes ? 200 : 404).json({
        message: result.changes ? `${resourceLabel} removed from favorites` : "Favorite not found"
      });
    } catch {
      res.status(500).json({ error: "Could not remove favorite" });
    }
  };

  return { list, add, remove };
};
