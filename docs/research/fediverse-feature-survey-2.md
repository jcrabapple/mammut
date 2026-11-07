# Mammut: second fediverse feature survey

Research note. Date: 2026-11-07. Author: researcher (kanban `t_0c89305c`). Branch: `feat/parity`.
Scope: what other fediverse clients ship that Mammut does not, weighted toward iOS, web, and non-Mastodon
clients, plus server features almost no client surfaces. This is the follow-up to
`docs/research/mastodon-feature-landscape.md` (findings F1–F24), which readers should treat as read.

The brief was "what would make this app stand out", not "what does Mastodon have that we lack". So the
findings below are chosen for distinctiveness first and parity second, and every one has a named app that
ships it and a link proving it. **Fifteen findings survived verification** (G1–G15); everything I looked at and
turned down is in the rejected section with a reason, including the ideas that died because Mammut already ships
them.

## Method

1. Read `docs/research/mastodon-feature-landscape.md` and `docs/MAMMUT-PARITY.md` first. Nothing in either is
   re-proposed here.
2. For each candidate, checked **both** sides: the competitor claim from its own listing/README/changelog, and
   the fork, to see whether Mammut already has it. Several plausible findings died at this step — see
   "Things I checked and dropped" at the end of the rejected section.
3. Server support was read from `docs.joinmastodon.org` method pages (version history blocks) and Mastodon's own
   release blog posts, not from memory.
4. Where a claim could not be confirmed from a primary source it is marked **unverified** and says what was
   missing. That is deliberate: a wrong finding costs a week of engineering.
5. Sizes use the earlier note's scale: **S** ≈ a day or less, **M** ≈ 1.5–3 days, **L** ≈ 5+ days or a new
   persistent subsystem.

Evidence conventions: `[Rn]` numbers refer to **Sources** at the bottom; bare paths refer to files in this repo.

## Status of the first survey (F1–F24)

Shipped means present on `feat/parity` today; I re-checked each one in the tree rather than trusting the card
description. One line each, no re-research.

| # | Item | Status |
|---|---|---|
| F1 | Per-timeline view filters | **Shipped** — `AccountLocalPreferences` + `TimelineViewSettingsFragment` + `StatusListFragment` |
| F2 | Filter action `blur` | **Shipped** — reviewed and merged (commit `6922d1003`) |
| F3 | Hidden-domains manager | **Shipped** — `fragments/settings/DomainBlocksFragment.java` |
| F4 | Oldest-first reading order | Open |
| F5 | Settings search | Open |
| F6 | Media aspect-ratio options | Open |
| F7 | Emoji size + emoji autocomplete in CW/polls | Open |
| F8 | Small polish bundle (refresh all, poll durations, reverse bookmarks) | Open |
| F9 | Tablet / foldable two-pane | Open |
| F10 | Catch-up digest | Open |
| F11 | Boosts carousel | Open |
| F12 | Thread unroll | Open |
| F13 | Nested replies / thread tree | Open |
| F14 | Reply sorting | Open |
| F15 | Unified multi-account timeline | Open |
| F16 | Split timeline / resume pane | Open |
| F17 | App lock (PIN / biometric) | Open |
| F18 | Home-only mute | Open |
| F19 | Mute by posting client | Open |
| F20 | Font family choice / accessible fonts | Open — Toot!'s OpenDyslexic switch `[R23]` is an instance of this, not new |
| F21 | Auto-CW for long posts | Open (`collapseLongPosts` is a different mechanism) |
| F22 | Device-side scheduled posting | Open |
| F23 | Translate profile bio | Open |
| F24 | Anonymous instance browsing | Open |

Also already in the tree, so not findings: status edit history (`GetStatusEditHistory` +
`StatusEditHistoryFragment`), alt-text editing on published posts (`mediaAttributes` sent from
`ComposeFragment.java:850-852`), `Idempotency-Key` on publish (`CreateStatus.java:16`), quote posts,
GoToSocial-style interaction policies (`SetStatusInteractionPolicies`), markers, notification grouping,
exclusive lists, and setting a private note on an account.

Known blockers from the parity doc, not re-proposed: device-side drafts and scheduling, `allowRemoteLoading`
(which is what Mona sells as "Load from Remote Server" `[R15]`), the alt-text feed filter, and per-status
spoiler reveal state.

---

## Findings

