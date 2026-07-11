# DaidongAgent - Play Store upload notes

## Current build produced

- App label: DaidongAgent
- Package: com.daidong.hermesstandalone
- Version: 0.7.0-daidongagent / versionCode 7
- APK: /sdcard/Download/DaidongAgent-v0.7.0.apk
- Size: ~116 MB
- Signing: local debug/upload-style keystore at hermes-standalone-debug.keystore, not a user-controlled production upload key
- SDK: minSdk 23, targetSdk 28

## Verified locally

- APK builds successfully with aapt2/ecj/d8/zipalign/apksigner.
- apksigner verify passes v1/v2/v3 signatures.
- Installed on the Android device with pm install -r.
- Launched activity: com.daidong.hermesstandalone/.MainActivity.
- UI shows title DaidongAgent and runtime status.

## Why it was not uploaded to Google Play yet

Google Play upload needs either:

1. Google Play Console browser session with access to the developer account, or
2. Android Publisher API service-account JSON that has permission on the Play Console account/app.

This device currently has no valid Google Play Console/API credential available. A test call to Android Publisher API returned 401 UNAUTHENTICATED.

## Additional Play Store blockers to resolve before production/public release

- New Play Store apps normally require Android App Bundle (.aab), not only APK.
- Current targetSdkVersion is 28. Google Play public release will require a much newer target SDK.
- The current embedded-runtime approach executes a bundled Python runtime from app-private storage; raising target SDK may break this because Android restricts executing files from writable app data on newer target SDKs.
- A real production upload key should be generated and stored securely. Do not rely on the current debug keystore for public release.
- Store listing assets are still needed: app icon, feature graphic, screenshots, short/long description, privacy policy URL, content rating, data safety, app category, support email.

## Suggested next step

Use this build for sideload/internal testing now. For Play Store, create a Play-ready branch that either:

- packages a compliant native/runtime design that works with current target SDK, then builds an AAB, or
- publishes a lighter companion app that connects to a local/remote DaidongAgent service instead of embedding the full Termux-derived runtime.
