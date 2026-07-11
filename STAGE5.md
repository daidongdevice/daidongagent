# Hermes Standalone APK - Stage 5

Status: completed PoC for real Hermes one-shot chat from the Android APK UI.

Artifacts:
- APK: `/sdcard/Download/hermes-standalone-stage5.apk`
- Project: `/data/data/com.termux/files/home/hermes-android-standalone`
- Runtime asset: `assets/hermes-runtime.zip`

What Stage 5 adds:
- Adds `runtime_payload/hermes_bridge.py`.
- Bridge creates app-private `HERMES_HOME` at:
  `/data/user/0/com.daidong.hermesstandalone/files/hermes-home`
- Bridge writes a minimal app-private `config.yaml` and `.env`.
- Bridge runs Hermes one-shot quiet mode with:
  - provider: `custom:9router`
  - model: `mimo-free/mimo-auto`
  - base URL: `http://127.0.0.1:20128/v1`
  - toolsets: `safe`
  - memory disabled for the PoC
- Android Send button now calls CPython -> `hermes_bridge.py` on a background thread.
- UI status now shows Python, Hermes, bridge, and HERMES_HOME state.
- Runtime asset manifest updated to Stage 5.
- Added needed plain SONAME library aliases/copies, including `libsqlite3.so`, so Hermes session store imports no longer warn about missing sqlite.

Build/install verification:

```text
apksigner verify --verbose build/hermes-standalone-poc.apk
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Number of signers: 1

pm install -r /data/local/tmp/hermes-standalone-stage5.apk
Success
pm clear com.daidong.hermesstandalone
Success
```

Runtime/UI verification:

```text
mCurrentFocus=Window{... com.daidong.hermesstandalone/com.daidong.hermesstandalone.MainActivity}
mFocusedApp=ActivityRecord{... com.daidong.hermesstandalone/.MainActivity}
```

App sandbox files after launch:

```text
files/hermes-runtime:
MANIFEST.txt
bin/
hermes-agent/
hermes_bridge.py
hermes_import_check.py
lib/
```

Direct sandbox bridge verification via `run-as com.daidong.hermesstandalone`:

```text
$ RT=$PWD/files/hermes-runtime; \
  LD_LIBRARY_PATH=$RT/lib \
  PYTHONHOME=$RT \
  PYTHONPATH=$RT/lib/python3.13:$RT/lib/python3.13/site-packages:$RT/hermes-agent \
  HOME=$PWD/files/hermes-home \
  $RT/bin/python $RT/hermes_bridge.py --yolo "Trả lời đúng 4 từ tiếng Việt: app Hermes chạy chưa?"

Đang chạy bình thường.
```

Actual UI Send-button verification:
- Launched the app.
- Typed: `Reply with OK only`
- Pressed `Gửi`.
- `uiautomator` dump contained the model response: `OK`.

Important notes:
- This Stage 5 build uses 9Router running in Termux as the local OpenAI-compatible endpoint. Start it with:
  `9router --no-browser --skip-update`
- If 9Router is stopped, the APK still opens and runtime checks pass, but chat returns a connection error from the bridge.
- The APK does not copy the user's main `~/.hermes/auth.json`; this avoids bundling sensitive OAuth/session tokens into the app sandbox for the PoC.

Known limitations for next stages:
- Conversations are one-shot; the UI does not yet maintain multi-turn Hermes history across messages.
- Model/provider selection is hardcoded in `hermes_bridge.py`.
- No in-app setup screen for API keys/provider settings yet.
- Terminal/file/browser tools are intentionally limited with `safe` toolset for this PoC.

Next stage idea:
- Stage 6: persist chat history in app-private storage and pass previous turns back into Hermes, plus add an in-app provider/model settings screen.
