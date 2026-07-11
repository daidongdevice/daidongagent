# Hermes Standalone APK - Stage 4

Status: completed PoC for importing Hermes inside the APK sandbox.

Artifacts:
- APK: `/sdcard/Download/hermes-standalone-stage4.apk`
- Project: `/data/data/com.termux/files/home/hermes-android-standalone`
- Runtime asset: `assets/hermes-runtime.zip`

What Stage 4 adds:
- Bundles CPython 3.13 runtime.
- Bundles Python stdlib + venv site-packages.
- Bundles Hermes source subset under `hermes-runtime/hermes-agent`.
- Bundles native shared libraries needed by Python extension modules (`libz`, `libssl`, `libcrypto`, `libsqlite3`, `libffi`, etc.).
- Adds `hermes_import_check.py` to verify important imports from inside app sandbox.

Verified in Android app sandbox via `run-as com.daidong.hermesstandalone`:

```text
Hermes Stage4 import check
python 3.13.13
prefix /data/user/0/com.daidong.hermesstandalone/files/hermes-runtime
OK openai /data/user/0/com.daidong.hermesstandalone/files/hermes-runtime/lib/python3.13/site-packages/openai/__init__.py
OK pydantic /data/user/0/com.daidong.hermesstandalone/files/hermes-runtime/lib/python3.13/site-packages/pydantic/__init__.py
OK rich /data/user/0/com.daidong.hermesstandalone/files/hermes-runtime/lib/python3.13/site-packages/rich/__init__.py
OK yaml /data/user/0/com.daidong.hermesstandalone/files/hermes-runtime/lib/python3.13/site-packages/yaml/__init__.py
OK httpx /data/user/0/com.daidong.hermesstandalone/files/hermes-runtime/lib/python3.13/site-packages/httpx/__init__.py
OK hermes_cli.main /data/user/0/com.daidong.hermesstandalone/files/hermes-runtime/hermes-agent/hermes_cli/main.py
OK run_agent /data/user/0/com.daidong.hermesstandalone/files/hermes-runtime/hermes-agent/run_agent.py
ALL_IMPORTS_OK
```

Important fixes discovered:
- After bundling site-packages, imports failed because Python extension modules needed shared libraries such as `libz.so.1`. Fixed by scanning `.so` files with `ldd` and copying required Termux libs into runtime `lib/`, including SONAME aliases.
- Must set runtime env before launching Python:
  - `LD_LIBRARY_PATH=$runtime/lib`
  - `PYTHONHOME=$runtime`
  - `PYTHONPATH=$runtime/lib/python3.13:$runtime/lib/python3.13/site-packages:$runtime/hermes-agent`
  - `HOME=$runtime`
- Target SDK remains 28 so extracted executable can run from app-private storage.

Current APK size:
- Approximately 119 MB asset runtime, final APK copied as `/sdcard/Download/hermes-standalone-stage4.apk`.

Not done yet:
- The Send button is not yet wired to `run_agent` or `hermes chat -q`.
- Config/.env/auth handling has not been migrated into app-private storage.
- Need set `HERMES_HOME` and create/import config for model provider before real chat.

Next stage:
- Stage 5: create app-private `HERMES_HOME`, copy/import current Hermes config safely, and implement a Python bridge script so Android Send button can call a real Hermes one-shot chat query.
