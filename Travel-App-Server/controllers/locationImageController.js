import { all, get, run } from "../db/queries.js";

export async function listLocationImages(req, res) {
  try {
    res.json(await all(
      "SELECT id,url,caption,sort_order FROM location_images WHERE location_id=? ORDER BY sort_order,id",
      [req.params.locationId]
    ));
  } catch { res.status(500).json({error:"Could not load location images"}); }
}

export async function addLocationImage(req, res) {
  const url = req.body.url ?? req.body.url_image;
  try {
    if (!await get("SELECT id FROM locations WHERE id=?", [req.params.locationId])) {
      return res.status(404).json({error:"Location not found"});
    }
    const result = await run(
      "INSERT INTO location_images (location_id,url,sort_order,caption) VALUES (?,?,?,?)",
      [req.params.locationId,url,req.body.sort_order,req.body.caption ?? null]
    );
    res.status(201).json({
      id:result.lastID,location_id:Number(req.params.locationId),url,
      sort_order:req.body.sort_order,caption:req.body.caption ?? null
    });
  } catch { res.status(500).json({error:"Could not add location image"}); }
}

export async function updateLocationImage(req, res) {
  const values = {url:req.body.url ?? req.body.url_image,sort_order:req.body.sort_order,caption:req.body.caption};
  const entries = Object.entries(values).filter(([,value]) => value !== undefined);
  try {
    const result = await run(
      `UPDATE location_images SET ${entries.map(([key]) => `${key}=?`).join(",")}
       WHERE id=? AND location_id=?`,
      [...entries.map(([,value]) => value),req.params.imageId,req.params.locationId]
    );
    if (!result.changes) return res.status(404).json({error:"Image not found"});
    res.json({message:"Image updated"});
  } catch { res.status(500).json({error:"Could not update image"}); }
}

export async function deleteLocationImage(req, res) {
  try {
    const result = await run(
      "DELETE FROM location_images WHERE id=? AND location_id=?",
      [req.params.imageId,req.params.locationId]
    );
    if (!result.changes) return res.status(404).json({error:"Image not found"});
    res.json({message:"Image deleted"});
  } catch { res.status(500).json({error:"Could not delete image"}); }
}
