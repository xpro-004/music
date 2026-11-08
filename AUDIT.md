# XroTune full repository audit

Audit date: 2026-11-08
Repository: https://github.com/xpro-004/music
Audited branch: `audit/app-check-play-integrity`
Source review baseline: `cca9f684654b0b79e18575377bee5a4b1fb2d299`

## Scope and confidence

Reviewed the repository tree, README, Gradle configuration, manifest, application
entry point, navigation, authentication/session code, InnerTube client and
parser, music repository, Media3 playback service/controller, Firestore
repository/models/rules, AI DJ screen/view-model, unit tests, Firestore rules
tests, and preview.

This is a source review. No Android SDK build, emulator run, Firebase emulator
run, live Firebase project check, or device playback test was executed during
this audit. Findings below are therefore classified as source-confirmed,
configuration-confirmed, or requiring runtime verification.

## Executive summary

XroTune is a real native Android application built with Kotlin, Jetpack
Compose, Media3, Firebase Authentication, Cloud Firestore, and Firebase AI
Logic. It is not a WebView wrapper.

The architecture is a credible prototype, but the project is not yet
production-ready. The highest-risk areas are:

1. Anonymous YouTube/InnerTube audio playback is intrinsically unreliable in
   the project's own recorded experiments.
2. Release App Check was configured with a reCAPTCHA provider instead of the
   native Android Play Integrity provider.
3. Cloud profile creation is implemented but is not called from the
   authentication flow.
4. Playlists are metadata-only; there is no persisted track membership model.
5. SessionStore persists a Google ID token even though the app explicitly does
   not use it as a YouTube OAuth token.
6. Library cloud data is fetched as a one-shot snapshot rather than maintained
   as a live observed state.
7. Firebase rules tests exist, but there is no checked-in package manifest or
   documented reproducible Node dependency install for those tests.
8. Release build/signing and the actual Firebase/OAuth configuration remain
   unverified.

## Findings

### P0 — Playback product viability

The repository's AGENTS.md and README record repeated experiments showing
that anonymous InnerTube player requests are refused for most tested video
IDs. A client-identity fallback loop does not remove the server-side
verification/attestation gate. Search success must not be interpreted as
playback availability.

Recommendation:
- Do not advertise unlimited or reliable playback.
- Do not attempt to bypass account verification, device attestation, or
  service controls.
- Select a licensed music catalogue/API or a provider that explicitly permits
  the intended playback use.
- Keep InnerTube search/browse isolated behind an interface so a compliant
  provider can replace it without rewriting the UI/player.

### P1 — App Check provider mismatch (fixed on audit branch)

Before this audit, release builds attempted to initialize the reCAPTCHA
provider from an Android Activity. Firebase's native Android App Check
guidance uses Play Integrity for Android, and Debug provider for development.

Change made on `audit/app-check-play-integrity`:
- Replace the reCAPTCHA dependency with
  `firebase-appcheck-playintegrity`.
- Install `PlayIntegrityAppCheckProviderFactory` in release builds.
- Keep `DebugAppCheckProviderFactory` for debug builds.
- Document Firebase Console registration, Play Integrity linking, SHA-256
  signing fingerprint, debug-token registration, and gradual enforcement.

Important deployment requirement:
The Firebase app must be registered for App Check and the Play Integrity
configuration must match the actual distribution channel and signing key.
Do not enable enforcement until metrics show legitimate clients are passing.

### P1 — Authentication and cloud profile lifecycle

Source-confirmed:
- AuthManager signs in with Google through Credential Manager and Firebase
  Authentication.
- AuthViewModel saves account metadata to DataStore.
- FirestoreRepository.saveUserProfile() exists.
- No call to saveUserProfile() was found in the authentication success path.

Impact:
The app may authenticate users but leave the Firestore user profile document
missing. This makes future user settings, profile metadata, account lifecycle
and server-side user features harder to support.

