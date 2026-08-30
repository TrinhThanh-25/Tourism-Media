# Kiến trúc và chức năng Travel App Server

Tài liệu này mô tả cấu trúc backend theo source hiện tại. Backend sử dụng
Node.js, Express 5 và SQLite, tập trung vào địa điểm du lịch, hành trình,
check-in, đánh giá, thử thách, điểm, phần thưởng và voucher.

## 1. Tổng quan kiến trúc

Luồng xử lý thông thường:

```text
Client
  -> server.js
  -> middleware chung (JSON, Helmet, rate limit, CORS)
  -> route theo tài nguyên
  -> middleware xác thực/phân quyền/validation
  -> controller
  -> db/queries.js
  -> SQLite
  -> JSON response
```

Các thư mục chính:

```text
Travel-App-Server/
├── config/       Cấu hình xác thực
├── controllers/  Xử lý nghiệp vụ và tạo response
├── db/           Kết nối, schema, migration và query helper
├── middleware/   JWT, admin và validation
├── routes/       Khai báo endpoint
├── validators/   Joi schema cho request
├── scripts/      Dữ liệu CSV lưu trữ/tham khảo
├── server.js     Điểm khởi động ứng dụng
└── travel_app.template.db  Database mẫu
```

## 2. Điểm khởi động `server.js`

`server.js` thực hiện các công việc theo thứ tự:

1. Đọc biến môi trường từ `.env`.
2. Import kết nối database và đợi schema/migration hoàn tất.
3. Import các router.
4. Giới hạn JSON body ở 10 KB.
5. Thêm security header bằng Helmet.
6. Áp dụng global rate limit.
7. Cấu hình CORS.
8. Mount các nhóm route.
9. Khởi động server ở `PORT`, mặc định là `3000`.

Các route gốc được mount:

| Prefix | Chức năng |
|---|---|
| `/auth` | Đăng ký, đăng nhập, refresh và logout |
| `/api/me` | Dữ liệu và thao tác của user đang đăng nhập |
| `/api/locations` | Địa điểm, nearby và yêu thích |
| `/api/locations/:locationId/images` | Gallery ảnh địa điểm |
| `/api/trips` | Hành trình cá nhân/công khai và yêu thích |
| `/api/reviews` | Review địa điểm |
| `/api/trip-reviews` | Review hành trình |
| `/api/challenges` | Thử thách và tiến độ |
| `/api/rewards` | Danh mục phần thưởng và cấp voucher |
| `/api/admin/points` | Điều chỉnh điểm bởi admin |
| `/api/chat` | Proxy tới dịch vụ chat ngoài |

`GET /api` là health endpoint đơn giản.

## 3. Controllers

### 3.1 `authController.js`

Phụ trách vòng đời xác thực:

- `register`: hash mật khẩu bằng bcrypt, tạo user và refresh token trong cùng
  transaction, sau đó trả access token và refresh token.
- `login`: tìm user theo email, kiểm tra bcrypt và cấp token. Mật khẩu plaintext
  từ dữ liệu cũ sẽ được nâng cấp sang bcrypt sau khi đăng nhập thành công.
- `logout`: revoke refresh token được gửi trong body.
- `me`: lấy hồ sơ của user từ `req.user.id`, không nhận user ID từ client.
- `refresh`: kiểm tra refresh token đã hash, revoke token cũ, tạo refresh token
  mới và cấp access token mới.

Access token chứa `id`, `username` và `role`. Refresh token được lưu trong
database dưới dạng SHA-256 thay vì plaintext.

### 3.2 `userController.js`

Xử lý dữ liệu cá nhân:

- `updateUserProfile`: cập nhật username, email, avatar, ngày sinh, giới tính và
  số điện thoại của chính user trong JWT.
- `updateUserPassword`: kiểm tra mật khẩu cũ rồi hash mật khẩu mới.
- `getCheckedInLocation`: trả lịch sử check-in kèm dữ liệu địa điểm.
- `checkInLocation`: kiểm tra địa điểm tồn tại và tạo một bản ghi check-in mới.
  Một user có thể check-in lại cùng địa điểm nhiều lần.
