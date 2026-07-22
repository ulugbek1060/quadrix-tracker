# Tracker

Android app that records the device location every 5 minutes in the background and POSTs it to
a REST API. New versions are shipped by hosting a signed APK and letting the app download and
install it in place — there is no Firebase and no Google Play involvement.

## Wiring up your backend

The backend contract lives in `doc/api-doc.md`. Everything the app needs to reach it is in
three files:

| What | Where |
| --- | --- |
| Base URL | `API_BASE_URL` in `app/build.gradle.kts` (must end with `/`) |
| Endpoint paths | `data/api/ApiConfig.kt` |
| Request/response JSON shapes | `data/api/ApiModels.kt` |

The main screen shows a red "Backend not configured" warning while the base URL still points at
`api.example.com`.

### Contract

The auth/location endpoints use the envelope `{ status, data, message, errors }`. Updates are
driven by a **separate, unenveloped version endpoint** (see below), not by these responses.

```
POST {base}api/tablet/auth/request-otp/    { email }
  → data: { email, expires_in, resend_after }

POST {base}api/tablet/auth/verify-otp/     { email, code, device_id }
  → data: { tokens: { access, refresh }, user: {…}, device_id, client_type }

GET  {base}api/tablet/auth/me/             Authorization: Bearer <access>
  → data: { id, username, email, name, role, company_id, client_type }

POST {base}api/tablet/location/            Authorization: Bearer <access>
  { device_id, latitude, longitude, heading, speed }
  → data: { device_id }

POST {base}api/token/refresh/              { refresh }
  → { access }   (accepted at the root or inside data)

GET  {base}api/tablet/app/version/         (unauthenticated)
  → { version, download_url }              (flat — no envelope)
```

Auth is OTP: request a code by email, then verify it with the code plus this device's
`device_id`. Verify returns a JWT **access**/**refresh** pair. The access token is sent on every
authenticated call; on a `401` the client transparently exchanges the refresh token for a new
access token (`/api/token/refresh/`) and replays the request. When refresh itself fails the
session is cleared and the user is sent back to sign-in.

Locations are sent **one fix per request**, not batched. Fixes recorded while offline are queued
and drained one POST at a time on reconnect.

**If your API is plain HTTP**, Android blocks cleartext by default. Either use HTTPS, or add a
network security config permitting cleartext for your host.

## Device identification — please read

**`device_id` is what the backend keys on.** It is `ANDROID_ID`:

- no permission required
- stable across reboots, app updates and Android upgrades
- unique per device **and per app signing key** — so a debug build and a release build of the
  same app report *different* IDs, and reinstalling with a different key changes it
- reset by a factory reset

It is displayed on the main screen so an operator can read it out during enrolment.

**IMEI is display-only.** Since Android 10 (API 29) `getImei()` requires
`READ_PRIVILEGED_PHONE_STATE`, granted only to platform-signed, carrier or device-owner apps; a
sideloaded app gets a `SecurityException`. So the status screen shows the IMEI only where the
platform still allows it, and nothing is sent to the backend. If you need an identifier that
survives factory resets, that requires enterprise enrolment (device owner via an MDM).

## In-app updates (self-hosted)

There is no app store and no Firebase. Updates are driven by the dedicated
`GET api/tablet/app/version/` endpoint, which returns `{ version, download_url }`:

1. `UpdateManager.refreshVersion()` polls the endpoint and compares `version` to the installed
   `versionName`.
2. If the server version is strictly newer, the whole app is blocked behind a non-dismissible
   `ForceUpdateScreen`.
3. "Update now" downloads the APK from `download_url` and hands it to the system installer.

The endpoint is polled in four places: at launch, from the "Check for updates" button, every 30
minutes by the background location service while tracking is on, and — when tracking is **off** —
by a periodic `UpdateCheckWorker` (WorkManager, ~30-minute cadence, survives reboots). The
background pollers raise a **notification** when a newer version appears; tapping it opens the app
onto the force-update gate. The worker steps aside when the tracking service is running so the two
never double up. Because the endpoint is unauthenticated, the check also runs on the login screen.

On Android 8+ the user must allow Tracker to "install unknown apps"; the updater routes them to
that setting when it is missing (`REQUEST_INSTALL_PACKAGES`, plus a `FileProvider` to share the
downloaded APK).

### Release signing

Android only installs an update over an existing app when both are signed with the **same key**.
Create `keystore.properties` in the project root (git-ignored):

```
storeFile=tracker-release.jks
storePassword=…
keyAlias=tracker
keyPassword=…
```

Without it, release builds fall back to the debug key. Note that changing the signing key later
also changes every device's `device_id`.

## Shipping a new version

1. Bump `versionName` (and `versionCode`) in `app/build.gradle.kts` — the updater compares
   `versionName` against the server's `version`.
2. `./gradlew assembleRelease` and host the signed APK where `download_url` points.
3. Point `GET api/tablet/app/version/` at the new `version` and `download_url`.

Every signed-in client picks up the update on its next API call.

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

The watchdog and boot receiver only act when the session says tracking was on *and* an access
token is still stored, so logging out genuinely stops everything.

**Caveat worth knowing:** aggressive OEM battery managers (Xiaomi, Huawei, Oppo, Samsung) kill
background services regardless of what Android's own rules allow. Those devices need the app
added to the vendor's own "protected apps" / "auto-start" list by hand — no code can do it.

## Offline behaviour

Fixes are appended to a local queue (`data/LocationQueue.kt`) *before* any upload is attempted,
so a fix is never lost to a momentary network failure. The queue is a JSON file holding up to
1000 entries (~3.5 days at one per 5 minutes); when full, the oldest are dropped first. It
drains on every 5-minute tick and immediately on reconnect, and is wiped on logout.

## Logout

Stops the service, cancels the watchdog, clears the update notification, and erases the
access/refresh tokens, user details, queued locations and app caches. `android:allowBackup="false"`
keeps the data out of cloud backups too.

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
