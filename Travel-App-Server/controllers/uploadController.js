import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { UPLOAD_DIR, uploadedImageUrl } from "../config/uploads.js";

const imageTypes = {
  "image/jpeg": { extension:"jpg", valid:body => body.length >= 3 && body[0] === 0xff && body[1] === 0xd8 && body[2] === 0xff },
  "image/png": { extension:"png", valid:body => body.length >= 8 && body.subarray(0,8).equals(Buffer.from([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a])) },
  "image/webp": { extension:"webp", valid:body => body.length >= 12 && body.toString("ascii",0,4) === "RIFF" && body.toString("ascii",8,12) === "WEBP" }
};

export async function uploadImage(req, res) {
  const type = String(req.headers["content-type"] || "").split(";")[0].toLowerCase();
  const format = imageTypes[type];
  if (!format || !Buffer.isBuffer(req.body) || !format.valid(req.body)) {
    return res.status(415).json({error:"Only valid JPEG, PNG or WebP images are accepted"});
  }
  try {
    const imageDir = path.join(UPLOAD_DIR,"images");
    await fs.mkdir(imageDir,{recursive:true});
    const filename = `${Date.now()}-${crypto.randomBytes(12).toString("hex")}.${format.extension}`;
    await fs.writeFile(path.join(imageDir,filename),req.body,{flag:"wx"});
    res.status(201).json({url:uploadedImageUrl(req,filename)});
  } catch {
    res.status(500).json({error:"Image upload failed"});
  }
}
