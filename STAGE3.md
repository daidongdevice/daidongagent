# Hermes Standalone APK - Stage 3

Status: completed PoC for embedded CPython runtime.

Artifacts:
- APK: `/sdcard/Download/hermes-standalone-stage3.apk`
- Project: `/data/data/com.termux/files/home/hermes-android-standalone`
- Runtime asset: `assets/hermes-runtime.zip`

What Stage 3 does:
- Bundles a Termux-derived Android CPython 3.13 runtime into APK assets.
- On first app start, extracts `hermes-runtime.zip` into app-private storage:
  `/data/user/0/com.daidong.hermesstandalone/files/hermes-runtime`
- Marks `bin/python` executable during extraction.
- Sets runtime env when launching Python:
  - `LD_LIBRARY_PATH=$runtime/lib`
  - `PYTHONHOME=$runtime`
  - `PYTHONPATH=$runtime/lib/python3.13`
  - `HOME=$runtime`
- UI status now reports `Python: OK Python 3.13.13`.

Verified commands/output:

```text
Runtime: bootstrapped · Python: OK Python 3.13.13 · Hermes: present
```

Direct sandbox verification via run-as:

```text
Hermes runtime stage3 stub
python 3.13.13 (main, Apr 10 2026, 13:09:22) [Clang 21.0.0 ...]
executable /data/user/0/com.daidong.hermesstandalone/files/hermes-runtime/bin/python
prefix /data/user/0/com.daidong.hermesstandalone/files/hermes-runtime
cwd /data/user/0/com.daidong.hermesstandalone
```

Important implementation note:
- APK target SDK is set to 28 so Android permits execution from app-private extracted files. Target >=29 may block executing writable app data files.

Not done yet:
- Full Hermes source and site-packages are not bundled.
- `openai`, `pydantic-core`, `psutil`, `rich`, etc. still need to be packaged/tested inside this runtime.
- The Send button is not wired to `run_agent` yet.

Next stage:
- Stage 4: bundle Hermes source + required pure/native Python packages into runtime and prove `python -c 'import hermes_cli, run_agent'` works inside the app sandbox.
