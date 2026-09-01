import bcrypt from "bcryptjs";

export const hashPassword = (password, rounds) => bcrypt.hash(password, rounds);

export const passwordMatches = (password, passwordHash) => bcrypt.compare(password, passwordHash);
