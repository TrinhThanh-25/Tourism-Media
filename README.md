# Tourism Media

Repository gồm Backend Express/SQLite và ứng dụng Android. Secret, đường dẫn SDK,
database runtime và build output không được commit.

## Backend

Yêu cầu Node.js 20 trở lên:

```bash
cp .env.example .env
cd Travel-App-Server
npm install
```

Mở `.env`, thay `JWT_SECRET` bằng giá trị riêng. Đặt `PUBLIC_BASE_URL` thành địa
chỉ Backend mà thiết bị Android truy cập được để URL avatar/ảnh trip dùng được
trên các máy khác, sau đó chạy `npm start`.
Để bật chatbot, cấu hình `AI_BASE_URL`, `AI_API_KEY`, `AI_MODEL` và
`AI_API_STYLE` trong cùng file. Mặc định dùng `gemini-3.1-flash-lite` qua
endpoint tương thích Chat Completions của Google,
nhưng có thể đổi sang nhà cung cấp tương thích khác mà không build lại Android.
API key chỉ tồn tại ở backend và không được thêm vào ứng dụng.
Lần chạy đầu, server tự copy `travel_app.template.db` tới `DB_PATH`.

### Tài khoản quay demo

- Email: `demo@tourism.vn`
- Mật khẩu: `Demo@123`

Tài khoản có sẵn hồ sơ, chuyến đi riêng/công khai, mục đã lưu, check-in, tiến độ
thử thách, lịch sử điểm và voucher đang hoạt động/đã sử dụng/hết hạn.

## Android

Mở thư mục `TourismMedia` bằng Android Studio. IDE tự tạo `local.properties`
chứa `sdk.dir`; file này không được commit.

`run_demo.sh` đọc `TOURISM_API_BASE_URL` từ file `.env`. Khi build Android trực
tiếp, đặt cùng giá trị bằng biến môi trường shell hoặc Gradle property. Build
script tự bổ sung dấu `/` cuối nếu thiếu:

```bash
export TOURISM_API_BASE_URL=http://10.0.2.2:3000/
cd TourismMedia
./gradlew :app:assembleDebug
```

`10.0.2.2` dùng cho Android Emulator. Với điện thoại thật, thay bằng IP LAN của
máy chạy Backend, ví dụ `http://192.168.1.10:3000/`. Có thể đặt lâu dài trong
`~/.gradle/gradle.properties`:

```properties
TOURISM_API_BASE_URL=http://192.168.1.10:3000/
```

Không đặt JWT secret hoặc API key trong `gradle.properties` của repository.

## Chạy demo tự động

Sau khi tạo `.env` ở thư mục gốc, chạy `./run_demo.sh TEN_AVD`. Script tự dò SDK, JDK
và GPU. Các biến tùy chọn:

- `TOURISM_JAVA_HOME`: JDK 17/21 đầy đủ.
- `ANDROID_SDK_ROOT`: Android SDK nếu chưa có `local.properties`.
- `TOURISM_API_BASE_URL`: URL Backend mà Android truy cập (đọc từ `.env` hoặc shell).
- `TOURISM_SERVER_PORT`: port dùng để health-check Backend.
- `TOURISM_GPU_MODE`: `auto`, `nvidia`, `host` hoặc `software`.
