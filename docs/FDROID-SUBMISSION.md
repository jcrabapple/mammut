# Getting Mammut into F-Droid / IzzyOnDroid

Status: **targeting IzzyOnDroid first** (lighter review, distributes the developer's own
signed APK, updates within ~24h), then F-Droid main once the fork has more history/users.

Mammut is a GPL-3.0 fork of Moshidon (`org.joinmastodon.android.moshinda`), which is already
on both F-Droid main and IzzyOnDroid. Moshidon's build metadata is the template for ours.

## Readiness audit (verified against the repo, 2026-11-16)

| Requirement | Status |
|---|---|
| FOSS license | GPL-3.0-only (`LICENSE`) ✓ |
| Public source | github.com/jcrabapple/mammut ✓ |
| No proprietary deps / trackers | No Play Services / Firebase / GMS / ML Kit / billing in `build.gradle` ✓ |
| GMS files in tree | Compile-only AIDL stubs (`src/main/aidl/com/google/android/gms/...`) for UnifiedPush interfaces; **no GMS library dependency**. Same as Moshidon (accepted). Disclose in submission. |
| Cleartext traffic | `usesCleartextTraffic` not set; `network_security_config.xml` present (release trusts system CAs only) ✓ |
| Self-updater | `GithubSelfUpdater.needSelfUpdating()` is true only for `githubRelease`/`githubDebug`/`nightly`. **Disabled in `fdroidRelease`.** In the GitHub build the download/install is user-initiated (opt-in). ✓ |
| Dependency metadata | `dependenciesInfo { includeInApk false; includeInBundle false }` already set ✓ |
| Fastlane `en-US` | `title.txt` (Mammut), `short_description.txt` (75 chars ≤ 80), `full_description.txt`, `changelogs/`, `images/{icon,featureGraphic,phoneScreenshots}` ✓ |
| APK on tagged release, < 30 MB | `mammut-1.3.13.apk` ≈ 17.5 MB on GitHub release `v1.3.13` ✓ |
| Builds from source (F-Droid) | `./gradlew :mastodon:assembleFdroidRelease` → BUILD SUCCESSFUL; output `mastodon/build/outputs/apk/fdroidRelease/*.apk` ✓ |
| Distinct application ID | `app.mammut.android` ✓ |

No code changes were needed for the updater or dependency metadata — both are inherited
correctly from Moshidon.

## Feature differentiation (F-Droid Inclusion Policy Quality #5)

F-Droid rejects forks that are "only minor rebranding" over an app already in the repo.
Mammut adds substantial, distinct functionality over Moshidon:

- **Catch up** — a bounded "since you last checked" reading view (24h / 200-post cap, author
  grouping, honest end-of-fetched footer; never claims you saw everything).
- **Reading Room / Unroll** — a focused, full-width thread reading mode.
- **Address-line thread grammar** — horizontal reply addressing with a Thread badge, explicit
  gap states, and an honest "end of the N posts fetched" line (no fabricated continuity).
- **Composer 2.0** — drafts, threads, and scheduled posts.
- **Mammut Translate** — a built-in, open-source translation fallback (LibreTranslate,
  self-hosted) that works on servers with no built-in translator; the instance's own
  translator is still preferred, and users can point at their own instance or turn it off.
- **Share post as image**, **search operator chips**, **full-width timeline option**.
- **Four bundled typefaces** (Inter, Source Sans 3, Literata, Atkinson Hyperlegible) with real
  italics, plus a system default.
- **FAB navigation** replacing the bottom tab bar, with an optional left-side placement.
- A distinct **visual identity** (palette, launcher icon, custom action-icon set, Tokyo Night theme).
- **Audit-grade hardening**: a full-app blind audit fixed 21 bugs plus security work (validated
  settings import/export, panic-app caller verification, release-only system trust anchors,
  credential backup exclusion, account-scoped unread store), with 368 unit tests.
- Independent versioning and a tag-driven release pipeline.

## IzzyOnDroid submission (do this first)

Process: file an issue at **https://codeberg.org/IzzyOnDroid/repodata/issues** (needs a Codeberg
account). IzzyOnDroid distributes the developer's signed APK from GitHub releases and runs
scanners (incl. VirusTotal) + reproducible-build checks. Policy:
https://izzyondroid.org/docs/general/AppInclusionPolicy/

