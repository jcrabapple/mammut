# Post as image — share a status as a PNG card

Status: approved for build. Implements G8 from `docs/research/feature-viability-review.md`.
Target: Mammut 1.1.0.

Sharing a post to a place that has no fediverse preview (a chat, a slideshow, a group with no
link unfurling) is a screenshot today. A generated card is the same act with the post's real
text, real media, and a link back.

## 1. Scope

In scope (slice A, this card):

- "Share as image" action in the post overflow menu (`R.menu.post`).
- A renderer that draws one status into a PNG at the system share sheet.
- Post text with custom emoji, mentions, links and hashtags rendered as they are in the timeline.
- The post's own media: 1 to 4 images in the same arrangements the timeline uses.
- Content warnings and sensitive media handled without silently stripping either.
- A footer that identifies the app and carries the post's canonical URL.

Out of scope (slice B, separate card):

- Quote-post block, poll block.
- "Save image" to the gallery.
- Any per-share options (colours, watermark on/off, font size).

Out of scope permanently for this feature: rendering other people's replies, boosts' outer
author, alt text as image text, and any server request. The card is drawn from the status model
already in memory.

## 2. Where the action lives

`res/menu/post.xml`, a new item `@+id/share_as_image`, title string
`share_as_image` = "Share as image", placed immediately after `@+id/share` inside
`menu_group1`. Wired in `HeaderStatusDisplayItem`'s `PopupMenu` listener next to the existing
`R.id.share` branch.

For a boost, the menu belongs to the outer status as displayed. Render the **inner** status
(`status.reblog` when present) — the booster's own text is empty and their wrapper is not what
the user meant to share.

The action is offered on every status that has either non-empty content or at least one media
attachment. It is never disabled for length, sensitivity or CW state.

## 3. Result of tapping it

1. A snackbar appears immediately: "Preparing image…" — the render is off the UI thread.
2. The card is rasterised to `cacheDir/share/post-<statusId>-<epochMillis>.png`.
3. `ACTION_SEND` with `type = image/png`, `EXTRA_STREAM` = the FileProvider URI,
   `FLAG_GRANT_READ_URI_PERMISSION`, and `EXTRA_TEXT` = the status's `url`, wrapped in
   `Intent.createChooser`.
4. If rendering fails, a snackbar says "Could not create image" and nothing is shared. No
   partially drawn or zero-height bitmap may ever reach the share sheet.

FileProvider: `res/xml/fileprovider_paths.xml` currently exposes only `images/`. Add
`<cache-path name="share" path="share/"/>`. Do not widen any existing path.

Cleanup: files older than one hour in `cacheDir/share/` are deleted when the next share
starts. The shared file itself must outlive the chooser, so do not delete it on return.

## 4. Card geometry

Fixed 1080 px wide. Height is measured content, capped at 8000 px (§6).

| Element | Value |
|---|---|
| Card background | theme `colorM3Surface` (matches the app the user is in) |
| Side padding | 56 px all edges |
| Avatar | 96 × 96, same rounding as the timeline (`OutlineProviders.roundedRect`) |
| Name | 40 sp, semibold, `colorM3OnSurface` |
| Handle | 30 sp, `colorM3OnSurfaceVariant`, `@user@instance` always (never bare `@user`) |
| Timestamp | 26 sp, `colorM3OnSurfaceVariant`, absolute date and time in the device's zone, e.g. `8 Nov 2026, 09:14` |
| Gap header → body | 56 px |
| Body | 38 sp, line spacing 1.35, `colorM3OnSurface`, links and mentions in `colorM3Primary` |
| Gap body → media | 40 px |
| Media gap | 8 px |
| Media corner radius | 24 px |
| Footer rule | 1 px, `colorM3OutlineVariant`, 40 px above the footer |
| Footer text | 24 sp, `colorM3OnSurfaceVariant`: post URL on the left, `Mammut` wordmark on the right |

No drop shadows, no gradients, no outer margin, no rounded corners on the card itself: the
shared image is full-bleed at 1080 px so no host crops a border into it.

Text is laid out with `TextView`-equivalent measurement on a `LinearLayout` inflated off-screen,
not by drawing glyphs by hand: the renderer must reuse `HtmlParser.parse(content, emojis,
mentions, tags, accountID, status, context)` so mentions, hashtags, links and custom emoji are
the same objects the timeline shows. Custom emoji must be loaded (the drawable resolves) before
`draw(canvas)`; if any emoji drawable is still pending, wait for it rather than drawing a blank
box.

## 5. Media arrangement

Uses the status's own attachments in order, ignoring any beyond the fourth with a `+N` badge on
the fourth tile (32 sp, white on a 60% black pill).

| Count | Arrangement | Tile size |
|---|---|---|
| 1 | full width, centre-cropped | 968 × 726 max, aspect preserved up to 4:3, taller images cropped to 4:3 |
| 2 | side by side | 480 × 480 each |
| 3 | one large left, two stacked right | 480 × 480 left, 480 × 236 each right |
| 4 | 2 × 2 | 480 × 480 each |