- `getUserChallenges`: lấy danh sách challenge và tính tiến độ động cho user.

### 3.3 `locationController.js`

Quản lý địa điểm:

- `getAllLocations`: tìm kiếm theo tên/mô tả, category, type, khoảng giá; hỗ trợ
  sắp xếp và trả `is_favorite` nếu có JWT.
- `getLocationById`: trả chi tiết location, trạng thái yêu thích và gallery ảnh.
- `nearbyLocations`: dùng công thức khoảng cách địa lý, rating và trọng số để
  xếp hạng địa điểm gần tọa độ client.
- `addLocation`, `updateLocation`, `deleteLocation`: CRUD dành cho admin.
- `getFavoriteLocations`, `addFavoriteLocation`, `removeFavoriteLocation`:
  được tạo từ favorite factory dùng chung.

Bộ lọc giá dùng hai query parameter rõ ràng:

```text
GET /api/locations?min_price=50000&max_price=200000
```

### 3.4 `locationImageController.js`

Quản lý gallery ảnh của địa điểm:

- `listLocationImages`: công khai danh sách ảnh theo thứ tự.
- `addLocationImage`: admin thêm ảnh sau khi kiểm tra location tồn tại.
- `updateLocationImage`: admin sửa URL, caption hoặc thứ tự.
- `deleteLocationImage`: admin xóa ảnh.

`locations.image_url` là ảnh đại diện; `location_images` là gallery nhiều ảnh.

### 3.5 `tripsController.js`

Quản lý hành trình:

- `listTrips`: danh sách trip đã publish, hỗ trợ tìm kiếm, rating, giá, sort và
  phân trang offset/limit.
- `getTrip`: chỉ trả trip đã publish hoặc trip thuộc user hiện tại; response gồm
  locations, images và `itinerary_by_day`.
- `createTrip`: tạo trip nháp và các `trip_locations` trong một transaction.
- `updateTrip`: chỉ chủ sở hữu được sửa nội dung và lịch trình.
- `deleteTrip`: chỉ chủ sở hữu được xóa.
- `getUserTrip`: danh sách trip của user hiện tại.
- `publishTrip`, `unpublishTrip`: chủ sở hữu đổi trạng thái công khai.
- Ba thao tác favorite được tạo từ favorite factory.

### 3.6 `challengeController.js`

Quản lý thử thách và tiến độ:

- `getAllChallenges`, `getChallengeById`: đọc challenge công khai.
- `addChallenge`, `updateChallenge`, `deleteChallenge`: CRUD admin, bao gồm
  quan hệ với location và reward.
- `joinChallenge`: user tham gia challenge còn hiệu lực.
- `getChallengeProgress`: tính tiến độ hiện tại từ dữ liệu thực.
- `completeChallenge`: kiểm tra tiến độ, cộng điểm, đánh dấu claimed và cấp
  reward trong cùng transaction.
- `setManualProgress`: admin cập nhật tiến độ thủ công.
- `logChallengeActivity`: admin ghi một hoạt động tin cậy cho user.
- `getChallengeLocations`, `getChallengeRewards`: lấy tài nguyên liên quan.
- `buildChallengeProgress`: helper dùng chung cho API challenge và `/api/me`.

Các loại tiến độ đang hỗ trợ gồm điểm, số location khác nhau, category khác
nhau, review, check-in, khoảng cách và các activity như đọc nội dung, xem video,
quiz, chia sẻ hoặc sử dụng reward. Chức năng streak đã bị loại bỏ.

### 3.7 `rewardController.js`

Quản lý phần thưởng và voucher:

- `getAllRewards`, `getRewardById`: đọc danh mục công khai.
- `addReward`, `updateReward`, `deleteReward`: CRUD admin.
- `getEligibleCatalog`: trả phần thưởng đang hoạt động và đánh dấu user có đủ
  điểm hay không.
