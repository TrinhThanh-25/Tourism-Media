# Travel App Server

Backend Node.js/Express và SQLite cho địa điểm, hành trình, đánh giá, check-in,
thử thách, điểm, phần thưởng và voucher.

## Cài đặt

```bash
cp ../.env.example ../.env
npm install
npm start
```

`travel_app.template.db` là dữ liệu mẫu chỉ đọc. Lần chạy đầu, ứng dụng tự sao
chép nó tới `DB_PATH` (mặc định `data/travel_app.db`); database runtime không được
commit vào Git.

Schema hiện tại nằm trong `db/schema.js`. Các thay đổi dữ liệu nằm trong
`db/migrations.js` và được ghi nhận ở bảng `schema_migrations`, nên mỗi migration
chỉ chạy một lần.

Backend luôn đọc `.env` tại thư mục gốc của repository, kể cả khi `npm start`
được gọi bên trong `Travel-App-Server`. Mọi môi trường đều bắt buộc cấu hình
`JWT_SECRET`. Đặt `ADMIN_EMAILS` thành danh
sách email phân cách bằng dấu phẩy để cấp role admin khi khởi động. Ảnh upload
được lưu trong `UPLOAD_DIR`; `PUBLIC_BASE_URL` phải là địa chỉ mà emulator hoặc
điện thoại có thể truy cập để URL ảnh dùng được trên các client khác.

Chatbot tư vấn dùng một API tương thích OpenAI ở backend. Endpoint, key, model
và kiểu API được đổi qua `AI_BASE_URL`, `AI_API_KEY`, `AI_MODEL` và
`AI_API_STYLE` trong `.env`; Android không chứa các giá trị này và không cần
build lại. `AI_API_STYLE` nhận `responses` hoặc `chat_completions`.
Mặc định dự án dùng free tier của Google Gemini với model
`gemini-3.1-flash-lite`, endpoint OpenAI-compatible và `chat_completions`.
Tạo API key tại Google AI Studio rồi dán vào `AI_API_KEY` trong `.env`. Có thể
điều chỉnh reasoning, timeout, token trả lời và rate limit bằng
`AI_REASONING_EFFORT`, `AI_TIMEOUT_MS`,
`AI_MAX_OUTPUT_TOKENS`, `AI_RATE_LIMIT_MAX`.

Ví dụ đổi sang một dịch vụ hoặc model server tương thích Chat Completions:

```env
AI_BASE_URL=https://provider.example.com/v1
AI_API_KEY=your-private-key
AI_MODEL=provider-model-name
AI_API_STYLE=chat_completions
AI_REASONING_EFFORT=none
```

Với model server chạy cục bộ không kiểm tra Bearer token, có thể đặt
`AI_API_KEY=local`; SDK vẫn yêu cầu biến này có giá trị. Provider dùng giao thức
độc quyền khác Responses/Chat Completions cần thêm adapter backend tương ứng.

## Xác thực và phân quyền

- Mật khẩu luôn được hash bằng bcrypt trước khi lưu và chỉ được kiểm tra bằng bcrypt.
- Refresh token chỉ được lưu dưới dạng SHA-256 và được xoay vòng khi refresh.
- JWT chứa `id`, `username`, `role`; thao tác cá nhân luôn lấy user từ JWT.
- Tạo thử thách/phần thưởng, cấp voucher và chỉnh điểm yêu cầu role `admin`.

Gửi access token bằng header `Authorization: Bearer <token>`.

## API

- `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`.
- `/api/me`: đọc/cập nhật hồ sơ cá nhân (bao gồm `avatar_url`).
- `/api/me/password`, `/api/me/locations`, `/api/me/activity/location-read`.
- `/api/uploads/images`: upload JPEG/PNG/WebP tối đa 8 MB, yêu cầu JWT.
- `/api/me/challenges`, `/api/me/points`, `/api/me/point-transactions`.
- `/api/me/rewards`, `/api/me/rewards/:rewardId/redeem`.
- `/api/me/vouchers`, `/api/me/vouchers/:voucherId/use`.
- `/api/locations`, `/api/reviews`, `/api/trips`, `/api/trip-reviews`.
- `/api/ai/chat`: chatbot tư vấn dựa trên địa điểm thật trong database, yêu cầu JWT.
- `/api/challenges`: đọc công khai; thao tác cá nhân cần JWT; tạo mới cần admin.
- `/api/rewards`: đọc công khai; tạo/cấp/xóa voucher cần admin.
- `/api/admin/points/transactions`: chỉnh điểm thủ công, chỉ admin.

API quản trị dùng JWT admin:

- `POST`, `PUT /:id`, `DELETE /:id` trên `/api/locations`.
- `POST`, `PUT /:id`, `DELETE /:id` trên `/api/challenges`.
- `POST`, `PUT /:id`, `DELETE /:id` trên `/api/rewards`.

Lọc giá địa điểm bằng `min_price` và `max_price`, ví dụ
`/api/locations?min_price=50000&max_price=200000`.

Các timestamp hệ thống được lưu dưới dạng ISO-8601 UTC, ví dụ
`2026-08-27T10:30:00.000Z`. `user_location` là lịch sử check-in và cho phép một
người check-in lại cùng địa điểm ở các thời điểm khác nhau.

## Mô hình ảnh địa điểm

`locations.image_url` là ảnh đại diện dùng trong danh sách/thẻ địa điểm. Bảng
`location_images` chứa thư viện nhiều ảnh, thứ tự hiển thị và chú thích của địa
điểm. Hai nguồn này có vai trò riêng, không phải dữ liệu trùng lặp.
