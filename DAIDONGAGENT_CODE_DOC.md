# DaidongAgent — Tài liệu code

App Android native (Java thuần, không Gradle/AndroidX) đóng gói toàn bộ runtime
Hermes Agent (Python 3.13) vào trong APK, cho phép chat với Hermes AI qua 9Router
và điều khiển điện thoại qua Shizuku — tất cả chạy offline-first trên máy.

- Package: `com.daidong.hermesstandalone`
- Label: DaidongAgent
- Version: 0.7.0-daidongagent (versionCode 7)
- minSdk 23 / targetSdk 28
- Thư mục: `/data/data/com.termux/files/home/hermes-android-standalone`

═══════════════════════════════════════════
## 1. Kiến trúc tổng thể
═══════════════════════════════════════════

    ┌─────────────────────────────────────────────┐
    │  MainActivity (UI Java thuần, không XML)      │
    │  - Chat bubbles, YOLO/Status/Settings/New     │
    │  - Header + input + loading dots              │
    └───────────────┬───────────────┬──────────────┘
                    │               │
        (1) chat    │               │ (2) [EXEC:lệnh]
                    ▼               ▼
        ┌──────────────────┐  ┌──────────────────────┐
        │ hermes_bridge.py │  │ ShizukuUserService    │
        │ (Python subproc) │  │ (chạy uid shell 2000) │
        │  → run_agent.py  │  │  → sh -c "lệnh"       │
        │  → 9Router API   │  └──────────────────────┘
        └──────────────────┘

Hai đường thực thi độc lập:
- Đường AI: Java spawn tiến trình Python embedded → gọi Hermes core → 9Router.
- Đường điều khiển máy: Java gửi lệnh qua Shizuku binder → chạy với quyền shell.

═══════════════════════════════════════════
## 2. Thành phần file nguồn
═══════════════════════════════════════════

    hermes-android-standalone/
    ├── AndroidManifest.xml         # khai báo package, quyền, activity, service, provider
    ├── build.sh                    # build APK bằng aapt2/ecj/d8/zipalign/apksigner (không Gradle)
    ├── src/com/daidong/hermesstandalone/
    │   ├── MainActivity.java       # 952 dòng — toàn bộ UI + logic bridge + Shizuku
    │   └── ShizukuUserService.java # 97 dòng — service chạy lệnh shell qua Shizuku
    ├── res/values/styles.xml       # theme tối thiểu
    ├── assets/hermes-runtime.zip   # runtime Python 3.13 + hermes-agent, giải nén khi chạy lần đầu
    ├── runtime_payload/            # nguồn để đóng gói thành hermes-runtime.zip
    │   ├── hermes_bridge.py        # 321 dòng — cầu nối Android ↔ Hermes core
    │   ├── hermes-agent/           # source Hermes (run_agent.py, agent/, tools/, ...)
    │   └── lib/python3.13/         # site-packages
    ├── libs/                       # shizuku-*.jar (api, provider, aidl, shared)
    ├── hermes-home/                # HERMES_HOME mẫu (config, logs)
    └── STAGE3..6.md                # nhật ký phát triển từng giai đoạn

═══════════════════════════════════════════
## 3. AndroidManifest.xml
═══════════════════════════════════════════

Quyền:
- INTERNET — gọi 9Router API.
- POST_NOTIFICATIONS.
- moe.shizuku.manager.permission.API_V23 — dùng Shizuku.

Components:
- `.MainActivity` — LAUNCHER, exported.
- `.ShizukuUserService` — exported, bảo vệ bằng quyền Shizuku API_V23.
- `rikka.shizuku.ShizukuProvider` — authority `com.daidong.hermesstandalone.shizuku`.

Lưu ý: `android:debuggable="true"` — cần cho Shizuku user service debuggable(true),
nhưng KHÔNG hợp lệ để phát hành Play Store.

═══════════════════════════════════════════
## 4. MainActivity.java
═══════════════════════════════════════════

