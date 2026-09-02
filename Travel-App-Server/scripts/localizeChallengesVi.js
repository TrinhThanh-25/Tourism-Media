import fs from "fs";
import path from "path";
import sqlite3 from "sqlite3";
import { fileURLToPath } from "url";

const projectDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const databases = process.argv.slice(2).map(value => path.resolve(projectDir, value));
if (!databases.length) databases.push(
  path.join(projectDir, "travel_app.template.db"),
  path.join(projectDir, "data", "travel_app.db")
);

const translations = [
  [1,"Check-in 5 địa điểm","Check-in tại 5 địa điểm bất kỳ."],
  [2,"Check-in 3 câu lạc bộ bắn cung","Ghé thăm và check-in tại 3 câu lạc bộ bắn cung."],
  [3,"Khám phá 5 điểm vui chơi","Khám phá và check-in tại 5 địa điểm giải trí."],
  [4,"Đọc thông tin 5 địa điểm","Mở và đọc phần giới thiệu của 5 địa điểm."],
  [5,"Viết 3 bài đánh giá","Chia sẻ trải nghiệm bằng 3 bài đánh giá địa điểm."],
  [6,"Đổi 2 phần thưởng","Dùng điểm để đổi thành công 2 phần thưởng."],
  [7,"Đạt 200 điểm","Tích lũy tổng cộng 200 điểm thành viên."],
  [8,"Marathon bắn cung: 10 lần check-in","Hoàn thành 10 lượt check-in tại các địa điểm bắn cung."],
  [9,"Bộ ba billiards","Check-in tại 3 địa điểm billiards khác nhau."],
  [10,"Một ngày ở công viên giải trí","Khám phá các điểm vui chơi trong một ngày đáng nhớ."],
  [11,"Check-in 10 địa điểm","Check-in tại 10 địa điểm bất kỳ."],
  [12,"Check-in 20 địa điểm","Check-in tại 20 địa điểm bất kỳ."],
  [13,"Check-in 50 địa điểm","Chinh phục cột mốc 50 địa điểm đã check-in."],
  [14,"Đạt 500 điểm","Tích lũy tổng cộng 500 điểm thành viên."],
  [15,"Đạt 1.000 điểm","Tích lũy tổng cộng 1.000 điểm thành viên."],
  [16,"Đạt 2.000 điểm","Tích lũy tổng cộng 2.000 điểm thành viên."],
  [17,"Đổi 5 phần thưởng","Dùng điểm để đổi thành công 5 phần thưởng."],
  [18,"Đổi 10 phần thưởng","Dùng điểm để đổi thành công 10 phần thưởng."],
  [19,"Marathon bắn cung: 20 lần check-in","Hoàn thành 20 lượt check-in tại các địa điểm bắn cung."],
  [20,"Nhà khám phá lịch sử và văn hóa","Check-in tại các địa danh lịch sử và văn hóa được chỉ định."],
  [21,"Nhà khám phá thiên nhiên","Check-in tại các địa điểm gần gũi với thiên nhiên."],
  [22,"Khám phá nhiều loại địa điểm","Check-in tại các địa điểm thuộc nhiều danh mục khác nhau."],
  [23,"Đọc thông tin 10 địa điểm","Mở và đọc phần giới thiệu của 10 địa điểm."],
  [24,"Viết 10 bài đánh giá","Chia sẻ trải nghiệm bằng 10 bài đánh giá địa điểm."]
];

function updateDatabase(filename) {
  if (!fs.existsSync(filename)) return Promise.resolve(false);
  const db = new sqlite3.Database(filename);
  return new Promise((resolve, reject) => {
    db.serialize(() => {
      db.run("BEGIN IMMEDIATE");
      const statement = db.prepare("UPDATE challenges SET name=?, description=? WHERE id=?");
      for (const [id,name,description] of translations) statement.run(name,description,id);
      statement.finalize(error => {
        if (error) return db.run("ROLLBACK", () => db.close(() => reject(error)));
        db.run("COMMIT", commitError => db.close(closeError =>
          commitError || closeError ? reject(commitError || closeError) : resolve(true)));
      });
    });
  });
}

for (const filename of databases) {
  const updated = await updateDatabase(filename);
  console.log(`${updated ? "Đã Việt hóa" : "Bỏ qua (không tồn tại)"}: ${filename}`);
}