Recommendation:
- Inject FirestoreRepository into AuthViewModel.
- After Firebase sign-in succeeds, save the profile using the Firebase Auth
  user's UID and profile data.
- Surface profile-sync failure separately from successful authentication.
- Test first sign-in, repeat sign-in, profile update, sign-out and account
  switching.

### P1 — Playlist persistence is incomplete

Source-confirmed:
- createPlaylist() writes only playlist metadata.
- trackCount is always initialized to zero.
- There is no playlist_tracks subcollection or equivalent ordered membership
  model in the current Firestore repository/rules/models.
- AI DJ's Save action creates a playlist shell; it does not add the suggested
  songs to it.
- Playlist cards are mapped to Track objects using the playlist document ID
  as though it were a video ID. This is not a valid playback mapping.

Recommendation:
Use a structure such as:

```
users/{uid}/playlists/{playlistId}
  title, description, createdAt, updatedAt

users/{uid}/playlists/{playlistId}/tracks/{videoId}
  videoId, title, artist, thumbnailUrl, duration, position, addedAt
```

Add atomic or transaction-based add/remove/reorder operations, validate
ownership and field types in Firestore rules, and derive trackCount from
membership (or maintain it transactionally). The playlist screen should open
the playlist and display its real tracks, not try to play the playlist ID.

### P1 — Unnecessary Google ID token persistence

Source-confirmed:
- AuthManager returns the Google ID token in UserAccount.
- SessionStore stores that token in Preferences DataStore.
- AuthViewModel comments that the Google OpenID token is not a YouTube OAuth
  token and does not assign it to MusicRepository.authHeader.
- No token consumer was found in the inspected session flow.

Impact:
An authentication token is persisted without an identified product need,
increasing the impact of local data extraction.

Recommendation:
Remove idToken from the persisted UserAccount/session model unless a concrete
Firebase/Google API flow needs it. Prefer FirebaseAuth's managed session and
request fresh tokens only when needed. Never treat a Google ID token as a
YouTube Data API OAuth access token.

### P2 — Library is not live-synchronized

Source-confirmed:
- LibraryViewModel calls observeLikedSongs().firstOrNull() and
  observePlaylists().firstOrNull().
- This consumes only the first snapshot emission and then stops observing.
- MainActivity separately observes liked songs for the current mini-player
  like state, but LibraryViewModel's displayed sections are not live.

Recommendation:
Combine the Firestore flows with the account state and map every emission into
the Library UI state. Cancel the old listener when the signed-in UID changes
or the user signs out. Provide explicit loading, empty, and error states.

### P2 — Firestore rules test setup is not reproducible

Source-confirmed:
- firestore.test.js uses node:test and @firebase/rules-unit-testing.
- The repository tree has no package.json/package-lock.json defining the
  required Node dependencies or test command.
- The current README did not document emulator startup for these tests.

Recommendation:
Add a minimal package manifest and lockfile with pinned dependencies, a
documented emulator command, and CI execution. Add negative tests for malformed
fields, unexpected keys, invalid timestamps, and unauthorized playlist-track
writes.

### P2 — AI DJ output is recommendations, not a resolved playable playlist

Source-confirmed:
- AI DJ asks Gemini for song titles and artists.
- The UI sends a tap to Search using title + artist.
- The response is stored as strings in ai_recommendations.
- No canonical provider track IDs are resolved or stored.
- Save creates a metadata-only playlist.

Recommendation:
Treat model output as untrusted suggestions. Resolve every suggestion through
the selected catalogue provider, show exact matches for user confirmation,
then save canonical track IDs and metadata to the playlist. Handle duplicate,
missing and ambiguous results.

The configured `gemini-3.8-flash` identifier is currently listed in Firebase
AI Logic's supported models; it is not flagged as an invalid model name by
this audit.

### P2 — Release readiness and signing

