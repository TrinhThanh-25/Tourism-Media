import jwt from 'jsonwebtoken';
import { JWT_SECRET } from '../config/auth.js';

export function authenticateJWT(req, res, next) {
  const auth = req.headers.authorization || req.headers.Authorization;
  if (!auth) return res.status(401).json({ error: 'No token provided' });
  const parts = auth.split(' ');
  const token = parts.length === 2 ? parts[1] : parts[0];
  jwt.verify(token, JWT_SECRET, (err, payload) => {
    if (err) return res.status(401).json({ error: 'Invalid token' });
    req.user = payload;
    next();
  });
}

export function optionalJWT(req, res, next) {
  const auth = req.headers.authorization || req.headers.Authorization;
  if (!auth) return next();
  const parts = auth.split(' ');
  const token = parts.length === 2 ? parts[1] : parts[0];
  jwt.verify(token, JWT_SECRET, (err, payload) => {
    if (!err) req.user = payload;
    next();
  });
}

export function requireAdmin(req, res, next) {
  if (req.user?.role !== 'admin') return res.status(403).json({ error: 'Admin access required' });
  next();
}
