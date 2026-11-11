# Installing Sawn on Android — user guide

> **Direct download from the site.** The app is not on Google Play in this release. These are the exact steps you already follow for any side-loaded app, and why they work.

## Before you begin

- Android 8.0+ (API 26).
- The signed `.apk` you got from the Sawn distribution channel or from the owner.
- Heads-up: Google Play Protect may warn about an “unknown source” — this is routine for any off-store install, not proof of harm.

## Steps

1. Enable install from unknown sources for your browser/file manager (once):
   - Open the `.apk` ← if you see “installs apps from this source” ← **Settings** ← toggle on ← go back ← Install.
2. When Google Play Protect warns (“this app may be unsafe”):
   - Tap **More details** ← then **Install anyway**.
   - (Build trust the other way: compare the APK with the published SHA-256 fingerprint.)
3. Open Sawn — it will open the **setup wizard** if permissions are missing.
4. **Allow restricted settings (Android 13+)** — one step unlocks Accessibility:
   - The wizard opens **App info** directly ← tap the **⋮** menu (top right) ← **Allow restricted settings** ← return to the wizard.
5. The app will ask for, in order:
   - **Accessibility** — to block apps and read the address bar on-device (nothing leaves your phone).
   - **Local VPN permission** — for DNS filtering (it sees domain names, not contents).
   - **Notifications** — for the “protection running” notification (some OEMs kill foreground services without one).
   - **Battery exemption (recommended)** — so the system doesn’t stop protection in the background.

## Verify file integrity (SHA-256)

```
certutil -hashfile Sawn-2.2.0.apk SHA256    # Windows
sha256sum Sawn-2.2.0.apk                   # Linux / macOS
```

Compare with the fingerprint published in the update manifest:
`https://drente748.github.io/warden/updates/manifest.json` — look for the `sha256` field.
If they match, the file is intact, and it is the same file the app offers under “Check for updates”.

> The digest is published with every release, so it never goes stale when a new version ships.

## Uninstall the old build first

If you have Warden 1.x — **uninstall it, then install Sawn**. Warden and Sawn are different packages (`com.sawn.app`), and Sawn will need permissions granted fresh. Your 1.x data is not migrated (Sawn starts clean).

## Protection does not stop

- Swiping from recents does **not** stop protection — the filter runs in the background.
- It stops only from inside the app.
- If time lock / strict mode is enabled, turning protection off needs your PIN.
