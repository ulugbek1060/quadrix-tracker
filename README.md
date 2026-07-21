# Tracker

Android app that records the device location every 5 minutes in the background and POSTs it to
a REST API. Firebase is used for one thing only: distributing new versions to testers through
App Distribution. The app is not published on Google Play.

## Wiring up your backend

The API is a **placeholder** until you provide the real one. Everything you need to change is
in two files:

| What | Where |
| --- | --- |
| Base URL | `API_BASE_URL` in `app/build.gradle.kts` |
| Endpoint paths + expected contract | `data/api/ApiConfig.kt` |
| Request/response JSON shapes | `data/api/ApiModels.kt` |

The main screen shows a red "Backend not configured" warning while the base URL still points at
`api.example.com`.

### Expected contract

```
POST {base}auth/login
  { "email": "…", "password": "…",
    "device": { "device_id": "…", "imei": null, "manufacturer": "…", "model": "…",
                "os_version": "…", "sdk_int": 34, "app_version": "1.0", "app_version_code": 1 } }
  → 200 { "token": "…", "user_id": "…", "email": "…" }
  → 401 invalid credentials

POST {base}locations          Authorization: Bearer <token>
  { "device_id": "…",
    "locations": [ { "latitude": 41.3, "longitude": 69.2, "accuracy": 12.5, "altitude": 430.0,
                     "speed": 0.0, "provider": "fused", "recorded_at": 1770000000000,
                     "battery_percent": 87 } ] }
  → 2xx  batch accepted and removed from the local queue
  → 401/403  token treated as expired; fixes are kept for the next session
  → anything else  retried on the next cycle
```

Locations are sent as a **batch array**, not one per request — after an offline stretch the
whole backlog is drained in batches of 50.

Prefer renaming JSON keys with `@SerialName` in `ApiModels.kt` over renaming the Kotlin
properties, so the rest of the app stays untouched.

**If your API is plain HTTP**, Android blocks cleartext by default. Either use HTTPS, or add a
network security config permitting cleartext for your host.

## Temporary test account (debug builds only)

Until the API exists, debug builds accept a stub login:

```
test@tracker.local / test1234
```

The login screen shows a card with these credentials and a "Fill in test account" button. This
account works **offline** — it never touches the network — and stubs out uploads: the queue
drains as though the server had accepted each batch, and the fixes are written to logcat
(`LocationRepository`, tag `TEST MODE`). That makes the whole flow testable now: login →
permissions → 5-minute tracking → status screen → logout wipe.

It is genuinely absent from release builds, not just disabled. `TestAccount` exists twice —
`app/src/debug/…` with the real credentials, `app/src/release/…` as an inert twin whose methods
always return false. A `BuildConfig.DEBUG` check would not have been enough: minification is
off (`optimization { enable = false }`), so dead code is not stripped and the credentials would
sit in the release APK as readable strings. Verified: 0 occurrences in the release dex.

**Delete both files** once the real API is wired up.

## Device identification — please read

**IMEI cannot be read.** Since Android 10 (API 29), `getImei()` requires
`READ_PRIVILEGED_PHONE_STATE`, granted only to apps signed with the platform key or a carrier
certificate. A sideloaded app gets a `SecurityException` no matter what it requests. The `imei`
field is sent for completeness but is `null` on Android 10+, i.e. on essentially every device
in use today.

**`device_id` is what you should key on.** It is `ANDROID_ID`:

- no permission required
- stable across reboots, app updates and Android upgrades
- unique per device **and per app signing key** — so a debug build and a release build of the
  same app report *different* IDs, and reinstalling with a different key changes it
- reset by a factory reset

It is displayed on the main screen so a tester can read it out during enrolment. If you need an
identifier that survives factory resets, that requires enterprise enrolment (device owner via an
MDM), which is a different distribution model altogether.

## Firebase setup (updates only)

1. Create a Firebase project, add an Android app with package `com.tracker.quadrix`.
2. Download `google-services.json` into `app/`. **The build fails without it.**
3. Add testers in App Distribution, in a group named `testers` (or change `groups` in
   `app/build.gradle.kts`).

No Authentication or Firestore setup is needed — those dependencies were removed.

### Release signing

App Distribution only offers updates between builds signed with the same key. Create
`keystore.properties` in the project root (git-ignored):

```
storeFile=tracker-release.jks
storePassword=…
keyAlias=tracker
keyPassword=…
```

Without it, release builds fall back to the debug key. Note that changing the signing key later
also changes every device's `device_id`.

## Shipping a new version

1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts` — the in-app updater only
   offers builds with a **higher `versionCode`**.
2. Edit `release-notes.txt`.
3. `./gradlew assembleRelease appDistributionUploadRelease`

Authenticate first with `firebase login`, or set `FIREBASE_TOKEN` /
`GOOGLE_APPLICATION_CREDENTIALS` in CI.

## Staying alive in the background

A hard 5-minute cadence rules out WorkManager (15-minute minimum, deferred under Doze), so
tracking runs in a **foreground service** with a persistent notification. Four mechanisms keep
it up, because no single one is reliable across OEMs:

| Mechanism | Covers |
| --- | --- |
| `START_STICKY` | low-memory kills |
| `TrackerWatchdog` alarm, every 15 min | force-stop, OEM battery managers |
| `BootCompletedReceiver` | reboots, app updates, OEM quick-boot |
| Battery optimisation exemption prompt | Doze freezing the app overnight |

The watchdog and boot receiver only act when the session says tracking was on *and* a token is
still stored, so logging out genuinely stops everything.

**Caveat worth knowing:** aggressive OEM battery managers (Xiaomi, Huawei, Oppo, Samsung) kill
background services regardless of what Android's own rules allow. Those devices need the app
added to the vendor's own "protected apps" / "auto-start" list by hand — no code can do it.

## Offline behaviour

Fixes are appended to a local queue (`data/LocationQueue.kt`) *before* any upload is attempted,
so a fix is never lost to a momentary network failure. The queue is a JSON file holding up to
1000 entries (~3.5 days at one per 5 minutes); when full, the oldest are dropped first. It
drains on every 5-minute tick and immediately on reconnect, and is wiped on logout.

## Logout

Stops the service, cancels the watchdog, signs out the App Distribution tester, and erases the
token, user details, queued locations and app caches. `android:allowBackup="false"` keeps the
data out of cloud backups too.

## Permissions

Foreground location is requested first, then background location separately (Android 10+
rejects a combined request). For tracking to survive the app being closed the user must pick
**"Allow all the time"**. Notification permission is requested alongside on Android 13+.

## Building

There is no JDK on the PATH; use Android Studio's bundled one:

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```
