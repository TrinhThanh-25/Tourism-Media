import path from "node:path";
import { fileURLToPath } from "node:url";

const projectDir = fileURLToPath(new URL("..", import.meta.url));
export const UPLOAD_DIR = path.resolve(projectDir, process.env.UPLOAD_DIR || "data/uploads");

export function uploadedImageUrl(req, filename) {
  const configured = process.env.PUBLIC_BASE_URL?.trim().replace(/\/$/, "");
  const base = configured || `${req.protocol}://${req.get("host")}`;
  return `${base}/uploads/images/${filename}`;
}
