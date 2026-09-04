#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$SCRIPT_DIR/Travel-App-Server"
ANDROID_DIR="$SCRIPT_DIR/TourismMedia"
ENV_FILE="$SCRIPT_DIR/.env"
LOG_DIR="$SCRIPT_DIR/.demo-logs"
AVD_NAME="${1:-Pixel_7}"
GPU_MODE="${TOURISM_GPU_MODE:-auto}"
SERVER_PORT="${TOURISM_SERVER_PORT:-$(sed -n 's/^PORT=//p' "$ENV_FILE" 2>/dev/null | head -n 1)}"
SERVER_PORT="${SERVER_PORT:-3000}"
SERVER_URL="http://127.0.0.1:${SERVER_PORT}/api"
ENV_API_BASE_URL="$(sed -n 's/^TOURISM_API_BASE_URL=//p' "$ENV_FILE" 2>/dev/null | head -n 1)"
export TOURISM_API_BASE_URL="${TOURISM_API_BASE_URL:-${ENV_API_BASE_URL:-http://10.0.2.2:${SERVER_PORT}/}}"
ENV_PUBLIC_BASE_URL="$(sed -n 's/^PUBLIC_BASE_URL=//p' "$ENV_FILE" 2>/dev/null | head -n 1)"
SERVER_PUBLIC_URL="${PUBLIC_BASE_URL:-${ENV_PUBLIC_BASE_URL:-${TOURISM_API_BASE_URL%/}}}"
PACKAGE_NAME="com.example.tourismmedia"
LAUNCHER_ACTIVITY="$PACKAGE_NAME/.auth.AuthActivity"

SERVER_PID=""
EMULATOR_PID=""
STARTED_SERVER=false
STARTED_EMULATOR=false
CLEANED_UP=false

info() { printf '\033[1;32m[Tourism Media]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[Lỗi]\033[0m %s\n' "$*" >&2; exit 1; }

mkdir -p "$LOG_DIR"

command -v flock >/dev/null 2>&1 || fail "Không tìm thấy flock (gói util-linux)."
LOCK_FILE="$LOG_DIR/run_demo.lock"
if [[ "${TOURISM_DEMO_LOCKED:-}" != "1" ]]; then
  # Let flock own the lock in a wrapper process and close its descriptor in the
  # script. This prevents long-lived children such as the ADB daemon from
  # inheriting the lock after this script has already exited.
  set +e
  TOURISM_DEMO_LOCKED=1 flock --nonblock --conflict-exit-code 73 --close \
    "$LOCK_FILE" "$0" "$@"
  RUN_STATUS=$?
  set -e
  if (( RUN_STATUS == 73 )); then
    fail "Một phiên run_demo.sh khác đang chạy. Hãy dừng phiên cũ bằng Ctrl+C trước."
  fi
  exit "$RUN_STATUS"
fi