### 4.1 UI (dựng hoàn toàn bằng code, không layout XML)
- `buildUi()` — dựng header card (logo "D", tiêu đề, subtitle "AI Agent · 9Router
  + DeepSeek"), status line, hàng nút, vùng chat ScrollView, ô nhập + nút gửi.
- Palette dark theme cố định (bg, surface, accent xanh #007AFF...).
- Helper drawable: `circle()`, `roundedRect()`, `pillBg()`, `bubbleBg()` (bo góc
  khác nhau cho bubble user vs bot).
- `addMessageBubble()` — mỗi tin có nhãn người gửi, thời gian, nút copy (bot).
  Loading indicator = 3 chấm, chèn ở cuối danh sách messages.

### 4.2 Nút điều khiển
- ⚡ YOLO ↔ 🛡 Manual — toggle biến `yolo` (mặc định TRUE → approvals off).
- 📊 Status — gọi `updateRuntimeStatusWithMessage()`: kiểm tra python version,
  import Hermes, bridge --status.
- ⚙ Settings — dialog nhập Base URL / Model / Toolsets → lưu bridge_settings.json.
- ✨ New — xoá messages + gọi bridge `--reset-history`.

### 4.3 Luồng gửi tin (`sendMessage`)
1. Lấy text, add bubble user, bật loading.
2. `ensureRuntime()` đảm bảo runtime đã giải nén, kiểm tra python + bridge tồn tại.
3. Chạy nền một Thread:
   - `runHermesBridge(text, yolo)` → nhận reply từ Hermes.
   - `extractExecBlock(reply)` tìm khối `[EXEC:...]`.
   - Nếu có và Shizuku sẵn sàng → `execShizuku()` chạy lệnh, rồi
     `processExecBlocks()` thay khối [EXEC:...] bằng kết quả thực thi.
   - Về UI thread: tắt loading, add bubble "Hermes AI", cập nhật status.

### 4.4 Gọi Python bridge
- `basePythonProcess()` — dựng ProcessBuilder với env quan trọng:
  - LD_LIBRARY_PATH, LD_PRELOAD=libpython3.13.so, PYTHONHOME=runtimeDir
  - PYTHONPATH = lib/python3.13 : site-packages : hermes-agent
  - HERMES_HOME = HOME = filesDir/hermes-home
  - NO_COLOR=1, HERMES_TUI=0
- `runHermesBridge()` — chạy `bin/python hermes_bridge.py [--yolo] <text>`,
  timeout 180s (poll exitValue mỗi 300ms), gộp stdout+stderr.
- `runBridgeCommand(arg)` — cho `--reset-history`, `--status`.

### 4.5 Runtime bootstrap
- `getRuntimeDir()` = filesDir/hermes-runtime.
- `ensureRuntime()` — nếu chưa có MANIFEST.txt thì `unzipAsset("hermes-runtime.zip")`.
- `unzipAsset()` — giải nén an toàn (chống Zip Slip bằng so canonical path), set
  executable cho .py và file trong bin/.

### 4.6 Settings
- `saveBridgeSettings()` — ghi `hermes-home/bridge_settings.json`:
  provider `custom:9router`, model, base_url, toolsets, history_turns=8.
- Mặc định: base_url `http://127.0.0.1:20128/v1`, model `ds/deepseek-v4-flash`,
  toolsets `safe`.

### 4.7 Shizuku (điều khiển máy)
- `initShizuku()` — đăng ký listener binder received/dead/permission, pingBinder.
- `checkShizukuPermission()` → `requestPermission()` nếu chưa cấp.
- `bindShizukuService()` — `Shizuku.bindUserService()` với UserServiceArgs
  (daemon false, version 1, processNameSuffix "user_service", debuggable true).
- `execShizuku(command)` — gửi qua binder.transact(code 12345), nhận String kết quả.
  Kiểm tra 3 điều kiện: shizukuAvailable, shizukuGranted, serviceBound.

═══════════════════════════════════════════
## 5. ShizukuUserService.java
═══════════════════════════════════════════

Service chạy trong tiến trình riêng với UID shell (2000) do Shizuku spawn.
- Kế thừa `Binder`; hỗ trợ 2 kênh gọi:
  - Messenger (MSG_EXEC=1 / MSG_RESULT=2) — bất đồng bộ.
  - onTransact code 12345 — đồng bộ (đây là kênh MainActivity đang dùng).
  - code 16777114 = destroy → System.exit(0).
- `execCommand()` — `Runtime.exec(["/system/bin/sh","-c",command])`, đọc
  stdout+stderr, trả về text hoặc "OK (exit=N)".

Vì service chạy uid=2000 nên các lệnh Android (am start, monkey, screencap,
input, pm...) có quyền shell — mạnh hơn app thường.

═══════════════════════════════════════════
## 6. hermes_bridge.py (runtime_payload)
═══════════════════════════════════════════

Điểm nối giữa app Android và Hermes core.

### Đường dẫn
- RUNTIME = thư mục chứa bridge (hermes-runtime).
- HERMES_SRC = RUNTIME/hermes-agent, SITE = lib/python3.13/site-packages.
- HERMES_HOME = RUNTIME.parent/hermes-home.
- SETTINGS_PATH = bridge_settings.json, HISTORY_PATH = chat_history.json.

### Hàm chính
- `_ensure_paths()` — chèn hermes-agent + site-packages vào sys.path.
- `_load_settings()` — đọc/merge bridge_settings.json với DEFAULT_SETTINGS,
  clamp history_turns 0..20.
- `_ensure_hermes_home(yolo)` — GHI config.yaml động từ settings:
  - model/provider/base_url → provider `9router` (transport chat_completions).
  - approvals.mode = off (yolo) / manual.
  - toolsets, terminal.backend=local cwd=HERMES_HOME timeout 60.
  - memory tắt, display cli, redact_secrets true.
  - ghi .env `NINE_ROUTER_API_KEY=sk-local` nếu thiếu.
  - set env HERMES_HOME/HOME/HERMES_TUI/NO_COLOR...
- `_load_history()` / `_save_history()` — chat_history.json, giữ tối đa 80 mục.
- `_history_prompt()` — dựng prompt gồm:
  1. HƯỚNG DẪN SHIZUKU (tiếng Việt): dạy model dùng cú pháp `[EXEC:lệnh]` trên
     một dòng để mở app/chụp màn hình/chạy lệnh Android. Đây chính là hợp đồng
     giữa Python (sinh [EXEC:...]) và Java (bắt & chạy qua Shizuku).
  2. Lịch sử N lượt gần nhất.
  3. Tin nhắn mới.
- `run_query()` — nạp `AIAgent` từ run_agent.py, khởi tạo với quiet_mode,
  skip_memory, skip_context_files, disable streaming; gọi `run_conversation()`;
  lấy `final_response`; lưu lịch sử; in ra stdout.
- `reset_history()` / `status()` — phục vụ nút New / Status của app.
- `main()` — parse `--yolo`, `--reset-history`, `--status`, còn lại là message.

═══════════════════════════════════════════
## 7. build.sh (không Gradle)
═══════════════════════════════════════════

Build APK thủ công bằng công cụ Termux:
1. `aapt2 compile` res → resources.zip.
2. `aapt2 link` manifest + res + assets → APK shell, sinh R.java, minSdk23/target28.
3. `ecj` compile Java (classpath gồm android.jar + shizuku-*.jar).
4. `d8` .class + các shizuku jar → classes.dex (min-api 23).
5. `zip -u` nhét classes.dex vào APK, `zipalign 4`.
6. `apksigner sign` bằng keystore debug (hermes-standalone-debug.keystore,
   pass android), rồi `apksigner verify`.
Output: build/hermes-standalone-poc.apk.

═══════════════════════════════════════════
## 8. Cách chạy / build
═══════════════════════════════════════════

Build:
    cd ~/hermes-android-standalone && ./build.sh

Cài:
    pm install -r build/hermes-standalone-poc.apk
    # hoặc bản đã đóng: /sdcard/Download/DaidongAgent-v0.7.0.apk

Yêu cầu runtime khi chạy:
- Shizuku đang chạy & đã cấp quyền (cho tính năng điều khiển máy).
- 9Router local lắng nghe tại http://127.0.0.1:20128/v1 (đường AI chat).
  Đổi Base URL/Model trong nút ⚙ Settings nếu dùng endpoint khác.

═══════════════════════════════════════════
## 9. Hạn chế / Play Store (xem PLAYSTORE_DAIDONGAGENT_NOTES.md)
═══════════════════════════════════════════

- debuggable=true và keystore debug → KHÔNG hợp lệ để publish production.
- targetSdk 28 quá cũ; Play yêu cầu target mới hơn.
- Runtime thực thi file Python từ app-private storage — target SDK mới siết chặt
  việc exec file trong data ghi được → có thể vỡ khi nâng target.
- Play cần AAB (không chỉ APK), assets store, privacy policy, upload key thật.
- Hiện chưa upload được: thiếu credential Play Console / Publisher API (401).

Khuyến nghị: dùng bản này để sideload/internal test. Muốn lên Play thì tách
nhánh Play-ready hoặc làm app companion nhẹ kết nối service Hermes local/remote.