- `redeemReward`: kiểm tra thời gian, điểm, giới hạn toàn hệ thống và giới hạn
  mỗi user; trừ điểm và sinh voucher trong transaction.
- `getUserInventory`: danh sách voucher của chính user.
- `useUserReward`: dùng voucher, ghi thời gian, activity và cộng `point_reward`
  nếu reward có cấu hình.
- `addUserReward`, `deleteUserReward`: admin cấp hoặc xóa voucher.

Mỗi voucher có code ngẫu nhiên riêng. Code không được dùng chung từ bản ghi
reward gốc.

### 3.8 `pointsController.js`

- `addTransaction`: admin cộng hoặc trừ điểm, kiểm tra số dư không âm, ghi lịch
  sử và cập nhật balance trong transaction.
- `getMyPoints`: trả điểm hiện tại của user.
- `listTransactionsForUser`: trả lịch sử điểm của user hiện tại.

Validation yêu cầu `credit` đi với số dương và `debit` đi với số âm.

### 3.9 Review controllers

`locationReviewController.js` và `tripReviewController.js` chỉ cấu hình
`reviewControllerFactory.js` cho từng loại tài nguyên.

Factory cung cấp:

- Tạo review sau khi kiểm tra tài nguyên tồn tại.
- Liệt kê review kèm username.
- Chỉ chủ review hoặc admin được sửa/xóa.
- Tính lại `rating` và `review_count` trong cùng transaction.
- Trả `409` nếu một user review cùng tài nguyên lần thứ hai.

### 3.10 `favoriteControllerFactory.js`

Factory dùng chung cho location và trip, cung cấp thao tác liệt kê, thêm và xóa
favorite. Với trip, factory được cấu hình chỉ chấp nhận trip đã publish.

## 4. Routes và quyền truy cập

Ký hiệu:

- Public: không cần JWT.
- Optional JWT: không bắt buộc JWT, nhưng có JWT hợp lệ sẽ trả thêm dữ liệu cá
  nhân như `is_favorite`.
- User: cần JWT.
- Admin: cần JWT và role `admin`.

### 4.1 Auth routes

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| POST | `/auth/register` | Public | Đăng ký |
| POST | `/auth/login` | Public | Đăng nhập |
| POST | `/auth/refresh` | Public + refresh token | Xoay vòng token |
| POST | `/auth/logout` | Public + refresh token tùy chọn | Revoke token |

Register, login và refresh có rate limit riêng.

### 4.2 User routes

Tất cả route dưới `/api/me` đều cần JWT.

| Method | Endpoint | Chức năng |
|---|---|---|
| GET/PATCH | `/api/me` | Đọc/cập nhật hồ sơ |
| POST | `/api/me/password` | Đổi mật khẩu |
| GET/POST | `/api/me/locations` | Xem/tạo check-in |
| GET | `/api/me/challenges` | Challenge và tiến độ |
| GET | `/api/me/points` | Số dư điểm |
| GET | `/api/me/point-transactions` | Lịch sử điểm |
| GET | `/api/me/rewards` | Danh mục phù hợp |
| POST | `/api/me/rewards/:rewardId/redeem` | Đổi điểm lấy voucher |
| GET | `/api/me/vouchers` | Kho voucher |
| POST | `/api/me/vouchers/:voucherId/use` | Sử dụng voucher |

### 4.3 Location routes

| Method | Endpoint | Quyền |
|---|---|---|
| GET | `/api/locations` | Optional JWT |
| GET | `/api/locations/nearby` | Optional JWT |
| GET | `/api/locations/:id` | Optional JWT |
| GET | `/api/locations/me/favorites` | User |
| POST/DELETE | `/api/locations/:id/favorite` | User |
| POST | `/api/locations` | Admin |
| PUT/DELETE | `/api/locations/:id` | Admin |
| GET | `/api/locations/:locationId/images` | Public |
| POST/PATCH/DELETE | `/api/locations/:locationId/images/...` | Admin |

### 4.4 Trip routes

