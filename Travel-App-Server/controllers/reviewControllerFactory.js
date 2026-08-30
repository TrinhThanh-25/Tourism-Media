import { all, get, transaction } from "../db/queries.js";

export function createReviewController({ reviewTable, resourceColumn, resourceTable, routeParam }) {
  async function recompute(queries, resourceId) {
    const aggregate = await queries.get(
      `SELECT COALESCE(AVG(rating),0) AS rating,COUNT(*) AS review_count
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
          if (!await queries.get(`SELECT id FROM ${resourceTable} WHERE id=?`, [resourceId])) {
            throw Object.assign(new Error("Reviewed resource not found"), { status:404 });
          }
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
      try {
        res.json(await all(
          `SELECT review.id,review.user_id,review.rating,review.comment,review.created_at,user.username
           FROM ${reviewTable} review LEFT JOIN users user ON user.id=review.user_id
           WHERE review.${resourceColumn}=? ORDER BY review.created_at DESC`,
          [req.params[routeParam]]
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
          await queries.run(`UPDATE ${reviewTable} SET rating=?,comment=? WHERE id=?`, [req.body.rating,req.body.comment || null,review.id]);
          await recompute(queries,review[resourceColumn]);
        });
        res.json({id:review.id,rating:req.body.rating,comment:req.body.comment || null});
      } catch { res.status(500).json({error:"Could not update review"}); }
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