| ID | Finding | Ships in | Server support | Why a user cares | Size |
|---|---|---|---|---|---|
| G1 | Thread composer (multi-post, per-post budget) | Mammoth, Ice Cubes | `in_reply_to_id` chaining; per-instance `max_characters` | Lets long-form writers post a real thread without posting N times and scrolling back | M–L |
| G2 | Undo-send window after publishing | Mammoth | `DELETE /api/v1/statuses/:id` (universal) | Catches the typo or wrong-account post in the first 20 seconds | S |
| G3 | Collections (curated account sets) | Phanpy, Ice Cubes (notifications) | Mastodon 4.6 / API v10 | The fediverse finally has a native "starter pack" for newcomers | M (read-only) / L (CRUD) |
| G4 | Live timeline over the streaming API | Ice Cubes | `configuration.urls.streaming`; SSE on Mastodon, WebSocket on GTS | New posts, edits and deletes appear without polling | L |
| G5 | Wellbeing controls (end of feed, grayscale, hide Explore) | Tusker, Phanpy | None | The one place a client can push back on infinite scrolling | S |
| G6 | Per-account boost suppression | Tusker | `showing_reblogs` / `reblogs=false` on follow (all versions); `languages[]` 4.0+ | Silences the three accounts that boost everything, without touching the rest | S |
| G7 | Alt text from image metadata | Phanpy | None (`description` on media upload) | Most photos from camera apps already carry a caption; typing alt text on a phone is why alt text is missing | S |
| G8 | Post as a shareable image | Mona, Mammoth | None (local render) | The clean, accessible alternative to screenshotting a post | S–M |
| G9 | Year in Posts / personal stats | Phanpy, Ivory | Own statuses, paginated; or the local cache | A reason to open the app that isn't a reflex | M |
| G10 | Remove follower without blocking | Mastodon web UI | `POST /api/v1/accounts/:id/remove_from_followers` (3.5+) | The middle path between "unfollow" and "block" | S |
| G11 | Search operators as UI | Mastodon web UI | Mastodon 4.2+ search prefixes | The fediverse's search is better than people think; mobile clients hide it | S |
| G12 | Soft mute: auto-CW for bots, warn-mutes | Toot!, Ivory | `account.bot`; local mute-with-warning | Keep useful bots and ambivalent people without letting them interrupt | S–M |
| G13 | Notes manager (all private notes in one place) | Mona, Toot! | `note` on Relationship; no list endpoint (local index) | Notes are how you remember why you followed someone; today they're write-only | S |
| G14 | Alt text shown inline in the timeline | Fedicat | None — `media_attachments[].description` is already on every status | A feed you can read with your ears, and alt text becomes part of the experience instead of a hidden field | S |
| G15 | Block everyone in a thread | Fedicat | `POST /api/v1/accounts/:id/block` per participant; no batch endpoint | Pile-ons are when people leave; blocking thirty accounts by hand is not a realistic ask | S–M |

---

### G1. Thread composer

**What it is.** A compose screen that holds several posts at once, each with its own text, media, content
warning and (optionally) poll, published in order as replies to each other. The character counter is per post,
against the instance's `max_characters`, and there is a visible "2 of 4" position. If post 3 fails, the thread
does not silently break: the remaining posts stay queued with a retry.

**Where it ships.** Mammoth's "Threader Mode to automate creating post threads — long posts are a breeze with
Mammoth" `[R1][R2]`; Ice Cubes, "You can write threads up to 5 posts" `[R3]`.

**Server support.** No thread object exists. Each post is `POST /api/v1/statuses` with `in_reply_to_id` set to
the previous post's returned id `[R25]`. The per-instance limit is already parsed in this fork
(`ComposeFragment.java:228`, `Instance.java:191`), and each request already carries its own `Idempotency-Key`
(`CreateStatus.java:16`), which matters: a retry after a timeout must not double-post.

**Why a user would care.** The people who write the most are the ones most punished by a single-post composer.
Posting a five-part thread today means posting, scrolling to your own post, replying, and repeating — while
hoping nobody replies in between and breaks the chain.

**Mammut today.** `ComposeFragment` holds one `replyTo` and sends one `CreateStatus` (`ComposeFragment.java:854`).
There is no queue and no thread UI.

**Traps.** Partial failure is the whole feature. Decide up front whether a failed middle post aborts the rest or
leaves them queued, and never publish post *n+1* before *n* returns an id. Do not couple this to the drafts
subsystem (blocked per `docs/MAMMUT-PARITY.md`) — an in-memory queue that dies with the screen is still useful
and much cheaper.

---

### G2. Undo-send window

