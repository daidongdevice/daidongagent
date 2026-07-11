# Hermes Standalone APK - Stage 6

Status: completed APK build for persistent Stage 6 bridge and app settings.

Artifacts:
- APK: `/sdcard/Download/hermes-standalone-stage6.apk`
- Project: `/data/data/com.termux/files/home/hermes-android-standalone`
- Runtime asset: `assets/hermes-runtime.zip`

What Stage 6 adds:
- `hermes_bridge.py` upgraded from Stage 5 to Stage 6.
- App-private settings file:
  `/data/user/0/com.daidong.hermesstandalone/files/hermes-home/bridge_settings.json`
- App-private chat history file:
  `/data/user/0/com.daidong.hermesstandalone/files/hermes-home/chat_history.json`
- `--reset-history` bridge command for the app's `New` button.
- `--status` bridge command for runtime/status diagnostics.
- Recent history injection: each successful reply appends user/assistant turns to `chat_history.json`; the next prompt includes recent turns so one-shot Hermes calls behave like a multi-turn chat.
- Android UI now includes four top buttons:
  - `YOLO` — default is `ON`
  - `Status`
  - `Settings`
  - `New`
- `Settings` opens an Android dialog for base URL, model, and toolsets, and writes `bridge_settings.json`.
- The bridge now calls `run_agent.AIAgent` directly and disables streaming for the Android bridge request path. This makes local OpenAI-compatible routers easier to support from the APK.

Default Stage 6 settings:

```json
{
  "provider": "custom:9router",
  "model": "mimo-free/mimo-auto",
  "base_url": "http://127.0.0.1:20128/v1",
  "toolsets": "safe",
  "history_turns": 8
}
```

Build/install verification:

```text
apksigner verify --verbose build/hermes-standalone-poc.apk
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Number of signers: 1

pm install -r /data/local/tmp/hermes-standalone-stage6.apk
Success
pm clear com.daidong.hermesstandalone
Success
```

App launch/UI verification:

- App launches as `com.daidong.hermesstandalone/.MainActivity`.
- `uiautomator` dump shows Stage 6 startup text.
- Header controls show: `YOLO`, `Status`, `Settings`, `New`.

Direct sandbox Stage 6 bridge verification:

Because live 9Router returned risk-control errors during testing, I used a local OpenAI-compatible verifier on `127.0.0.1:20129/v1` to validate the bridge/settings/history mechanics without relying on the external model provider.

Test settings written to app sandbox:

```json
{"provider":"custom:9router","model":"stage6-local-test","base_url":"http://127.0.0.1:20129/v1","toolsets":"safe","history_turns":8}
```

Commands run through `run-as com.daidong.hermesstandalone`:

```text
hermes_bridge.py --reset-history
=> Đã xoá lịch sử chat Stage 6.

hermes_bridge.py --yolo "Ghi nhớ mã bí mật là XANH-42. Chỉ trả lời OK."
=> OK

hermes_bridge.py --yolo "Mã bí mật tôi vừa nói là gì? Chỉ trả lời mã."
=> XANH-42

hermes_bridge.py --status
=> history_user_turns=2
=> model=stage6-local-test
=> base_url=http://127.0.0.1:20129/v1
```

This verifies:
- `bridge_settings.json` is read by the bridge.
- `chat_history.json` is created and updated.
- The second call receives and uses the previous turn context.
- Reset/status commands work from the app sandbox.

Live-provider note:

9Router was reachable, but during Stage 6 verification it returned:

```text
HTTP 400 risk_control: Detected high-frequency non-compliant requests...
```

So the final APK is built and installed with the normal 9Router default, but live model chat may fail until 9Router risk-control clears or the user changes Settings to another compatible endpoint/model.

Known limitations:
- History is injected as text context, not yet a native long-lived Hermes session resume.
- Settings dialog writes base URL/model/toolsets, but not API key UI yet.
- The app still defaults to `safe` toolset.

Next stage idea:
- Stage 7: in-app API key/provider management, native persistent Hermes session IDs, and better automated UI testing hooks/resource IDs.

Codex test configuration update:
- Built and installed `/sdcard/Download/hermes-standalone-stage6-codex.apk`.
- App sandbox now has copied existing `openai-codex` OAuth auth at `files/hermes-home/auth.json`.
- `bridge_settings.json` is set to provider `openai-codex`, model `gpt-5.5`, toolsets `safe`.
- Verified via `run-as com.daidong.hermesstandalone`: `hermes_bridge.py --yolo "Tra loi dung mot tu: OK"` returned `OK`.
- YOLO remains default ON; generated app config shows `approvals.mode: off`.