### Ready-to-file issue text

Title: `New app: Mammut (app.mammut.android)`

Body:
```
App name: Mammut
Package ID: app.mammut.android
Source code: https://github.com/jcrabapple/mammut
License: GPL-3.0-only
Category: Social Network

Releases (signed APK attached, ~17.5 MB, < 30 MB):
https://github.com/jcrabapple/mammut/releases
Latest: https://github.com/jcrabapple/mammut/releases/tag/v1.3.13
APK: https://github.com/jcrabapple/mammut/releases/download/v1.3.13/mammut-1.3.13.apk

Fastlane metadata (en-US) is in the repo under fastlane/metadata/android/en-US/
(short_description 75 chars, full_description, changelogs, icon, featureGraphic, screenshots).

Short description: A reading-first fediverse client. Catch up, threads, drafts and scheduling.

Mammut is a GPL-3.0 fork of Moshidon (already in this repo) adding Catch up, a Reading Room
thread mode, Composer 2.0 (drafts/threads/scheduling), a built-in open-source LibreTranslate
translation fallback, share-as-image, bundled typefaces, and a full-app security/bug audit.

Compliance notes:
- No proprietary dependencies or trackers. The com.google.android.gms AIDL files in the tree
  are compile-only interface stubs for UnifiedPush; there is no Play Services/GMS library.
- Self-updater is opt-in (user-initiated download) in the GitHub build and fully disabled in
  the fdroidRelease build (GithubSelfUpdater.needSelfUpdating()).
- dependenciesInfo includeInApk/includeInBundle = false.
- No usesCleartextTraffic; network_security_config.xml restricts release to system CAs.
- Builds from source: ./gradlew :mastodon:assembleFdroidRelease (OpenJDK 17).
- Happy to establish reproducible builds.

Author: Jason Crabtree (https://github.com/jcrabapple) — I am the maintainer of this fork.
```

### Before filing
- Confirm the latest release tag/APK is current (bump if a newer release ships first).
- Decide the donation link (see below).
- Tag format: Mammut tags are `v1.3.13`; IzzyOnDroid matches tags to versionName/versionCode.
  The `v` prefix is generally handled, but confirm the updater picks it up (Moshidon uses
  unprefixed tags). If needed, add `UpdateCheckMode: Tags` regex or tag without the `v`.

## F-Droid main (later)

Two policy hurdles to clear first (https://f-droid.org/en/docs/Inclusion_Policy/):
1. **Quality #5 (unique value)** — use the differentiation statement above in the MR.
2. **Quality #6 (≥ 3 months active development)** — Mammut is a young fork; either demonstrate
   a user base (downloads/installs) or accumulate development history. Exceptions: proven users,
   or author maintains other F-Droid apps.

Steps when ready:
1. Install fdroidserver; `fdroid import` to refine `fdroid/app.mammut.android.yml` (draft in this repo).
2. `fdroid build app.mammut.android` locally to confirm a clean source build.
3. Fork gitlab.com/fdroid/fdroiddata, add `metadata/app.mammut.android.yml`, public fork +
   unprotected branch, MR titled "New app: Mammut", fill the policy checklist, one app per MR.
4. Respond to reviewer feedback; F-Droid builds from source and publishes; new tags auto-build.

Metadata draft: `fdroid/app.mammut.android.yml` (gradle `fdroidRelease`, OpenJDK 17,
`AutoUpdateMode: Version`, `UpdateCheckMode: Tags`). Reproducible-build verification
(`Binaries:` + `AllowedAPKSigningKeys:`) is optional and can be added after inclusion.

## Decisions needed from Jason
1. **Donation link** — README points to Moshidon's author (LucasGGamerM). F-Droid requires
   upstream-verified donation links. List his, yours, or none?
2. **Who files the IzzyOnDroid issue** — needs a Codeberg account and is a public submission
   under your identity. File it yourself, or authorize me to file it via your account.
3. **F-Droid main timing** — wait for ~3 months / a user base, or stay IzzyOnDroid-only for now.

## Signing note
F-Droid main signs its own build (F-Droid key) — users cannot upgrade in place from the
GitHub-signed APK (different signature). IzzyOnDroid distributes the GitHub-signed APK as-is,
so IzzyOnDroid users keep the same signature as GitHub installs.