# XroTune

An Android music client for YouTube, written in Kotlin with Jetpack Compose and
Material 3. Package `com.xrotune.app`, minSdk 26, targetSdk 35.

Search and browsing work without signing in. Audio playback is gated by the
InnerTube player endpoint for unauthenticated callers, so the app tries several
client identities in order and keeps the first one that returns a playable
stream. This is an unofficial client; playback availability and service terms
must be reviewed before public distribution.

## Download

Prebuilt debug APK: [releases](https://github.com/xpro-004/xrotune-android/releases)

Debug-signed, so the phone must allow installs from unknown sources.

## Build

Requires JDK 17+ and Android SDK platform 35 / build-tools 35.0.0.

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

A local `google-services.json` for the Firebase project is required by the
Google Services Gradle plugin. It is intentionally git-ignored; never commit
Firebase project configuration or signing keys.

## Firebase setup

1. Register the Android app using package `com.xrotune.app` in Firebase.
2. Add the matching `google-services.json` locally.
3. Enable Google as a Firebase Authentication provider.
4. Enable Cloud Firestore and deploy `firestore.rules`.
5. Enable Firebase AI Logic and confirm the configured Gemini model is
   available for the selected provider and project.
6. Register the Android app in Firebase App Check.
7. For release builds, link the Firebase project to the app's Play Integrity
   API configuration and register the signing certificate SHA-256 fingerprint.
   Configure Play Integrity policy for the actual distribution channel.
8. For debug builds, use the Firebase App Check Debug provider and register
   its generated debug token in the Firebase console. Never ship a debug token
   in a release build.
9. Start with App Check enforcement disabled, verify legitimate app traffic in
   Firebase metrics, then enable enforcement only after validation.

App Check provider initialization happens in `MainActivity.onCreate` before
the app first accesses Firestore or Firebase AI Logic. Debug builds use the
Debug provider; release builds use the native Android Play Integrity provider.

## Layout

```
app/src/main/java/com/xrotune/app/
├── MainActivity.kt          Navigation, tabs, mini-player, top bar
├── XroTuneApp.kt            Application: repository + session singletons
├── auth/                    Google sign-in and session persistence
├── data/api/                InnerTubeClient, ResponseParser, ApiConfig
├── data/model/              Track, MusicSection, PlayableMedia, exceptions
├── data/repo/               MusicRepository and FirestoreRepository
├── player/                  Media3 PlaybackService + controller
├── ui/                      Compose screens, theme, components
└── util/                    JSON helpers, error mapping, App Check
```

## How playback resolves

`MusicRepository.resolveMedia` walks `ApiConfig.PLAYER_CLIENTS` in order and
returns the first response that parses to an audio format carrying a direct
URL. A transport failure for one identity does not abort the loop; if every
identity refuses, the last refusal is surfaced to the UI.

The current repository's own research notes that anonymous playback is
effectively unavailable for most tested videos. Search results must not be
presented as a guarantee that their audio can be played.

## Screens

- Home — 16:9 video cards with duration badges, horizontal rails
- Search — two-column video grid, debounced query
- Library — liked music and playlists
- Player — full screen, seek slider, play/pause
- Sign in — Google sign-in, with a guest path
- AI DJ — Gemini playlist recommendations

`preview/index.html` is a self-contained visual preview of the screens.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

`ResponseParserTest` runs against captured API responses in
`app/src/test/resources/`. Firestore rules tests are in `firestore.test.js`
and require the Firebase Emulator Suite plus the Node test dependencies.

## Known limitations

- Playback against unauthenticated InnerTube sessions is not guaranteed.
- Playlist documents currently store metadata; a full playlist-track
  subcollection and add/remove/reorder flows still need implementation.
- Library cloud data is loaded as a one-shot refresh, not a continuously
  observed state.
- Google sign-in, Firebase configuration, App Check, and release signing must
  be validated using the actual Firebase project and signing certificate.
- Do not distribute a production APK until licensing, API terms, privacy
  disclosures, and runtime behavior have been reviewed.