cleanup() {
  [[ "$CLEANED_UP" == true ]] && return
  CLEANED_UP=true
  printf '\n'
  info "Đang dừng các tiến trình do script tạo..."
  if [[ "$STARTED_SERVER" == true && -n "$SERVER_PID" ]]; then
    kill "$SERVER_PID" 2>/dev/null || true
  fi
  if [[ "$STARTED_EMULATOR" == true && -n "$EMULATOR_PID" ]]; then
    # Stop only the emulator process created by this script. Never kill by a
    # reused adb serial because it may already belong to a newer script run.
    kill "$EMULATOR_PID" 2>/dev/null || true
    for _ in $(seq 1 10); do
      ! kill -0 "$EMULATOR_PID" 2>/dev/null && break
      sleep 1
    done
    kill -9 "$EMULATOR_PID" 2>/dev/null || true
  fi
  info "Đã dừng. Log được lưu tại $LOG_DIR"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

command -v node >/dev/null 2>&1 || fail "Chưa cài Node.js."
command -v npm >/dev/null 2>&1 || fail "Chưa cài npm."
command -v curl >/dev/null 2>&1 || fail "Chưa cài curl."

# Android Gradle Plugin needs a complete JDK, including javac and jlink. VS Code
# can expose a Java runtime that has java but no jlink, which makes
# :app:androidJdkImage fail even though the source code compiles normally.
JDK_DIR=""
JDK_CANDIDATES=(
  "${TOURISM_JAVA_HOME:-}"
  "/opt/android-studio/jbr"
  "$SCRIPT_DIR/android-studio/jbr"
  "${HOME}/android-studio/jbr"
  "/snap/android-studio/current/android-studio/jbr"
  "${JAVA_HOME:-}"
)
for candidate in "${HOME}"/.gradle/jdks/*; do
  JDK_CANDIDATES+=("$candidate")
done
for candidate in "${JDK_CANDIDATES[@]}"; do
  if [[ -n "$candidate" && -x "$candidate/bin/java" && -x "$candidate/bin/javac" && -x "$candidate/bin/jlink" ]]; then
    JDK_DIR="$candidate"
    break
  fi
done
[[ -n "$JDK_DIR" ]] || fail "Không tìm thấy JDK đầy đủ có java, javac và jlink. Hãy đặt TOURISM_JAVA_HOME tới JDK 17/21."
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
# Gradle may otherwise auto-detect an IDE-bundled runtime that has java/javac
# but no jlink. Pin both its daemon JVM and Java toolchain discovery to the
# complete JDK selected above so the script behaves consistently per machine.
GRADLE_JAVA_ARGS=(
  --no-daemon
  "-Dorg.gradle.java.home=$JDK_DIR"
  -Porg.gradle.java.installations.auto-detect=false
  -Porg.gradle.java.installations.auto-download=false
  "-Porg.gradle.java.installations.paths=$JDK_DIR"
)
info "Sử dụng JDK đầy đủ tại $JAVA_HOME"

if [[ "$GPU_MODE" == "auto" ]]; then
  if command -v nvidia-smi >/dev/null 2>&1 && command -v prime-run >/dev/null 2>&1 \
      && nvidia-smi --query-gpu=name --format=csv,noheader >/dev/null 2>&1; then
    GPU_MODE="nvidia"
  else
    GPU_MODE="host"
  fi
fi

if [[ "$GPU_MODE" == "nvidia" ]]; then
  command -v nvidia-smi >/dev/null 2>&1 || fail "Không tìm thấy nvidia-smi."
  command -v prime-run >/dev/null 2>&1 || fail "Không tìm thấy prime-run để bật NVIDIA PRIME offload."
  if ! nvidia-smi --query-gpu=name --format=csv,noheader >/dev/null 2>&1; then
    fail "Driver NVIDIA chưa hoạt động. Hãy kiểm tra nvidia-smi hoặc khởi động lại máy."
  fi
  NVIDIA_NAME="$(nvidia-smi --query-gpu=name --format=csv,noheader | head -n 1)"
  info "Đã phát hiện GPU: $NVIDIA_NAME"
fi

SDK_DIR=""
if [[ -f "$ANDROID_DIR/local.properties" ]]; then
  SDK_DIR="$(sed -n 's/^sdk\.dir=//p' "$ANDROID_DIR/local.properties" | head -n 1)"
fi
if [[ -z "$SDK_DIR" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
  SDK_DIR="$ANDROID_SDK_ROOT"
fi
[[ -n "$SDK_DIR" ]] || fail "Không tìm thấy Android SDK. Hãy mở project bằng Android Studio một lần."

ADB_BIN="$SDK_DIR/platform-tools/adb"
EMULATOR_BIN="$SDK_DIR/emulator/emulator"
[[ -x "$ADB_BIN" ]] || fail "Không tìm thấy adb tại $ADB_BIN"
[[ -x "$EMULATOR_BIN" ]] || fail "Không tìm thấy emulator tại $EMULATOR_BIN"

if ! "$EMULATOR_BIN" -list-avds | grep -Fxq "$AVD_NAME"; then
  fail "Không tìm thấy AVD '$AVD_NAME'. AVD hiện có: $($EMULATOR_BIN -list-avds | tr '\n' ' ')"
fi

if [[ ! -d "$SERVER_DIR/node_modules" ]]; then
  info "Đang cài dependency backend..."
  (cd "$SERVER_DIR" && npm install)
fi

[[ -f "$ENV_FILE" ]] || fail "Thiếu .env tại thư mục gốc. Hãy copy .env.example thành .env và đặt JWT_SECRET riêng."
AI_KEY_VALUE="$(sed -n 's/^AI_API_KEY=//p' "$ENV_FILE" | head -n 1)"
if [[ -z "$AI_KEY_VALUE" || "$AI_KEY_VALUE" == "replace-with-your-provider-api-key" ]]; then
  info "Cảnh báo: AI_API_KEY đang trống; ứng dụng vẫn chạy nhưng chatbot sẽ báo chưa được cấu hình."
fi

if curl --silent --fail "$SERVER_URL" >/dev/null 2>&1; then
  info "Backend đã chạy tại $SERVER_URL"
else
  info "Đang khởi động backend..."
  (cd "$SERVER_DIR" && PORT="$SERVER_PORT" PUBLIC_BASE_URL="$SERVER_PUBLIC_URL" npm start) >"$LOG_DIR/backend.log" 2>&1 &
  SERVER_PID=$!
  STARTED_SERVER=true
  for _ in $(seq 1 30); do
    if curl --silent --fail "$SERVER_URL" >/dev/null 2>&1; then break; fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
      tail -n 30 "$LOG_DIR/backend.log" >&2
      fail "Backend dừng bất thường."
    fi
    sleep 1
  done
  curl --silent --fail "$SERVER_URL" >/dev/null 2>&1 || fail "Backend không phản hồi sau 30 giây."
  info "Backend đã sẵn sàng tại $SERVER_URL"
fi

"$ADB_BIN" start-server >/dev/null
if "$ADB_BIN" devices | grep -q '^emulator-.*device'; then
  info "Đang dừng emulator cũ để áp dụng lại cấu hình GPU..."
  "$ADB_BIN" emu kill >/dev/null 2>&1 || true
  for _ in $(seq 1 20); do
    ! "$ADB_BIN" devices | grep -q '^emulator-' && break
    sleep 1
  done
fi

info "Đang khởi động AVD $AVD_NAME với hardware rendering..."
if [[ "$GPU_MODE" == "nvidia" ]]; then
  # prime-run sets NVIDIA PRIME offload for both OpenGL and Vulkan. This avoids
  # the unstable hybrid state where Vulkan selects NVIDIA but GLES selects AMD.
  prime-run "$EMULATOR_BIN" "@$AVD_NAME" -gpu host -no-snapshot -no-boot-anim -feature -Vulkan \
    >"$LOG_DIR/emulator.log" 2>&1 &
elif [[ "$GPU_MODE" == "software" ]]; then
  "$EMULATOR_BIN" "@$AVD_NAME" -gpu swiftshader_indirect -no-snapshot -no-boot-anim \
    >"$LOG_DIR/emulator.log" 2>&1 &
else
  "$EMULATOR_BIN" "@$AVD_NAME" -gpu host -no-snapshot -no-boot-anim -feature -Vulkan \
    >"$LOG_DIR/emulator.log" 2>&1 &
fi
EMULATOR_PID=$!
STARTED_EMULATOR=true

# Confirm the emulator process inherited NVIDIA PRIME before waiting minutes
# for Android. The renderer itself is also written to emulator.log.
sleep 2
if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
  tail -n 60 "$LOG_DIR/emulator.log" >&2
  fail "Emulator không thể khởi động bằng hardware rendering."
fi
if [[ "$GPU_MODE" == "nvidia" ]]; then
  info "NVIDIA PRIME offload đã bật; emulator đang dùng -gpu host."
fi

info "Đang chờ Android khởi động..."
"$ADB_BIN" wait-for-device
for _ in $(seq 1 180); do
  [[ "$("$ADB_BIN" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && break
  if [[ "$STARTED_EMULATOR" == true ]] && ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    tail -n 40 "$LOG_DIR/emulator.log" >&2
    fail "Emulator dừng bất thường."
  fi
  sleep 1
done
[[ "$("$ADB_BIN" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] || fail "Emulator không boot sau 180 giây."

info "Đang build APK debug..."
(cd "$ANDROID_DIR" && ./gradlew "${GRADLE_JAVA_ARGS[@]}" :app:assembleDebug)

APK_PATH="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK_PATH" ]] || fail "Không tìm thấy APK tại $APK_PATH"

info "Đang cài ứng dụng..."
"$ADB_BIN" install -r "$APK_PATH" >/dev/null
"$ADB_BIN" shell am force-stop "$PACKAGE_NAME"
"$ADB_BIN" shell am start -n "$LAUNCHER_ACTIVITY" >/dev/null

info "Ứng dụng đã mở trên $AVD_NAME."
info "Backend: $SERVER_URL"
info "Nhấn Ctrl+C để dừng backend và emulator do script tạo."

# Keep ownership of the processes and report the actual failure if either one
# exits. Short polling also avoids a long `sleep` hiding termination signals.
ADB_MISSES=0
while true; do
  if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    tail -n 80 "$LOG_DIR/emulator.log" >&2
    fail "Emulator đã dừng. Xem đầy đủ log tại $LOG_DIR/emulator.log"
  fi
  if [[ "$STARTED_SERVER" == true ]] && ! kill -0 "$SERVER_PID" 2>/dev/null; then
    tail -n 50 "$LOG_DIR/backend.log" >&2
    fail "Backend đã dừng. Xem đầy đủ log tại $LOG_DIR/backend.log"
  fi
  if "$ADB_BIN" devices | grep -q '^emulator-.*device'; then
    ADB_MISSES=0
  else
    ADB_MISSES=$((ADB_MISSES + 1))
    if (( ADB_MISSES >= 6 )); then
      tail -n 80 "$LOG_DIR/emulator.log" >&2
      fail "ADB mất kết nối emulator trong 30 giây."
    fi
  fi
  sleep 5
done