| Method | Endpoint | Quyền |
|---|---|---|
| GET | `/api/trips` | Optional JWT |
| GET | `/api/trips/:id` | Optional JWT, có kiểm tra publish/owner |
| GET | `/api/trips/me` | User |
| GET | `/api/trips/me/favorites` | User |
| POST/DELETE | `/api/trips/:id/favorite` | User |
| POST | `/api/trips` | User |
| PUT/DELETE | `/api/trips/:id` | Owner |
| POST | `/api/trips/:id/publish` | Owner |
| POST | `/api/trips/:id/unpublish` | Owner |

### 4.5 Challenge routes

Đọc challenge, locations và rewards là public. Join, complete và xem tiến độ cần
JWT. Tạo, sửa, xóa, ghi activity và chỉnh tiến độ thủ công cần admin.

### 4.6 Review routes

Đọc review là public. Tạo review cần JWT. Sửa/xóa cần JWT và được controller
kiểm tra owner hoặc admin.

### 4.7 Reward và points routes

Đọc reward là public. CRUD reward, cấp/xóa voucher và điều chỉnh điểm đều cần
admin. Đổi điểm, xem hoặc sử dụng voucher nằm dưới `/api/me` để user ID luôn lấy
từ JWT.

## 5. Middlewares

### 5.1 `middleware/auth.js`

- `authenticateJWT`: đọc Bearer token, verify chữ ký và gán payload vào
  `req.user`; trả `401` nếu thiếu hoặc token không hợp lệ.
- `optionalJWT`: cho phép request không có token; nếu token hợp lệ thì gán
  `req.user`.
- `requireAdmin`: kiểm tra `req.user.role === "admin"`; trả `403` nếu không đúng.

### 5.2 `middleware/validate.js`

`validateSchema(schema, source)` dùng Joi để:

- Kiểm tra body hoặc query.
- Chuyển đổi kiểu dữ liệu an toàn.
- Loại bỏ field không được khai báo.
- Trả toàn bộ lỗi validation với HTTP `400`.
- Xử lý riêng `req.query` do Express 5 cung cấp thuộc tính chỉ đọc.

## 6. Validators

- `validators/auth.js`: register, login, refresh token và logout.
- `validators/user.js`: cập nhật profile, đổi mật khẩu và check-in.
- `validators/resources.js`: location, nearby, trip, challenge, reward, voucher,
  location image, activity, manual progress và giao dịch điểm.
- Hai review route hiện khai báo schema rating/comment ngay trong file route.

## 7. Database

### 7.1 `db/connect.js`

- Xác định `DB_PATH`.
- Nếu database runtime chưa có, copy từ `travel_app.template.db`.
- Bật khóa ngoại SQLite.
- Tạo bảng còn thiếu từ schema hiện tại.
- Chạy migration chưa từng áp dụng.
- Tạo index.
- Cấp role admin cho email trong `ADMIN_EMAILS`.

### 7.2 `db/schema.js`

Chứa schema nguồn và index của các nhóm bảng:

- Người dùng/xác thực: `users`, `user_refresh_tokens`.
- Địa điểm: `locations`, `location_images`, `user_location`,
  `user_favorite_locations`, `location_reviews`.
- Hành trình: `trips`, `trip_locations`, `trip_images`,
  `user_favorite_trips`, `trip_reviews`.
- Thử thách: `challenges`, `challenge_location`, `challenge_reward`,
  `user_challenge`, `user_activity`.
- Điểm/phần thưởng: `points_transactions`, `rewards`, `user_reward`.

`user_location` là bảng lịch sử, có khóa chính `id` và `checked_in_at`; không còn
khóa unique user-location. Hai bảng review có unique index theo user và tài
nguyên.

### 7.3 `db/migrations.js`

Bảng `schema_migrations` lưu version đã chạy. Mỗi migration được bọc trong
transaction và chỉ thực thi một lần.

