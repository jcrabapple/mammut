# Releasing Mammut

Version line: `major*10000 + minor*100 + patch` for `versionCode`, with `versionName` matching
(`1.0.0` is `10000`, `1.1.0` is `10100`, `1.1.1` is `10101`). Both live in `mastodon/build.gradle`.

The inherited Moshidon value was `110`, so the first release must keep the code above that; `10000`
does. Do not reset it to `1`, or existing installs will refuse the update as a downgrade.

## Cutting a release

1. Bump `versionCode` and `versionName` in `mastodon/build.gradle`.
2. Commit: `Bump to 1.1.0`.
3. Tag and push:
   ```sh
   git tag -a v1.1.0 -m "Mammut 1.1.0"
   git push origin v1.1.0
   ```
4. `.github/workflows/release.yml` builds `:mastodon:assembleGithubRelease`, verifies the signature
   with `apksigner`, writes a `.sha256`, and publishes a GitHub release with the APK attached as
   `mammut-1.1.0.apk`, marked latest.

A tag that fails to sign fails the job on purpose: `KEYSTORE_BASE64` missing is a hard error, so a
release is never published unsigned.

## The signing key

- Keystore: `CN=Mammut, OU=Release, O=Mammut, L=Winchester, ST=Virginia, C=US`
- Cert SHA-256: `9e6c898feb404dee7f774d8b9f5586e27d4ad2b7b3e1644b5afb0f32dfe6f887`
- CI secrets: `KEYSTORE_BASE64` (keystore, base64), `KEYSTORE_PASSWORD`
- Local env: `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` (default `key0`),
  `SIGNING_KEY_PASSWORD`

**Losing this key means every existing install has to be uninstalled before it can be updated.**
Keep a copy of the keystore and its password outside CI.

## In-app updates

`mastodon/src/github/.../GithubSelfUpdaterImpl.java` reads
`https://api.github.com/repos/jcrabapple/mammut/releases` and installs the first APK asset it finds.
The release workflow uploads exactly one APK, named `mammut-<version>.apk`, so the updater picks it up
without configuration.

## Translations

`crowdin.yml` came from Moshidon and points at its translation setup. Until Mammut has its own
translation project, do not advertise translation; the file is harmless if left alone.