`previewUrl` is the source; if it is missing or fails, draw a neutral placeholder tile with the
attachment's description or "Media" centred, never a blank hole. Video and audio attachments are
not rendered as frames in slice A; they get the placeholder tile with a centred type label.

Sensitive media (`status.sensitive` or `attachment.sensitive`): the source bitmap is blurred
with a real box blur (radius 32 px on the 480 px tile) and a centred label "Sensitive media"
(32 sp, white on 55% black) is drawn over it. The unblurred image never enters the PNG. This is
a hard requirement: the person sharing saw a blurred tile, and the file must not undo that.

## 6. Content warning and length

If `spoilerText` is non-empty, draw `CW: <spoilerText>` at 32 sp semibold above the body, then
the body text. The CW line is never omitted and never truncated before the body is.

If the measured height would exceed 8000 px, truncate the body at the last complete paragraph
that fits, append an ellipsis line, and add a 30 sp line "Continue reading at <url>". Truncation
must cut at a paragraph or line boundary, never mid-word, and the URL is then mandatory in the
footer regardless of space.

Empty content (media-only post) omits the body block entirely and closes the 56 px gap it would
have left.

## 7. Reuse, not reinvention

- Text: `HtmlParser.parse(...)` as above.
- Share sheet: follow the existing `UiUtils.openSystemShareSheet` intent shape, but this feature
  needs `EXTRA_STREAM`; add a sibling method (`shareImage(...)`) rather than editing behaviour
  other callers rely on.
- Avatar and media loading: the existing image loader (`ImageLoader`/`ImageLoaderRequest`)
  against the already-warm cache; no new networking library.
- Theme colours: the same `UiTheme` accessors the timeline uses, so the card matches light,
  dark, Tokyo Night and Mammut palettes without a second colour table.

## 8. Threading and lifecycle

Rendering happens on a single background executor; at most one render runs at a time and a
second tap replaces the pending request. Nothing may touch a `Context` after the fragment is
destroyed: hold the application context for the render, and post the snackbar only if the
activity is still alive.

## 9. Testing

JVM-testable and required:

- The media grid solver: given 1, 2, 3, 4 and 5+ attachments and each aspect ratio, returns
  rectangles that fit inside 968 px, keep the declared gaps, and never overlap.
- The truncation chooser: given a list of paragraph heights and a 8000 px budget, returns the
  largest prefix that fits, and the flag that says a URL must be appended.
- The sensitive-media decision: `status.sensitive`, per-attachment `sensitive`, and the
  aggregate rule that any sensitive attachment in the set means that attachment is blurred.

Not testable on this host (no working emulator): the actual pixels. The card's `Done means`
therefore requires a real device render of: a text-only post, a media post, a CW post, a
sensitive media post, and a boost, each opened in the share sheet. Attach the PNGs to the task.

## 10. Done means

- `assembleDebug` and `lint` clean with no new warnings in touched files.
- The five device renders above exist as files and are attached to the task, plus one each in
  light and dark theme.
- No existing share path changed behaviour (`R.id.share` on a post still shares the link).
- FileProvider exposes only `cache/share/` in addition to what it already had.
- No new dependency in `mastodon/build.gradle`.

## 11. Slice B (separate card, after A lands)

Additive only — slice A's rendering path, geometry and tests must not be rewritten to fit these.

- **Quote block.** When `status.quote` (or the quoted-status field this build uses) is present,
  draw it after the body: a 1 px outline rounded 20 px block, background
  `darken(colorM3SurfaceVariant, 0.06f)`, inner padding 32 px, the quoted author's name and
  `@user@instance` at 28 sp, its text clamped to 4 lines at 32 sp, and its first media thumbnail
  (if any) as a 160 × 160 tile on the right. Sensitive quoted media is blurred by the same rule
  as §5. A quote block never contains another quote block; if the quoted status itself quotes,
  show its own text only.
- **Poll block.** Draw the question at 34 sp semibold, then each option as a 32 sp label with its
  percentage right-aligned and a 12 px-tall rounded bar behind it scaled to the percentage
  (`colorM3Primary` at 35% for the leader, `colorM3SurfaceVariant` for the rest). Votes and
  "closed" state in a 26 sp footer line: `N votes · closed` / `N votes · ends in 3 days`. The bar
  length must be driven by the same percentage the label prints, rounded the same way.
- **Save image.** In the same menu group, `@+id/save_image`, title "Save image", offered on the
  same statuses. Saves to `MediaStore.Images` under `Pictures/Mammut` via
  `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` on API 29+ (`IS_PENDING` set then cleared); on
  API 23–28 write into the app's external files dir under `Pictures/` and toast the path, with no
  runtime storage permission requested. Success snackbar "Saved to Pictures/Mammut" on 29+ only.
- **Ordering.** Quote block above poll block when both exist; both above media.
- **Tests.** Percentage-to-bar geometry (0%, rounding boundaries, ties, a poll with one option and
  a closed poll), and the clamp rule for a quoted post longer than 4 lines.