| Version | Nội dung |
|---|---|
| 1 | Xóa bảng thuộc chức năng không còn sử dụng |
| 2 | Đổi `tripsLocation` thành `trip_locations` |
| 3 | Chuyển check-in thành lịch sử và loại review trùng |
| 4 | Chuẩn hóa timestamp cũ sang ISO-8601 UTC |
| 5 | Xóa challenge streak |
| 6 | Chuẩn hóa thời hạn voucher |

### 7.4 `db/queries.js`

- `run`: INSERT, UPDATE, DELETE; trả `lastID` và `changes`.
- `get`: lấy một dòng.
- `all`: lấy nhiều dòng.
- `transaction`: chạy nhiều thao tác trong `BEGIN IMMEDIATE`/`COMMIT`, rollback
  nếu lỗi. Transaction được xếp hàng để tránh nhiều transaction dùng chung một
  SQLite connection cùng lúc.

## 8. Cấu hình và bảo mật

`config/auth.js` tập trung cấu hình JWT, thời hạn access token, thời hạn refresh
token và bcrypt rounds. Production bắt buộc có `JWT_SECRET`.

Các biến môi trường chính:

| Biến | Ý nghĩa |
|---|---|
| `DB_PATH` | Đường dẫn database runtime |
| `PORT` | Cổng server |
| `JWT_SECRET` | Khóa ký JWT |
| `JWT_EXPIRES_IN` | Thời hạn access token |
| `REFRESH_TOKEN_TTL_DAYS` | Số ngày tồn tại refresh token |
| `BCRYPT_ROUNDS` | Cost bcrypt |
| `ADMIN_EMAILS` | Email được cấp admin khi startup |
| `CORS_ORIGIN` | Origin frontend được phép |
| `RATE_LIMIT_WINDOW_MS` | Cửa sổ rate limit |
| `RATE_LIMIT_MAX` | Số request tối đa trong cửa sổ |
| `CHAT_API_URL` | Dịch vụ chat được proxy |

## 9. Các lỗi và hạn chế còn lại

Phần này phản ánh source tại thời điểm tài liệu được tạo. Các mục nhỏ vẫn được
liệt kê vì có thể hữu ích khi trình bày hoặc phát triển tiếp.

### Mức ưu tiên vừa

1. **Tiến độ challenge vẫn tính trên toàn bộ lịch sử.** Check-in, review, điểm
   và activity trước ngày bắt đầu hoặc trước lúc user join vẫn có thể được tính.
   Nên thêm điều kiện thời gian theo `start_date`, `end_date` và `joined_at`.

2. **Manual progress không có tác dụng với phần lớn challenge động.** Giá trị
   `user_challenge.progress` chỉ là fallback; challenge check-in, review, điểm và
   activity được tính lại từ bảng nguồn. Nên bỏ endpoint manual hoặc thiết kế
   trường `manual_adjustment` riêng.

3. **Cấp reward từ challenge chưa áp dụng đầy đủ quy tắc reward.** Luồng này
   chưa kiểm tra `start_date`, `end_date`, `max_uses`; đồng thời bỏ qua reward nếu
   user từng có cùng reward, kể cả voucher cũ đã dùng hoặc hết hạn.

4. **Admin cấp voucher trực tiếp chưa kiểm tra giới hạn.** `addUserReward` chưa
   kiểm tra reward/user tồn tại trước, trạng thái hoạt động, `max_uses` hoặc
   `per_user_limit`; lỗi khóa ngoại hiện có thể chỉ trả `500`.

5. **Trip image mới chỉ có phần đọc.** `getTrip` trả `trip_images`, nhưng chưa có
   API thêm, sửa và xóa ảnh hành trình.

6. **Trip có thể publish khi chưa có địa điểm.** Hiện chỉ kiểm tra owner, chưa
   yêu cầu lịch trình tối thiểu hoặc nội dung đầy đủ.

7. **Cập nhật email chưa chuẩn hóa chữ thường.** Register/login dùng email chữ
   thường, nhưng `PATCH /api/me` có thể lưu email viết hoa; việc đăng nhập sau đó
   có thể không tìm thấy do truy vấn SQLite đang so sánh theo kiểu phân biệt hoa
   thường.