**What it is.** After publishing, a snackbar with a countdown: "Posted · Undo". Tapping it deletes the status.
The window is user-configurable (Mammoth's is "within a custom duration").

**Where it ships.** Mammoth, "Undo Posts (within a custom duration)" `[R1][R2]`; TechCrunch's launch coverage
describes it as "undo posts with a custom duration, similar to Twitter" `[R4]`.

**Server support.** `DELETE /api/v1/statuses/:id` — present since forever, works on every Mastodon-compatible
server including GoToSocial.

**Why a user would care.** Wrong-account posts and typos are the most common public mistakes on any social app,
and a 20-second window is nearly free insurance.

**Mammut today.** Publish calls `CreateStatus` immediately and dismisses the composer. No undo.

**Traps — this is the one finding with a real honesty problem.** A delete-after-publish is not an undo: the post
is already public and federated, and push notifications may have gone out. Mastodon's `scheduled_at` minimum is
five minutes `[R25]`, so it cannot be used as a sub-minute buffer either. Two defensible designs:

1. **Delete-based (Mammoth's, probably):** publish immediately, delete on undo. Say "Undo" not "Cancel", and
   do not imply the post never existed.
2. **Delayed publish:** hold the post locally for N seconds, then POST. A true undo, but it adds a queue, a
   foreground-service question, and a failure mode where the app is killed mid-window.

Recommend (1) first. It is a day of work and the failure mode is a deleted post, not a lost one.

---

### G3. Collections

**What it is.** Named, description-and-hashtag-tagged sets of recommended accounts, with per-item consent
states, shareable by URL and shown on profiles. The protocol-native answer to Bluesky starter packs.

**Where it ships.** Phanpy shipped "Collections (Mastodon v4.6)" on 2026-06-22 `[R8]`; Ice Cubes ships the
notification side, "Added notifications for being added to a collection and for collection updates, on supported
servers" `[R9]`.

**Server support.** Mastodon 4.6.0, `mastodon` API version 10 `[R5][R6]`. Endpoints: `POST/GET/PATCH/DELETE
/api/v1/collections`, `POST /api/v1/collections/:id/items`, `DELETE .../items/:id`,
`POST .../items/:id/revoke`, `GET /api/v1/accounts/:id/collections`, `GET /api/v1/accounts/:id/in_collections`
`[R7]`. Statuses gain `tagged_collections`; notifications gain `added_to_collection` and `collection_update`;
reporting accepts `collection_ids` `[R6]`. Limits: 25 accounts in a collection created on a Mastodon server, up to
150 for a collection that originated on a non-Mastodon server, and a role-based `collection_limit` (default 10) `[R6]`.

**Why a user would care.** Discovery is the weakest part of the fediverse for a newcomer, and Collections is the
first server-side answer that does not depend on an algorithm. A client that renders them well is the one you
hand to a friend who is "thinking about joining".

**Mammut today.** No request class, no model, no UI (grep for `collections` in `api/requests/` returns nothing).

**Traps.** This is the newest API in the list, so the fallback has to be clean: read `api_versions` from
`/api/v2/instance` and hide every entry point (profile section, status link preview, notifications) below
version 10. Consent states matter — an item can be `pending`, and a collection you own shows items other users
cannot see `[R7]`. Start read-only: view a collection, open it from a profile, open a `tagged_collections` link.
Creating and editing is a second card.

---

### G4. Live timeline over the streaming API

**What it is.** A long-lived connection that pushes `update`, `delete`, `status.update`, `notification` and
`filters_changed` events, so the timeline is live, edits replace posts in place, and deletes disappear without a
refresh.

**Where it ships.** Ice Cubes "relies heavily on the streaming events of Mastodon to do stuff like showing new
posts live in the home timeline and editing and deleting your posts" `[R3]`.

**Server support.** Documented streaming API with a **hostname-discovery requirement**: the streaming server may
live on a different host, and clients must read `configuration.urls.streaming` from the instance entity (or follow
the redirect from `/api/v1/streaming`) before connecting `[R10]`. Mastodon serves server-sent events at
`/api/v1/streaming/{user,list,hashtag,public...}` `[R10]`. GoToSocial implements **only** the WebSocket flavour at
`wss://host/api/v1/streaming`, multiplexed with a `stream` query parameter `[R11][R12]` — so a client that speaks
only SSE silently gets no live updates on GTS.

**Why a user would care.** It removes the "pull to refresh to see anything new" tax and makes edit/delete
correctness automatic instead of incidental. It is also a battery and request-count win over polling every tab.

**Mammut today.** No streaming code at all. The only occurrence of the word is the parsed field
`Instance.Urls.streaming` (`model/Instance.java:216`).

**Traps.** This is the largest item here and the easiest to get wrong: connection lifecycle across process death,
backoff, doze, and per-tab subscription management. Keep the existing polling path as the permanent fallback and
treat streaming as an optimisation, not a replacement. If only one stream ships, ship `user` (home + notifications)
— it is the one users notice.

---

### G5. Wellbeing controls

**What it is.** Explicit ways to stop: an "end of feed" state instead of infinite pagination, a grayscale mode for
media, and the ability to remove Explore/trending from the interface entirely.

**Where it ships.** Tusker's App Store listing: "Digital wellness options … including by turning infinite scrolling
off or showing all images in grayscale" `[R13]`. Phanpy shipped "Wellbeing: hide trending, local or federated
timelines" on 2026-05-15 `[R8]`.

**Server support.** None. Pure client behaviour.

**Why a user would care.** The fediverse's pitch is "no algorithm", but an infinite feed is still an infinite feed.
A client that lets you read to the end is making a claim the others cannot.

**Mammut today.** `loadNewPosts` and `showNewPostsButton` exist, but nothing that ends the feed and no grayscale
option.

**Traps.** Frame it as a setting, not a lecture: no streaks, no guilt copy, no "you've been here 40 minutes"
interstitial. Grayscale is a `ColorMatrix` on the image pipeline — do not re-encode media. "End of feed" needs a
deliberate decision about gap-filling (`loadMore` vs `fillGap` already exist in the timeline code) or the button
will reappear forever on busy timelines.

---

### G6. Per-account boost suppression

**What it is.** A single action on a profile or following row: "Hide their boosts". Everything else about the
follow stays as it is.

**Where it ships.** Tusker 2023.4 release notes: "Add action to show hide/show reblogs from specific accounts"
`[R13]`.

**Server support.** This is a real server feature that clients mostly ignore: `Relationship.showing_reblogs`, set
with `reblogs=false` on `POST /api/v1/accounts/:id/follow` — the parameter predates 3.3.0, so it is safe to send
anywhere `[R14]`. Because it is server-side it stays in sync
across the web UI and other clients — unlike F1's per-tab boost filter, which is local.

**Why a user would care.** Three accounts that boost forty things a day can own a timeline. Muting them entirely
loses their posts; per-tab hide-boosts throws out every boost. This is the surgical version.

**Mammut today.** `Relationship.showingReblogs` is parsed (`model/Relationship.java:16`) and never written or
shown. The follow request does not send `reblogs`.

**Traps.** None worth noting; it is a switch and a request parameter. Worth doing alongside it: the same endpoint
accepts `notify` (3.3.0+) and `languages[]` (4.0.0+) `[R14]`, so a per-follow "notify me" and language filter come
almost free and would be the only mobile UI for either.

---

### G7. Alt text from image metadata

**What it is.** When you attach a photo that already carries a caption in its metadata (EXIF `ImageDescription` /
`XPTitle`, or IPTC `Caption-Abstract` inside the XMP packet), prefill the description field with it and let the
user edit before posting.

**Where it ships.** Phanpy, 2025-11-26: "Extract description from attached image metadata" `[R8]`.

**Server support.** None needed — it is a client-side read that populates `description` on the media upload
request `[R27]`.

**Why a user would care.** Alt text is missing mostly because typing it on a phone is tedious, and photo exports
from Lightroom, Apple Photos and most camera apps already carry a caption. This turns a chore into a confirmation.

**Mammut today.** EXIF is already read for orientation (`api/ResizedImageRequestBody.java:112`), so the parser and
the code path exist; only the caption tags are missing. The framework `android.media.ExifInterface` used there
exposes `TAG_IMAGE_DESCRIPTION` and `TAG_USER_COMMENT`; the IPTC case means reading the XMP packet, which is a
one-class swap to `androidx.exifinterface.media.ExifInterface` (`TAG_XMP`) or a short hand parse.

**Traps.** Never silently publish metadata text as alt text — always show it in the field for editing, and truncate
to the server's description limit. Some camera captions are filenames or dates, which is worse than nothing; a
plausibility check (length, not a bare date/filename pattern) is worth ten lines.

---

### G8. Post as a shareable image

**What it is.** Render a post — author, handle, text, media thumbnails, alt-text badge, timestamp — into a PNG at
post or story dimensions, with the option to include or exclude the handle.

**Where it ships.** Mona, "Share a post as picture" `[R15]`; Mammoth, "Share posts as images that you can decorate
as you wish, with custom colours and more" `[R2]`.

**Server support.** None. Local `Canvas` rendering.

**Why a user would care.** People already screenshot posts to send them elsewhere. A rendered card is legible,
carries alt text, and does not leak the rest of your screen — which is the actual privacy problem with screenshots.

**Mammut today.** No status-to-bitmap renderer; the only `Canvas` users are the QR code fragment and the avatar
cropper.

**Traps.** Keep it one screen with two or three choices, not a design tool. Include the alt text as a caption strip
by default (that is the accessibility argument), and make removing the handle a single tap, not a hidden setting.
Custom media (video, audio, polls) needs a defined fallback rather than a broken layout.

---

### G9. Year in Posts / personal stats

**What it is.** A local recap computed from your own posts: counts by month, top hashtags, most-favourited post,
reply-to-boost ratio. Phanpy's is framed as a year-in-review; Ivory's is a live statistics tab.

**Where it ships.** Phanpy, 2026-01-07: "Experimental 'Year In Posts'" `[R8]`. Ivory: "Our Statistics tab gives you
a clear, real-time view of your post activity and engagement. Easily track which posts are gaining traction"
`[R17]`.

**Server support.** `GET /api/v1/accounts/:id/statuses` with pagination, or the existing `CacheController` SQLite
cache. No new endpoint, no analytics service.

**Why a user would care.** It creates a reason to open the app that is not a reflex, and it is the most shareable
thing a client can produce without exposing anyone else's data.

**Mammut today.** No stats surface and no aggregation code.

**Traps.** Any count computed from a paginated fetch is incomplete; label it honestly ("your last N posts") or cap
the window. And do not rank posts by favourites as a primary frame — the creative direction may want the opposite
emphasis. This is the one finding where the product framing matters more than the code.

---

### G10. Remove follower without blocking

**What it is.** On a profile of someone who follows you: "Remove follower". They stop following you; they are not
blocked, not notified as a block, and can follow again later.

**Where it ships.** Mastodon's own web UI, added in 4.4: "Don't want someone to follow you anymore? You don't have
to block them, simply tap 'Remove follower' in the dropdown menu on their profile" `[R18]`. I found no Android
client that advertises it; Tusky's `strings.xml` contains no remove-follower string (checked 2026-11-07) `[R19]`.

**Server support.** `POST /api/v1/accounts/:id/remove_from_followers`, added in 3.5.0 `[R14]`. **Unverified** for
GoToSocial and Akkoma: I did not find this endpoint in GTS's documented API surface, so gate the action on a
successful call and hide it on 404 rather than assuming.

**Why a user would care.** The current choice is binary and both ends are bad: unfollow leaves them following you,
block is a loud social statement. Harassment-adjacent situations are exactly where people want the quiet option.

**Mammut today.** No request class (`grep -r remove_from_followers` returns nothing).

**Traps.** Do not bury it where it can be tapped by accident, and do not add a confirmation that explains the
other person's experience in detail. One line: "They will stop following you. They won't be blocked."

---

### G11. Search operators as UI

**What it is.** A chip row under the search field — From, Has, Is, Language, Before/After, In library — that builds
the query string in the field, so the user can see and edit what is being sent.

**Where it ships.** Mastodon's web UI and API. The operators are documented in the user docs: `has:` (`media`,
`poll`, `embed`), `is:` (`reply`, `sensitive`), `language:`, `from:` (`@user`, `@user@domain`, `me`), `before:`,
`after:`, `during:`, `in:` (`library`, `public`), combinable with `AND` and negatable with `-` `[R20]`. Fedi.Tips
records that they arrive in Mastodon 4.2.0 `[R21]`.

**Server support.** 4.2.0+. On older servers an unknown prefix is treated as plain text, so the degradation is a
normal search with no filtering — no error state needed. Status full-text search additionally depends on the server
having ElasticSearch; accounts and hashtags do not `[R22]`.

**Why a user would care.** "Find that post I favourited last spring about X" is a real, common, currently impossible
task on mobile. The server can already do it; the client just never exposes it.

**Mammut today.** `SearchFragment` sends a plain `q` with a type filter and has no operator affordance or help text
(`fragments/discover/SearchFragment.java`).

**Traps.** Start with `from:me`, `in:library`, `language:` and `has:media` — the four with obvious intent. Do not
build a full query builder; the value is discoverability, and a chip that inserts text into a visible field is the
cheapest honest version.

---

### G12. Soft mute: auto-CW for bots, and mute-with-warning

**What it is.** Two related reading rules. (a) Any account flagged `bot` gets its posts folded behind a content
warning automatically. (b) An account can be "muted, but shown with a warning" instead of removed from the timeline.

**Where it ships.** Toot!: "Added a settings flag to give all posts from bot accounts a content warning
automatically" `[R23]`. Ivory: "Support for 'Hide with a Warning' for Mutes and Filters" `[R24]`.

**Server support.** None for (a) — `account.bot` is on every account object `[R25]`. For (b), Mastodon's mute is
server-side and binary (`POST /api/v1/accounts/:id/mute` with `notifications` and `duration` `[R14]`); "mute but
warn" is local state layered on top, which also means it will not sync across devices unless stored per account in
`AccountLocalPreferences`.

**Why a user would care.** Feeds are full of accounts people follow for 10% of their posts. The current options are
"see everything" or "see nothing". A warning is the missing middle, and it reuses an interaction (tap to reveal)
that Mammut already has for filters and sensitive media.

**Mammut today.** Mute is binary; filters have warn/hide/blur but only for keywords.

**Traps.** (a) will misfire on good bots (feeds, weather, RSS bridges) — make it opt-in and per-account overridable.
(b) must not fight server-side filters: if a post is both muted-with-warning and filtered, one reveal should clear
both.

---

### G13. Notes manager

**What it is.** A screen listing every account you have written a private note about, searchable, with the note
editable in place and a tap through to the profile.

**Where it ships.** Mona: "User Notes list: browse, organize and edit all your notes in one place" `[R15]`. Toot!
17.2: "Support for adding private notes for other accounts" `[R23]`.

**Server support.** The `note` field on `Relationship`, written with `POST /api/v1/accounts/:id/note` `[R14]`. There
is **no endpoint that lists accounts you have notes on**, so the index has to be built locally from accounts the
app has seen (ids + note text), or derived from your following/followers pages. Say so in the empty state.

**Why a user would care.** A note is how you remember why you followed someone, or which of three similar accounts
is the actual expert. Mammut already lets you write the note and then never shows it again.

**Mammut today.** `SetPrivateNote` exists and is wired into `ProfileFragment`; there is no list, search, or edit view.

**Traps.** This is the weakest of the thirteen on evidence: both shipping apps are iOS and both rely on their own
sync (Mona syncs notes via iCloud `[R15]`). A local index will miss notes written elsewhere. Ship it only if the
empty/partial state can be made honest ("notes saved on this device").

---

### G14. Alt text inline in the timeline

**What it is.** Render each attachment's description as text under the media in the timeline — clamped to a few
lines, expandable — instead of hiding it behind an ALT badge and a tap.

**Where it ships.** Fedicat, which lists among its still-unique features "displaying alt-text immediately in the
timeline (besides encouraging alt-text, much of it is good reading)" `[R30]`.

**Server support.** None. `media_attachments[].description` arrives with every status `[R25]`; no extra request.

**Why a user would care.** For a TalkBack or VoiceOver user this is the difference between "image" and the actual
content of the post. For everyone else it makes alt text part of the feed rather than a compliance field, which is
the only reliable way to get more people to write it.

**Mammut today.** `showAltIndicator` / `showNoAltIndicator` draw a badge; the description itself appears only after
opening the attachment.

**Traps.** Default **off**: descriptions double the height of image-heavy posts and some are longer than the post.
Show only when a description exists, clamp to two or three lines with an explicit expand, and never render them for
avatars or custom emoji. Do not auto-translate them.

---

### G15. Block everyone in a thread

**What it is.** An overflow action on a thread: "Block everyone in this conversation" — it shows the count and the
list first, then blocks each account involved.

**Where it ships.** Fedicat lists "an option to block everyone in a thread (a real time-saver)" among its unique
features `[R30]`.

**Server support.** `POST /api/v1/accounts/:id/block`, one account at a time `[R14]`. The participant set comes from
`GET /api/v1/statuses/:id/context` (ancestors, descendants and their `account.id`), which the thread view already
fetches. There is no batch block endpoint.

**Why a user would care.** A pile-on is exactly the moment a user decides whether to stay on the network. Asking
them to open thirty profiles and block each one is asking them to leave. This is the highest-impact safety feature in
the list and it is not a moderation system — it is one loop over an endpoint the app already calls.

**Mammut today.** Blocking is per-profile only.

**Traps.** Consent and scope matter more than the code: show the count and the handles, skip accounts you already
block, and exclude people you follow by default (a good-faith critical reply should not be swept up). Thirty
sequential requests need the existing request queue with partial-failure reporting, and the confirmation should say
what blocking does and does not do (it does not stop them seeing public posts). Never make this a one-tap action
from a timeline row.

---

## Deliberately rejected

| Idea | Where it ships | Why not |
|---|---|---|
| Pinned post in a Picture-in-Picture window | Mammoth `[R2]` | Android lets any activity enter PiP, so it is technically possible — but PiP is a system floating window with no inline interaction, one activity at a time, and a gesture requirement. That is a lot of lifecycle risk for a novelty; a bottom sheet or a saved-post shortcut gets the same benefit without the window. |
| AR media viewing | Mammoth `[R2]` | ARCore gating, one novelty use, no daily value. |
| Sentiment analysis before posting | Mammoth `[R2]` | Needs a model or provider, and a client scoring your tone is both unreliable and patronising. If it exists at all it is a design/voice question. |
| AI compose and AI alt text via a third-party API | Ice Cubes uses the OpenAI API `[R3]`; Ivory now does alt text on-device with Apple Intelligence `[R24]` | Requires a user key or OS-gated hardware, and ships post text to a third party — a privacy regression for a client whose pitch is "no algorithm". Revisit only if Android exposes an on-device captioner. |
| Tag groups / multi-hashtag timelines | Ice Cubes `[R3]`, Phanpy `[R8]` | Already rejected in survey 1: duplicates hashtag tabs and adds a timeline type for a marginal gain. |
| Misskey antennas (condition-based custom timelines) | Misskey `[R26]`, Aria for Misskey `[R28]` | Server-side engine over the firehose. A client can only filter what it happened to fetch, so the feature would silently lie about completeness — the worst property a timeline can have. |
| Misskey clips / named bookmark folders | Misskey `[R26]`, Aria `[R28]` | Server-side in Misskey; Mastodon bookmarks are flat, so a local folder layer would be invisible on the web and in every other client. |
| Elk's "connecting linked posts" in the timeline | Elk `[R29]` | Reorders a cursor-paginated list, which breaks recency and paging invariants (the exact class of bug F1 had to avoid). The thread view already does this job. |
| Regex mute filters | Ivory `[R17]` | Rejected in survey 1 and still right: ReDoS risk plus a second filtering engine beside the server's. |
| One client for every fediverse platform (Mastodon, Misskey/Sharkey, Pixelfed, NeoDB…) | Fedicat `[R30]`, Aria for Misskey `[R28]` | Different APIs and object models. That is a second product, not a feature. |
| Per-platform feature adapters (Pixelfed collections, NeoDB shelves, Pleroma bookmark folders, Akkoma bubble feed) | Fedicat `[R30]` | Each is a platform-specific capability behind a compatibility check. Individually cheap, collectively a maintenance surface with no Mastodon user to serve. If Mammut ever targets GoToSocial properly, do it there — GTS is Mastodon-API compatible in a way Pixelfed's extras are not. |
| Single "bloost" button (like + boost in one action) | Fedicat `[R30]` | Clever, and Fedicat's author likes it — but a boost is public and a favourite is not, so one button makes an irreversible action easy to trigger by accident. If it ships at all, it belongs as a long-press behaviour that the user opts into. |
| Lemmy multi-community feeds / PieFed feeds | Connect for Lemmy, PieFed `[R31]` | A per-platform workaround for a missing server feature (Lemmy's own issue #818 is still open). Mastodon already has the primitives — lists and hashtag tabs — so the same UI would be a fourth way to build a timeline. |
| Hot/active/scaled ranking of a feed | Lemmy `[R32]` | Needs votes and server-side aggregation (Lemmy's formula uses active-user counts per community). A client can only rank the page it already fetched, which produces a "hot" sort that reshuffles as you paginate — dishonest by construction. |
| Read Later integrations (Instapaper, Pinboard, Pocket, Readwise) | Ivory `[R17]` | The share sheet already reaches all of them; hard-coding five services is maintenance for no new capability. |
| OpenDyslexic font switch | Toot! `[R23]` | Real and good, but it is an instance of F20 (font family choice), already on the list. |
| "Potential Spam" filter preset | Ivory `[R24]` | A canned keyword filter. The capability exists; the preset is copy and belongs with design. |
| Client-side language detection | Phanpy, using TinyLD `[R8]` | A trained model in the app to compensate for a `language` field servers often set wrong. The honest fix is the per-follow `languages[]` filter (Mastodon 4.0+) that comes free with G6 `[R14]`. |
| Second-tap scroll-to-top returns you to where you were | Toot! `[R23]` | Lovely, and worth doing — but it is interaction polish for the design brief, not a research finding. |

### Things I checked and dropped (already in Mammut)

Worth recording so nobody re-opens them: status edit history, editing alt text on a published post (through the
edit-status `media_attributes` path), `Idempotency-Key`, quote posts, GoToSocial interaction policies, markers,
notification grouping, hidden domains, quote-revoke, batch status fetch (`GetStatusesByIDs`), and setting a private
note. Several candidate findings died here; that is the method working.

---

## Sources

- **[R1]** Mammoth for Mastodon, App Store listing (Threader Mode, Undo Posts, share-as-image, PiP, AR, sentiment analysis, columns) — <https://apps.apple.com/us/app/id1667573899>
- **[R2]** Mammoth first-look announcement, 2023-02-24 (same feature list, primary source) — <https://getmammoth.wordpress.com/2023/02/24/the-mammoth-first-look-is-live-on-the-app-store>
- **[R3]** Ice Cubes README (streaming-driven timeline, threads up to 5 posts, tag groups, remote timelines, AI tools) — <https://github.com/Dimillian/IceCubesApp>
- **[R4]** TechCrunch, "Mammoth is a free Mastodon app for iOS", 2023-02-27 (undo with custom duration) — <https://techcrunch.com/2023/02/27/mammoth-is-a-free-mastodon-app-for-ios-that-makes-it-easier-to-get-started>
- **[R5]** Mastodon 4.6 for Developers (Collections, notification types, interactionPolicy.canFeature) — <https://blog.joinmastodon.org/2026/06/mastodon-4-6-for-devs>
- **[R6]** Mastodon docs, "Implementing Collections" (API v10, consent, limits, `tagged_collections`) — <https://docs.joinmastodon.org/client/collections>
- **[R7]** Mastodon docs, collections API methods (endpoint list and version history) — <https://docs.joinmastodon.org/methods/collections>
- **[R8]** Phanpy CHANGELOG (Collections 2026-06-22; wellbeing 2026-05-15; Year In Posts 2026-01-07; image-metadata alt text 2025-11-26; edit-history snapshots 2025-09-17) — <https://github.com/cheeaun/phanpy/blob/main/CHANGELOG.md>
- **[R9]** Ice Cubes for Mastodon, App Store listing (collection notifications, streaming, tag groups) — <https://apps.apple.com/vc/app/ice-cubes-for-mastodon/id6444915884>
- **[R10]** Mastodon docs, streaming API (hostname discovery via `configuration.urls.streaming`, event types) — <https://docs.joinmastodon.org/methods/streaming/>
- **[R11]** GoToSocial docs, WebSocket (`wss://host/api/v1/streaming`, why proxy config matters) — <https://docs.gotosocial.org/en/latest/getting_started/reverse_proxy/websocket>
- **[R12]** Mastodon.py issue #432, "Websocket streaming (compat w/ GoToSocial)" — GTS implements only the websocket stream, not SSE — <https://github.com/halcy/Mastodon.py/issues/432>
- **[R13]** Tusker, App Store listing and version history (digital wellness, notification grouping, per-account reblog toggle in 2023.4) — <https://apps.apple.com/app/id1498334597>; Tusker site (multi-window, timeline sync) — <https://vaccor.space/tusker>
- **[R14]** Mastodon docs, accounts API (follow with `reblogs`/`notify`/`languages[]`, `showing_reblogs`, mute with duration, note, `remove_from_followers` 3.5.0) — <https://docs.joinmastodon.org/methods/accounts/>
- **[R15]** Mona for Mastodon, App Store listing ("Share a post as picture", "Load from Remote Server", "User Notes list", iCloud sync of notes and positions) — <https://apps.apple.com/us/app/mona-27-for-mastodon/id6755672518> and <https://apps.apple.com/us/app/mona-for-mastodon/id1659154653>
- **[R16]** 9to5Mac, "Here are the best Mastodon apps for iPhone, iPad, and Mac" (Mona split view, Mammoth columns/threader/undo) — <https://9to5mac.com/2023/07/05/here-are-the-best-mastodon-apps-for-iphone-ipad-and-mac>
- **[R17]** Ivory for Mastodon, Tapbots product page (Timeline Filters, Statistics tab, Read Later, regex mute filters) — <https://tapbots.com/ivory>
- **[R18]** Mastodon 4.4 announcement ("Remove follower" in the profile dropdown; enhanced list management) — <https://blog.joinmastodon.org/2025/07/mastodon-4.4>
- **[R19]** Tusky `strings.xml` on `develop`, checked 2026-11-07 — no remove-follower string — <https://github.com/tuskyapp/Tusky/blob/develop/app/src/main/res/values/strings.xml>
- **[R20]** Mastodon user docs, "Using the network features" (search prefixes `has:`, `is:`, `language:`, `from:`, `before:`, `after:`, `during:`, `in:`) — <https://docs.joinmastodon.org/user/network>
- **[R21]** Fedi.Tips, "How do I search for stuff on Mastodon?" (operators arrive in 4.2.0) — <https://fedi.tips/how-do-i-search-for-stuff-on-mastodon>
- **[R22]** Mastodon docs, search API (ElasticSearch requirement for status full-text, `resolve`/`offset` behaviour) — <https://docs.joinmastodon.org/methods/search/>
- **[R23]** Toot! for Mastodon, App Store listing and version history (v1.14 bot auto-CW; v17.2 edit history and private notes; v17.4 OpenDyslexic and Dynamic Type; v20 timeline sync and second-tap scroll restore) — <https://apps.apple.com/us/app/toot/id1229021451>; mirror of the same version history — <https://indieappcatalog.com/app/605/changelog>
- **[R24]** Ivory changelog mirror ("Hide with a Warning" for mutes and filters; "Potential Spam" filter; 2.5.4 on-device Apple Intelligence alt text) — <https://indieappcatalog.com/app/187/changelog>
- **[R25]** Mastodon docs, statuses API (post with `in_reply_to_id`, `scheduled_at` minimum 5 minutes, `Idempotency-Key`, `media_attributes` on edit, `GET /:id/history`) — <https://docs.joinmastodon.org/methods/statuses/>
- **[R26]** Misskey Hub, Clips and Antennas — <https://misskey-hub.net/en/docs/for-users/features/clip>, <https://misskey-hub.net/en/docs/for-users/features/antenna>
- **[R27]** Mastodon docs, media API (`POST /api/v2/media` with `description`; `PUT /api/v1/media/:id` is pre-publish only) — <https://docs.joinmastodon.org/methods/media/>
- **[R28]** Aria for Misskey, App Store listing (timelines as tabs, clips, lists, antennas) — <https://apps.apple.com/us/app/aria-for-misskey/id6499410880>
- **[R29]** Elk docs, Features ("Connecting linked posts in the timeline", grouped notifications, markdown/code blocks) — <https://docs.elk.zone/guide/features>
- **[R30]** Fedicat "About" (inline alt text in timeline, "bloost" like+boost button, block everyone in a thread, per-platform feature list) — <https://fedicat.com/about>; FediDB app entry with the supported-platform list (Pixelfed collections, NeoDB shelves, Pleroma bookmark folders, Akkoma bubble feed, GTS interaction policy) — <https://fedidb.com/apps/fedicat>
- **[R31]** Connect for Lemmy, "MultiCommunities are here!" release note (client-side combined community feeds) — <https://mtgzone.com/post/1616765>; Lemmy issue #818, "Support for grouping communities / multi-communities" (still an open request) — <https://github.com/LemmyNet/lemmy/issues/818>
- **[R32]** Lemmy ranking algorithm (Active / Hot / Scaled, votes and active-user counts) — <https://join-lemmy.org/docs/contributors/07-ranking-algo.html>
- **[R33]** Woolly for Mastodon, App Store listing (pinnable timelines, threaded replies, multi-column iPad, filters, widgets) — <https://apps.apple.com/us/app/woolly-for-mastodon/id6444360628>

## Confidence notes

- **Verified directly** (primary listing, README, changelog or API docs read this session): Mammoth, Ice Cubes,
  Mona, Tusker, Phanpy, Elk, Misskey, Aria, Fedicat, Woolly, and every Mastodon API version in the findings.
- **Coverage note on Woolly:** its listing advertises pinnable timelines, threaded replies, iPad columns, timeline
  filters, widgets and GIPHY `[R33]` — all of which map onto findings already in survey 1 (F1, F9, F13) or are
  cosmetic, so it contributed no finding. It is not unexamined, just redundant.
- **Partially verified:** Toot!'s feature history comes from App Store listing text; `apps.apple.com` serves a
  JavaScript page that is awkward to read, so the version-history claims for v1.14, v17.2, v17.4 and v20 were read
  from the listing text and a changelog mirror `[R23]`, not by running the app. Ivory's "Hide with a Warning" comes
  from a changelog mirror `[R24]`; the Tapbots product page confirms the filters and statistics tab but not that
  exact wording.
- **Unverified:** how Mammoth implements undo (delete-after-publish vs a delayed queue) — the listing only advertises
  the window. Whether GoToSocial and Akkoma implement `remove_from_followers` — not found in GTS's documented
  surface, so G10's fallback assumes 404. Whether Akkoma/Pleroma support SSE streaming as well as WebSocket — not
  checked; the polling fallback covers it.
- **"No Android client ships X"** is scoped to the clients surveyed in the first note (Tusky, Pachli, Fedilab,
  SubwayTooter, Trunks, Kurage) plus a direct `strings.xml` check of Tusky for G10. It is not a claim about every
  client on F-Droid.
- Effort sizes are estimated from this fork's structure (`ComposeFragment`, `BaseStatusListFragment`,
  `StatusDisplayItem`, `fragments/settings`), not measured.
