<img src="img/mammut-icon.png" alt="Mammut" width="160" height="160">

# Mammut

**A fediverse client for people who want to read their timeline and be done with it.**

Mammut is a fork of [Moshidon](https://github.com/LucasGGamerM/moshidon), which is itself a fork of
[the official Mastodon Android app](https://github.com/mastodon/mastodon-android) by Mastodon gGmbH,
via [megalodon](https://github.com/sk22/megalodon). It keeps what those projects do well and adds a
reading-first redesign on top.

## Please support Moshidon

Mammut exists because Lucas Eduardo ([LucasGGamerM](https://github.com/LucasGGamerM)) has spent years
building and maintaining Moshidon, and because the Mastodon Android team built the client underneath
it. Most of the code you are running is their work.

**If you find Mammut useful, please donate to Moshidon's developer.** He is the reason this app exists
at all, and a donation matters more than a star on any fork:

- [GitHub Sponsors](https://github.com/sponsors/LucasGGamerM)
- [Liberapay](https://liberapay.com/LucasGGamerM/donate)
- Monero: `4886mdarcyB6Yf8Qc6vDJBK1fz6ibHFLZUmHb4GZZz9yLGNhcG3XC64e5UZ8dVQYTLZb82W6P9WhteowW4STJEec97Gf22j`

## What this fork adds

### Reading

- **Catch up** is a bounded view of what you have not seen: posts since your last catch-up, capped at
  24 hours and 200 posts, grouped by author in readable cards, with an honest ending. No infinite
  scroll, no streaks, no claim that you saw everything.
- **Reading Room** turns a conversation into something you can read: a factual count of posts and
  people, side-branch markers, and an **Unroll** mode that lays a self-thread out in order without
  pretending a conversation is one column.
- **Inline image descriptions**, off by default, for timelines where alt text matters.

### Writing

- **Drafts** that survive leaving the app, with their media, descriptions, content warnings and polls
  copied into app storage, so a draft never opens onto a deleted file.
- **Threads**: numbered segments, each with its own text, media and content warning, published as a
  chain. A failure part-way keeps the unpublished tail as a draft and never double-posts the prefix.
- **Scheduling**, with a date and time picker and a Scheduled tab listing what goes out next.
- **The Caption Track**: attaching a photo fills its description from the file's own metadata (EXIF,
  XMP, IPTC) and shows an editable field under each image, so alt text is part of writing instead of a
  modal chore.
- **Undo** for a few seconds after posting.

### Sharing

- **Share a post as an image**: one action in a post's menu renders it as a 1080 px card — text with
  its custom emoji, links and mentions, its media, a quoted post, poll results with percentages, and
  the post's own content warning — then hands the PNG to the system share sheet, or saves it to
  `Pictures/Mammut`. Media the author marked sensitive is blurred in the file itself, so sharing a
  post can never quietly reveal what the timeline was hiding.

### Finding

- **Search operator chips** for `has:media`, `has:link`, `has:poll`, `is:reply`, `is:sensitive`,
  `from:me` and `in:library`, with a full syntax reference. The chips write ordinary Mastodon syntax,
  so a chip you tap and a token you type are the same query.
- **Per-timeline filters**, media blur, hidden domains, and a filter action on any post.

### Navigation and look

- **FAB navigation**: one floating button holding Home, Local, Federated, Notifications, Explore,
  Profile, Compose, Drafts and Settings, with the destination title in the app bar and a live unread
  chip beside it.
- **Mammut palette** by default and first in the theme list, alongside the palettes Moshidon ships,
  including Tokyo Night.
- **Full-width posts**, an optional layout in the display settings where post text, media and actions
  start at the edge of the timeline instead of lining up after the avatar.
- Custom post-action icons and a launcher icon of its own.
- **Four bundled typefaces**, in Display settings: Inter, Source Sans 3, Literata and Atkinson
  Hyperlegible, each shown in its own face. System stays the default. Code spans keep monospace;
  Atkinson Hyperlegible covers Latin only, so other scripts fall back to the system face per glyph.

### Under the hood

- **UnifiedPush** support (for example through ntfy), so notifications do not depend on Google.
- GPL-3.0, like upstream, with the complete source for every released build in this repository.

Everything else Moshidon offers is still here: multiple accounts, crossposting, lists, filters, post
editing, translation, pronouns, emoji reactions, and the rest.

## Install

Download the APK from [Releases](https://github.com/jcrabapple/mammut/releases). Builds are signed
with a consistent key, so newer versions install over older ones, and the app offers updates from
GitHub.

Requires Android 6.0 (API 23) or newer. The app id is `app.mammut.android`, so Mammut and Moshidon can
be installed side by side.

## Build from source

```sh
git clone https://github.com/jcrabapple/mammut.git
cd moshidon
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :mastodon:assembleGithubRelease
```

JDK 17 and Android SDK 35. The APK is written to
`mastodon/build/outputs/apk/githubRelease/mammut-githubRelease.apk`.

Release builds are signed when `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and
`SIGNING_KEY_PASSWORD` are set. CI does this from the `KEYSTORE_BASE64` secret. See
[docs/RELEASING.md](docs/RELEASING.md).

## Translations

The app ships 79 locales inherited from Moshidon and the projects it came from. Mammut's own
strings — Catch up, the Reading Room, typefaces, the upload messages — exist only in English, and
show in English inside an otherwise translated app.

There is no translation service connected, and the inherited `crowdin.yml` was removed so nobody
syncs into Moshidon's project by accident. Strings are plain Android resources in
`mastodon/src/main/res/values/strings.xml`; a pull request is a perfectly good way to translate
them.

## Credits

Mammut would not exist without these projects:

- **[Moshidon](https://github.com/LucasGGamerM/moshidon)** by Lucas Eduardo. Most of this app is his
  work. [Donate if you can.](https://github.com/sponsors/LucasGGamerM)
- **[megalodon](https://github.com/sk22/megalodon)** by sk22, the fork of the official app that
  Moshidon grew from.
- **[Mastodon for Android](https://github.com/mastodon/mastodon-android)** by
  [Mastodon gGmbH](https://joinmastodon.org), the client all of this descends from.
- The translators, testers and issue reporters across those projects whose work came along with the
  code.

Mammut is an independent fork. It is not affiliated with or endorsed by Mastodon gGmbH, the Moshidon
project, or any server operator.

## License

GPL-3.0, the same license as the projects above. See [LICENSE](LICENSE). Because this is a modified
version distributed under the GPL, the complete corresponding source for every release lives in this
repository, tagged to match the release.
