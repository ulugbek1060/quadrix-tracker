# Tracker

Android app that records the device location every 5 minutes in the background and POSTs it to
a REST API. This branch (`play-store`) is prepared for **Google Play** distribution — the
Firebase App Distribution self-update, IMEI reading, battery-optimisation override, and the
hardcoded test account have all been removed, since Play prohibits or restricts them.

## Wiring up your backend

The API is a **placeholder** until you provide the real one. Change two files:

| What | Where |
| --- | --- |
| Base URL | `API_BASE_URL` in `app/build.gradle.kts` |
| Endpoint paths + contract | `data/api/ApiConfig.kt` |
| Request/response JSON shapes | `data/api/ApiModels.kt` |

The main screen shows a red "Backend not configured" warning while the base URL still points at
`api.example.com`.

### Expected contract

```
POST {base}auth/login
  { "email": "…", "password": "…",
    "device": { "device_id": "…", "manufacturer": "…", "model": "…",
                "os_version": "…", "sdk_int": 34, "app_version": "1.0", "app_version_code": 1 } }
  → 200 { "token": "…", "user_id": "…", "email": "…" }
  → 401 invalid credentials

POST {base}locations          Authorization: Bearer <token>
  { "device_id": "…",
    "locations": [ { "latitude": …, "longitude": …, "accuracy": …, "altitude": …,
                     "speed": …, "provider": "fused", "recorded_at": <epoch ms>,
                     "battery_percent": 87 } ] }
  → 2xx  batch accepted and removed from the local queue
  → 401/403  token treated as expired; fixes are kept for the next session
```

Locations are sent as a **batch array**. If your API is plain HTTP, add a network security
config permitting cleartext for your host (Play prefers HTTPS).

## Device identification

Uses `ANDROID_ID` (`device_id`): no permission, stable across reboots and app updates, unique
per device + app signing key, reset only by a factory reset. IMEI is **not** used — Google Play
restricts non-resettable hardware identifiers.

## How it works

- **Tracking** runs in a foreground service with an ongoing notification, one fix every
  5 minutes. Resumes after reboot via `BootCompletedReceiver`; a `TrackerWatchdog` alarm and
  `START_STICKY` bring it back if it is killed.
- **Offline** fixes are queued to disk (`data/LocationQueue.kt`, up to 1000 entries) and flushed
  on reconnect. `ConnectivityObserver` checks `NET_CAPABILITY_VALIDATED`.
- **Logout** stops tracking and erases the token, user details, queued locations and caches.

## Permissions

Foreground location first, then background ("Allow all the time") separately — Android 11+
requires the user to pick that in Settings. `PermissionGateScreen` shows a **prominent
disclosure** (what is collected, that it happens in the background, and why) before the request,
as Google Play requires. The app blocks until all-time location is granted.

---

## Publishing to Google Play — checklist

Publishing an app that collects background location requires more than the APK:

1. **App Bundle (.aab), not APK.** Play requires `.aab`:
   ```
   ./gradlew bundleRelease   # output: app/build/outputs/bundle/release/app-release.aab
   ```
2. **Privacy policy** — a public URL describing what data you collect (location) and how it is
   used. Required in the Play Console listing.
3. **Data safety form** — declare location collection, whether it is shared, encryption in
   transit, etc.
4. **Location permissions declaration** — background location (`ACCESS_BACKGROUND_LOCATION`)
   triggers a mandatory review: you submit a short **video** showing the in-app prominent
   disclosure + consent, and justify why background location is core to the feature.
5. **Prominent disclosure & consent** — already implemented in `PermissionGateScreen`. Keep it;
   the reviewer looks for it.
6. **Foreground service type declaration** — the `location` FGS type is declared in the
   manifest; on the Play Console you also fill the foreground-service-use form.
7. **Content rating, target audience, store listing** — standard.

⚠️ **Play requires transparency and consent.** An app that tracks people without their
knowledge will be rejected under the User Data / stalkerware policies. This build discloses
tracking to the user by design; keep it that way.

Updates on Play are handled by Play itself — do **not** re-add any in-app self-update mechanism,
it violates the Device and Network Abuse policy.

## Release signing

`keystore.properties` in the project root (git-ignored) provides the upload key:

```
storeFile=tracker-release.jks
storePassword=…
keyAlias=tracker
keyPassword=…
```

Back up the keystore and password — losing them means you cannot update the app on Play.

## Building

There is no JDK on the PATH; use Android Studio's bundled one:

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew bundleRelease      # for Play
./gradlew assembleRelease    # APK for sideload testing
```
