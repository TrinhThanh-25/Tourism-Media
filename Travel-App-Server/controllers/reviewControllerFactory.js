import { all, get, transaction } from "../db/queries.js";

const allow = () => null;

export function createReviewController({
  reviewTable, resourceColumn, resourceTable, routeParam,
  resourceSelect = "id", canRead = allow, canWrite = allow
}) {
  function denied(rule, resource, user) {
    const error = rule(resource, user || null);
    return error ? Object.assign(new Error(error.message), { status:error.status }) : null;
  }

  async function loadResource(queries, resourceId) {
    const resource = await queries.get(`SELECT ${resourceSelect} FROM ${resourceTable} WHERE id=?`, [resourceId]);
    if (!resource) throw Object.assign(new Error("Reviewed resource not found"), { status:404 });
    return resource;
  }

  async function recompute(queries, resourceId) {
    const aggregate = await queries.get(
      `SELECT AVG(rating) AS rating,COUNT(*) AS review_count
       FROM ${reviewTable} WHERE ${resourceColumn}=?`,
      [resourceId]
    );
    await queries.run(
      `UPDATE ${resourceTable} SET rating=?,review_count=? WHERE id=?`,
      [aggregate.rating,aggregate.review_count,resourceId]
    );
  }

  return {
    createReview: async (req, res) => {
      const resourceId = req.body[resourceColumn];
      try {
        const id = await transaction(async queries => {
          const resource = await loadResource(queries,resourceId);
          const rejection = denied(canWrite,resource,req.user);
          if (rejection) throw rejection;
          const inserted = await queries.run(
            `INSERT INTO ${reviewTable} (user_id,${resourceColumn},rating,comment) VALUES (?,?,?,?)`,
            [req.user.id,resourceId,req.body.rating,req.body.comment || null]
          );
          await recompute(queries,resourceId);
          return inserted.lastID;
        });
        res.status(201).json({
          id,user_id:req.user.id,[resourceColumn]:resourceId,
          rating:req.body.rating,comment:req.body.comment || null
        });
      } catch (error) {
        if (/UNIQUE constraint/i.test(error.message)) {
          return res.status(409).json({error:"You already reviewed this resource"});
        }
        res.status(error.status || 500).json({error:error.status ? error.message : "Could not create review"});
      }
    },

    listReviews: async (req, res) => {
      const resourceId = req.params[routeParam];
      try {
        const resource = await get(`SELECT ${resourceSelect} FROM ${resourceTable} WHERE id=?`, [resourceId]);
        if (!resource) return res.status(404).json({error:"Reviewed resource not found"});
        const rejection = denied(canRead,resource,req.user);
        if (rejection) return res.status(rejection.status).json({error:rejection.message});
        res.json(await all(
          `SELECT review.id,review.user_id,review.rating,review.comment,review.created_at,user.username
           FROM ${reviewTable} review LEFT JOIN users user ON user.id=review.user_id
           WHERE review.${resourceColumn}=? ORDER BY review.created_at DESC`,
          [resourceId]
        ));
      } catch { res.status(500).json({error:"Could not load reviews"}); }
    },

    editReview: async (req, res) => {
      try {
        const review = await get(`SELECT * FROM ${reviewTable} WHERE id=?`, [req.params.id]);
        if (!review) return res.status(404).json({error:"Review not found"});
        if (review.user_id !== req.user.id && req.user.role !== "admin") {
          return res.status(403).json({error:"Forbidden"});
        }
        await transaction(async queries => {
          const resource = await loadResource(queries,review[resourceColumn]);
          const rejection = denied(canWrite,resource,req.user);
          if (rejection) throw rejection;
          await queries.run(`UPDATE ${reviewTable} SET rating=?,comment=? WHERE id=?`, [req.body.rating,req.body.comment || null,review.id]);
          await recompute(queries,review[resourceColumn]);
        });
        res.json({id:review.id,rating:req.body.rating,comment:req.body.comment || null});
      } catch (error) {
        res.status(error.status || 500).json({error:error.status ? error.message : "Could not update review"});
      }
    },

    deleteReview: async (req, res) => {
      try {
        const review = await get(`SELECT * FROM ${reviewTable} WHERE id=?`, [req.params.id]);
        if (!review) return res.status(404).json({error:"Review not found"});
        if (review.user_id !== req.user.id && req.user.role !== "admin") {
          return res.status(403).json({error:"Forbidden"});
        }
        await transaction(async queries => {
          await queries.run(`DELETE FROM ${reviewTable} WHERE id=?`, [review.id]);
          await recompute(queries,review[resourceColumn]);
        });
        res.json({message:"Review deleted"});
      } catch { res.status(500).json({error:"Could not delete review"}); }
    }
  };
}