Source-confirmed:
- The Gradle file enables minification and resource shrinking for release.
- The debug signing config points at a root debug.keystore.
- The repository ignores keystores and google-services.json.
- The build expects a local Firebase configuration file.
- No release signing configuration or release verification evidence was
  established by this source review.

Recommendation:
- Keep production signing material outside GitHub.
- Configure release signing via environment/local secret properties.
- Verify Google OAuth SHA-1 and App Check SHA-256 for the actual signing key.
- Add a CI build/test workflow using a safe Firebase config strategy.
- Run release lint, unit tests, minified release assembly, and install/playback
  smoke tests before publishing.

### P3 — Maintainability and UX

- SearchViewModel catches Exception broadly; cancellation should be rethrown so
  cancelled searches do not become user-visible errors.
- AI DJ save failures are logged but not surfaced to the UI.
- The AI DJ can accept repeated preset taps; request cancellation/sequence
  handling should prevent stale responses from overwriting newer prompts.
- Firestore profile updates should define whether null photo URLs clear or
  preserve the previous avatar.
- The repository should define privacy disclosures, retention and deletion
  behavior for profile, liked-song, playlist and AI prompt history.
- Add accessibility labels and test tags for primary playback and library
  actions.

## Implemented in this audit branch

Branch: `audit/app-check-play-integrity`

Files changed:
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/xrotune/app/util/AppCheckInit.kt`
- `README.md`

The changes are limited to the Android App Check provider correction and
configuration documentation. No production playback bypass or service
restriction circumvention was added.

## Verification status

- GitHub branch comparison: three commits ahead of the audited base at the
  time of review; the final Gradle dependency wiring was committed afterward.
- Source diff inspected.
- Gradle build: NOT RUN in this environment.
- JVM tests: NOT RUN in this environment.
- Firestore Emulator tests: NOT RUN.
- Physical device and Play Integrity attestation: NOT RUN.

Before merging, run:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleRelease --no-daemon
```

Then validate App Check in Firebase Console with the real release signing
certificate before enabling enforcement.


## Follow-up implementation on audit branch

The following source-level fixes were added after the original audit findings:

### Authentication/session fixes

- Credential Manager is now created with the foreground Activity and called from the ViewModel's main coroutine rather than wrapping UI interaction in an IO dispatcher.
- A sign-in result is successful only when Firebase Authentication returns a non-null Firebase user; Google profile IDs are never substituted for Firebase UIDs.
- FirebaseAuth.currentUser is the authority for restoring an authenticated session. Cached DataStore account metadata alone cannot create a signed-in state.
- Guest mode is persisted; switching to guest clears stale cached account metadata.
- Google ID tokens were removed from UserAccount and DataStore. Firebase Auth manages its own credentials.
- Firestore profile synchronization runs after sign-in and when restoring a Firebase session. A cloud profile failure is surfaced separately and does not incorrectly discard the valid Firebase login.
- Profile photo removal now deletes the stale Firestore field.
- Coroutine cancellation is rethrown rather than converted into a generic sign-in error.
- Firestore error logs no longer include user identifiers, email addresses, document paths or raw exception messages.
- App Check debug token handling now uses the SDK-managed Debug provider flow instead of writing undocumented SDK SharedPreferences keys.
- Added unit tests for Firebase-backed session restoration and a GitHub Actions workflow for debug/release builds and JVM unit tests.

### Remaining verification and product work

- The Gradle workflow has been added, but no successful workflow run or local Gradle build is available in this review session. Treat compilation and unit-test status as **unverified** until GitHub Actions reports success.
- Firebase Emulator rules tests have not been run.
- Google OAuth package/SHA fingerprints, actual `google-services.json`, Firebase provider enablement, Firestore profile writes and Play Integrity attestation require verification against the real Firebase project.
- Production App Check enforcement must remain disabled until legitimate release clients pass the Firebase metrics checks.
- Playback provider viability, playlist track membership, live Library observation, AI DJ canonical track resolution, privacy/deletion policy and release signing remain separate outstanding work items.