### Mức ưu tiên thấp

8. **Validation chưa bao phủ mọi URL parameter và query.** Các payload tạo/sửa
   chính đã có Joi, nhưng nhiều `:id`, query của trip và thao tác favorite vẫn
   dựa vào ép kiểu hoặc SQLite.

9. **Schema review còn nằm trong route.** Hai review route khai báo Joi schema
   giống nhau tại hai file; có thể chuyển sang `validators/resources.js` để tránh
   lặp hoàn toàn.

10. **Chưa có middleware 404 và error handler chung.** Controller tự bắt lỗi và
    tạo response, dẫn tới thông báo/log chưa đồng đều; route không tồn tại có thể
    nhận response mặc định của Express.

11. **Nhiều `catch` không log lỗi gốc.** Một số controller chỉ trả thông báo
    chung, khiến việc tìm lỗi dữ liệu trong lúc demo khó hơn.

12. **Danh sách lớn chưa phân trang đồng đều.** Locations, challenges, rewards,
    reviews, check-in và lịch sử điểm có thể trả toàn bộ dữ liệu; chỉ trip có
    limit/offset rõ ràng.

13. **`getUserChallenges` có thể tạo nhiều query.** Mỗi challenge tính tiến độ
    bằng ít nhất một truy vấn riêng. Với dữ liệu bài tập nhỏ không đáng kể, nhưng
    số lượng challenge lớn sẽ tạo mô hình N+1 query.

14. **Role trong JWT có thể cũ.** Nếu role bị thay đổi trong database, access
    token hiện tại vẫn giữ role cũ cho đến khi token hết hạn.

15. **`optionalJWT` bỏ qua token sai.** Request có token không hợp lệ vẫn được
    xem như khách thay vì trả `401`. Đây có thể là chủ ý cho endpoint public,
    nhưng đôi khi gây khó phát hiện frontend đang gửi token lỗi.

16. **Refresh token cũ chưa có tác vụ dọn dẹp.** Token revoked hoặc hết hạn vẫn
    nằm trong bảng và sẽ tăng dần theo số lần đăng nhập/refresh.

17. **Logout bỏ qua lỗi database.** API luôn trả thành công kể cả khi thao tác
    revoke gặp lỗi, vì lỗi được catch rỗng.

18. **CORS mặc định là `*`.** Phù hợp demo local; khi deploy thật nên bắt buộc
    cấu hình origin cụ thể.

19. **Chat proxy chưa yêu cầu JWT và chưa có timeout.** Khi cấu hình
    `CHAT_API_URL`, endpoint công khai có thể bị dùng nhiều và request tới dịch vụ
    ngoài có thể chờ lâu.

20. **Xóa admin là hard delete.** Xóa location/challenge/reward có thể cascade
    sang dữ liệu liên quan. Với hệ thống thật nên cân nhắc soft delete hoặc bước
    xác nhận; với bài tập hiện tại vẫn chấp nhận được.

21. **SQLite và transaction queue chỉ phù hợp quy mô nhỏ.** Mọi transaction ghi
    được xếp hàng trên một connection. Đây là lựa chọn hợp lý cho demo môn học,
    nhưng không phù hợp tải ghi lớn hoặc nhiều instance server.

22. **Chưa có bộ test tự động.** Bộ test đã được loại bỏ theo yêu cầu, nên việc
    kiểm tra regression hiện dựa vào syntax check, schema check và smoke-test thủ
    công.

23. **Một số controller vẫn có phong cách format khác nhau.** Location, trip và
    challenge đã được làm rõ; reward, auth, points và user vẫn còn một số câu
    lệnh dài hoặc nhiều thao tác trên cùng dòng, có thể tiếp tục chạy Prettier để
    đồng nhất hình thức.

24. **Các CSV archive có dữ liệu trùng hoặc legacy.** Cặp file cleaned/original
    và `location_reward.csv` không được runtime sử dụng. Chỉ nên xóa sau khi xác
    nhận không cần làm dữ liệu tham khảo cho bài tập.
