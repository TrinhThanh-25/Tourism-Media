import db from "./connect.js";

export const run = (sql, params = []) => new Promise((resolve, reject) => {
  db.run(sql, params, function (error) {
    if (error) reject(error);
    else resolve({ lastID: this.lastID, changes: this.changes });
  });
});

export const get = (sql, params = []) => new Promise((resolve, reject) => {
  db.get(sql, params, (error, row) => error ? reject(error) : resolve(row));
});

export const all = (sql, params = []) => new Promise((resolve, reject) => {
  db.all(sql, params, (error, rows) => error ? reject(error) : resolve(rows));
});

let transactionQueue = Promise.resolve();

export function transaction(work) {
  const execute = async () => {
    await run("BEGIN IMMEDIATE");
    try {
      const result = await work({ run, get, all });
      await run("COMMIT");
      return result;
    } catch (error) {
      await run("ROLLBACK").catch(() => {});
      throw error;
    }
  };
  const pending = transactionQueue.then(execute, execute);
  transactionQueue = pending.catch(() => {});
  return pending;
}
