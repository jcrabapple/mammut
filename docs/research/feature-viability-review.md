# Mammut: Second Feature Survey Viability Review

**Date:** 2026-11-07  
**Author:** reviewer (kanban `t_8002cca1`)  
**Branch:** `feat/parity`  
**Input:** `docs/research/fediverse-feature-survey-2.md` (card `t_0c89305c`), `docs/FEATURE-PLAN.md`, `docs/MAMMUT-PARITY.md`  
**Target:** `docs/research/feature-viability-review.md`  

---

## 1. Executive Summary

The second feature survey (`docs/research/fediverse-feature-survey-2.md`) brought forward 15 candidate findings (G1–G15) aimed at making Mammut distinctive rather than merely matching other clients.

As an engineering gate, this review evaluates each finding against server support, buildability within Mammut's Java/AppKit/ViewPager architecture, effort/blast radius, single-device verifiability, and evidence quality.

### Verdict Summary
- **go (5):** G5, G7, G8, G11, G14
- **go-if (5):** G1, G3, G10, G12, G15
- **no (5):** G2, G4, G6, G9, G13

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ VERDICT MATRIX                                                              │
├─────────┬──────────────────────────────────┬─────────┬──────────────────────┤
│ Finding │ Title                            │ Verdict │ Size / Effort        │
├─────────┼──────────────────────────────────┼─────────┼──────────────────────┤
│ G1      │ Thread composer                  │ go-if   │ M–L (3–5 days)       │
│ G2      │ Undo-send window                 │ no      │ Trap (deceptive/loss)│
│ G3      │ Collections (curated packs)      │ go-if   │ M (2–3 days read)    │
│ G4      │ Live streaming timeline          │ no      │ L (2+ weeks, arch)   │
│ G5      │ Wellbeing controls               │ go      │ S (1–1.5 days)       │
│ G6      │ Per-account boost suppression    │ no      │ Already Shipped (0d) │
│ G7      │ Alt text from image metadata     │ go      │ S (1 day)            │
│ G8      │ Post as a shareable image        │ go      │ S–M (1.5–2 days)     │
│ G9      │ Year in Posts / personal stats   │ no      │ Trap (rate limits)   │
│ G10     │ Remove follower without blocking │ go-if   │ S (0.5–1 day)        │
│ G11     │ Search operators as UI           │ go      │ S (1 day)            │
│ G12     │ Soft mute (bot CW / warn-mute)   │ go-if   │ S (1 day, bot only)  │
│ G13     │ Notes manager                    │ no      │ Trap (no list API)   │
│ G14     │ Alt text inline in timeline      │ go      │ S (1 day)            │
│ G15     │ Block everyone in a thread       │ go-if   │ S–M (1.5–2 days)     │
└─────────┴──────────────────────────────────┴─────────┴──────────────────────┘
```

---

## 2. Evaluation of Findings (G1–G15)

### G1. Thread composer
- **Server support:** Universal. Mastodon has no thread entity; multi-post threads are constructed via sequential `POST /api/v1/statuses` with `in_reply_to_id` set to the previous status ID. `Idempotency-Key` is already supported in `CreateStatus.java`.
- **Buildability in this codebase:** Difficult. `ComposeFragment.java` is a 1,414-line monolithic ViewPager fragment built around a single draft, a single `ComposeEditText`, and tight coupling to `ComposeMediaViewController`. Rebuilding it into a dynamic multi-post composer in Java/AppKit without Compose requires either a dynamic `RecyclerView` of card editors or a multi-page pager, with complex touch, focus, and attachment handling.
- **Effort and blast radius:** M–L (3–5 engine days). Touches `ComposeFragment`, media upload binding, and state preservation across configuration change.
- **Verifiability:** Moderate. A multi-post thread can be tested on a single account. Testing partial-failure states (e.g. post 2 fails) requires synthetic fault injection (airplane mode mid-flight).
- **Evidence quality:** Strong. Shipped in Mammoth and Ice Cubes.
- **Verdict:** **go-if**
  - *Reason:* High user value for long-form writers, but only buildable if strictly bounded to avoid the blocked drafts subsystem.
  - *Smallest viable version:* An in-memory thread composer capped at 4 posts with sequential publishing; if post *n* fails, stop publishing immediately and retain the remaining posts in the editor with retry.

---

### G2. Undo-send window
- **Server support:** Universal for delete (`DELETE /api/v1/statuses/:id`). None for client-side delay.
- **Buildability in this codebase:**
  - *Delete-after-publish:* Trivial (S, 1 day) via a `Snackbar` calling `DeleteStatus.java`.
  - *Delayed publish:* High friction. Requires delaying `ComposeFragment` dismissal or keeping an in-memory timer. Without a persistent outbox service (which is blocked per `docs/MAMMUT-PARITY.md`), process death or swiping the app away destroys pending posts.
- **Effort and blast radius:** S (1 day) for delete-after-publish; M–L for persistent delayed queue.
- **Verifiability:** High. Immediately verifiable on a single account.
- **Evidence quality & Trap analysis:**
  - *The Trap:* Delete-after-publish is **fundamentally dishonest**. As soon as `POST /statuses` returns 200, the server federates `Create(Note)` over ActivityPub to all remote follower instances, and remote push notifications fire immediately. Issuing `DELETE` 10 seconds later cannot revoke push notifications that already sounded on recipients' phones, nor can it retract posts from relays or fediverse servers that ignore remote deletes.
  - Conversely, delayed publish without a persistent SQLite queue drops posts whenever Android reclaims memory.
- **Verdict:** **no**
  - *Reason:* A delete-after-publish is a fake undo that leaks federated notifications; a delayed publish without a persistent outbox drops posts on process termination.

---

### G3. Collections (curated account sets / starter packs)
- **Server support:** Extremely new: Mastodon 4.6.0 (API v10). Mastodon instances below 4.6, GoToSocial, and Akkoma return 404.
- **Buildability in this codebase:**
  - *Read-only:* M (2–3 days). `InstanceV2.java:60` already parses `apiVersions`. An API v10 gate can cleanly suppress Collections UI on older servers. Read-only viewer can reuse `BaseRecyclerFragment`.
  - *CRUD (create/edit/curate):* L (5+ days), involving consent workflows (`pending`/`accepted`) and role limits.
- **Effort and blast radius:** M for read-only viewer; L for full management.
- **Verifiability:** Hard. Requires a Mastodon 4.6+ instance, which is not yet widely deployed across test environments.
- **Evidence quality:** Documented in Mastodon official 4.6 docs; shipped in Phanpy.
- **Verdict:** **go-if**
  - *Reason:* Excellent newcomer discovery feature, but only viable as a read-only viewer gated strictly on API v10, deferred until a 4.6 server is available for verification.
  - *Smallest viable version:* Read-only viewer for `tagged_collections` status links and a profile Collections tab, visible only when `instance.getApiVersion("collections") >= 10`.

---

### G4. Live timeline over the streaming API
- **Server support:** Fractured across platforms. Mastodon serves Server-Sent Events (SSE) requiring hostname discovery via `configuration.urls.streaming`. GoToSocial serves **only** WebSockets (`wss://host/api/v1/streaming`) and rejects SSE. Akkoma/Pleroma support is uneven.
- **Buildability in this codebase:** Massive architectural mismatch. Mammut's timeline foundation (`StatusListFragment`, `BaseStatusListFragment`, `CacheController`) is built around cursor-based HTTP pagination (`maxID`) and manual/pull refresh. Live insertion of items into an active `RecyclerView` causes reading jumps, disrupts cursor boundaries, and requires complex background lifecycle management (Doze, standby, network drops).
- **Effort and blast radius:** L (2+ weeks; an entire dedicated subsystem). Touches networking core, background power, and timeline synchronization.
- **Verifiability:** Difficult. Requires multi-client live message dispatch and network drop testing.
- **Evidence quality:** Shipped in Ice Cubes (iOS/SwiftUI).
- **Verdict:** **no**
  - *Reason:* Protocol incompatibility between Mastodon SSE and GoToSocial WebSocket, severe battery/reconnect complexity on Android, and timeline scroll jumping fight the AppKit architecture.

---

### G5. Wellbeing controls (end of feed, grayscale, hide Explore)
- **Server support:** None required (100% client-side).
- **Buildability in this codebase:** Very high (S: 1–1.5 days).
  - *Grayscale:* Applied to image views via `ColorMatrixColorFilter` (`new ColorMatrix().apply { setSaturation(0) }`) in `MediaGridLayout` and avatar holders.
  - *End of feed:* In `StatusListFragment.onScrolled()`, suppress automatic bottom-pagination and show a static "You're all caught up" footer with an optional manual "Load older posts" button.
- **Effort and blast radius:** S (1–1.5 days). Isolated to `StatusListFragment`, `MediaGridLayout`, and `GlobalUserPreferences`.
- **Verifiability:** High. 100% testable on a single device/account.
- **Evidence quality:** Strong. Shipped in Tusker and Phanpy.
- **Verdict:** **go**
  - *Reason:* Cheap, robust, completely isolated client-side feature that directly supports mindful consumption.
  - *Smallest viable version:* A "Mindful browsing" settings section with two toggles: "Endless scrolling" (off = bottom stops and requires tap to load more) and "Grayscale media" (applies saturation=0 filter).

---

### G6. Per-account boost suppression
- **Server support:** Universal (`reblogs=false` on `POST /api/v1/accounts/:id/follow`, `showing_reblogs` on `Relationship`).
- **Buildability in this codebase:** **ALREADY SHIPPED.**
  - `ProfileFragment.java:808`:
    `menu.findItem(R.id.hide_boosts).setTitle(getString(relationship.showingReblogs ? R.string.hide_boosts_from_user : R.string.show_boosts_from_user));`
  - `ProfileFragment.java:852`:
    `new SetAccountFollowed(account.id, true, !relationship.showingReblogs, relationship.notifying)`
  - `AccountViewHolder.java:262, 358`:
    Also fully wired and operational in account lists.
- **Effort and blast radius:** 0 days. Already in production in the tree.
- **Verifiability:** Verified directly in source.
- **Evidence quality:** The researcher's claim that `showingReblogs` was "never written or shown" was factually incorrect.
- **Verdict:** **no**
  - *Reason:* Already fully implemented and shipped in `ProfileFragment` and `AccountViewHolder`.

---

### G7. Alt text from image metadata
- **Server support:** None required. Client-side extraction populating `description` on `POST /api/v2/media`.
- **Buildability in this codebase:** Excellent (S: 1 day).
  - Framework `android.media.ExifInterface` is already in active use in `ResizedImageRequestBody.java:112`.
  - Android framework `ExifInterface` natively supports `TAG_IMAGE_DESCRIPTION` and `TAG_USER_COMMENT` across JPEG and PNG files with **zero new third-party dependencies**.
  - In `ComposeFragment.java:648, 1058, 1062`, when media is picked, extract the description from EXIF and pass it into `mediaViewController.addMediaAttachment(uri, description)`.
- **Effort and blast radius:** S (1 day). Touches only media intake in `ComposeFragment`.
- **Verifiability:** High. Testable on a single device with a test JPEG carrying EXIF metadata.
- **Evidence quality:** Strong. Shipped in Phanpy.
- **Verdict:** **go**
  - *Reason:* High accessibility impact for minimal effort, uses standard Android framework APIs, and requires no external dependencies.
  - *Smallest viable version:* Read `TAG_IMAGE_DESCRIPTION` / `TAG_USER_COMMENT` via framework `ExifInterface` upon image pick, prefilling the description field for user review.

---

### G8. Post as a shareable image
- **Server support:** None required (pure local `Canvas` bitmap rendering).
- **Buildability in this codebase:** High (S–M: 1.5–2 days).
  - Inflate a dedicated card layout (`layout/card_shareable_post.xml`) with avatar, display name, handle, post body, and media thumbnails.
  - Perform standard Android off-screen measurement (`measure()`, `layout()`, `draw(canvas)`), export to a PNG cache file, and open `UiUtils.openSystemShareSheet()`.
  - Avoids Compose; uses standard Android View rendering.
- **Effort and blast radius:** S–M (1.5–2 days). Contained in a single utility class (`PostImageGenerator`) and an overflow menu item on `StatusDisplayItem`.
- **Verifiability:** High. Fully testable on a single device with any post.
- **Evidence quality:** Shipped in Mona and Mammoth.
- **Verdict:** **go**
  - *Reason:* Clean, self-contained, high utility for sharing fediverse posts to external platforms without messy screenshots.
  - *Smallest viable version:* "Share as Image" action in post overflow menu rendering a 1080px wide card (avatar, handle, text, timestamp, instance domain) to a PNG sent to the system share sheet.

---

### G9. Year in Posts / personal stats
- **Server support:** Inadequate. Mastodon has no aggregation or statistics API.
- **Buildability in this codebase:**
  - The survey suggested computing stats from "the local cache". However, `CacheController.java` **only** caches `home_timeline` and `notifications`; it does not cache the user's historical statuses.
  - Paginating through thousands of past statuses via `GET /api/v1/accounts/:id/statuses` (40 per request) over the network will immediately exhaust Mastodon's API rate limits (300 requests / 5 minutes), take minutes of spinner loading, and burn user mobile bandwidth.
- **Effort and blast radius:** M–L (3–5 days). Fragile network scraper with high failure rate.
- **Verifiability:** Requires an account with years of activity; rate-limit prone.
- **Evidence quality:** Phanpy runs on web with desktop connections; Ivory uses local Tapbots sync.
- **Verdict:** **no**
  - *Reason:* The local cache does not store user posts, and paginating years of statuses over REST hits severe API rate limits and data overhead.

---

### G10. Remove follower without blocking
- **Server support:** Mastodon 3.5.0+ (`POST /api/v1/accounts/:id/remove_from_followers`).
  - GoToSocial & Akkoma: Unverified / missing. Returns 404 on unsupported servers.
- **Buildability in this codebase:** Very high (S: <1 day).
  - `Relationship.java:13` already contains `public boolean followedBy;`.
  - Requires adding one 15-line request class `RemoveFromFollowers extends MastodonAPIRequest<Relationship>` and an overflow menu item in `ProfileFragment.java` visible when `relationship.followedBy == true`.
  - Safe error handling: on 404, toast "Not supported by this server".
- **Effort and blast radius:** S (0.5–1 day). Touches only `ProfileFragment` menu.
- **Verifiability:** Requires an account that follows the test user to verify live.
- **Evidence quality:** Standard Mastodon web UI feature since 4.4 / API 3.5.0.
- **Verdict:** **go-if**
  - *Reason:* Trivial to implement and valuable for quiet boundaries, but requires a follower account to verify and must cleanly handle 404 on GoToSocial.
  - *Smallest viable version:* "Remove follower" in `ProfileFragment` overflow when `relationship.followedBy` is true, executing the POST and updating relationship state.

---

### G11. Search operators as UI
- **Server support:** Mastodon 4.2.0+. Operators (`from:me`, `has:media`, `is:reply`, `in:library`, `has:poll`, `language:`).
  - On older instances (<4.2), operators degrade gracefully to plain search text tokens without crashing. Full-text search requires ElasticSearch on the instance.
- **Buildability in this codebase:** High (S: 1 day).
  - `SearchQueryFragment.java` already manages search entry, text input, and recent queries.
  - Adding a horizontal row of filter chips (`from:me`, `has:media`, `in:library`, `has:poll`) that append/toggle text prefixes in the search field is standard Android View work.
- **Effort and blast radius:** S (1 day). Confined strictly to `SearchQueryFragment` and its layout.
- **Verifiability:** High. Testable on a single account by searching own posts or bookmarks.
- **Evidence quality:** Strong. Official Mastodon search feature.
- **Verdict:** **go**
  - *Reason:* Unlocks existing, powerful server search capabilities with zero backend risk and clean degradation on older instances.
  - *Smallest viable version:* A horizontal chip row in `SearchQueryFragment` with 4 operators (`from:me`, `has:media`, `in:library`, `has:poll`) that insert query prefixes when clicked.

---

### G12. Soft mute (auto-CW for bots, mute-with-warning)
- **Server support:**
  - *Bot auto-CW:* Universal (`account.bot` is present on every Mastodon account object).
  - *Mute-with-warning:* Zero server support. Mastodon's server mute (`POST /api/v1/accounts/:id/mute`) removes posts entirely from the timeline before the client ever receives them.
- **Buildability in this codebase:**
  - *Bot auto-CW:* Clean (S: 1 day). In `StatusDisplayItem.java`, if `status.account.bot` and user preference is enabled, wrap in `SpoilerStatusDisplayItem` with title "Bot account".
  - *Mute-with-warning:* Fragile trap. Because the server drops muted posts, a "mute with warning" must NOT call Mastodon's mute API. It requires maintaining a private, unsynced local list of IDs in `AccountLocalPreferences`.
- **Effort and blast radius:** S (1 day) for bot auto-CW; M for local warn-mute database.
- **Verifiability:** High for bot auto-CW; moderate for warn-mute.
- **Evidence quality & Trap analysis:**
  - Local warn-mutes cannot sync across devices or to web Mastodon, creating confusing state discrepancies.
  - Bot auto-CW is fully supported by the data model and straightforward to implement.
- **Verdict:** **go-if**
  - *Reason:* Accept the bot auto-CW portion; strictly reject the device-local "mute-with-warning" trap.
  - *Smallest viable version:* An opt-in toggle "Collapse bot posts" in Timeline Settings that wraps posts from `account.bot == true` accounts in a content warning spoiler.

---

### G13. Notes manager
- **Server support:** **Zero list API.** Mastodon has `POST /api/v1/accounts/:id/note` and returns `Relationship.note`, but has **no endpoint** to list accounts that have notes (no `/api/v1/notes` or filter).
- **Buildability in this codebase:** Fragile.
  - Mammut would have to crawl hundreds of followed accounts to discover notes, or only index notes typed on that specific phone.
  - Notes written on web Mastodon or another client are completely invisible to the app. Reinstalling or clearing cache wipes the index.
- **Effort and blast radius:** M (2–3 days) for an unsynced, partial local database.
- **Verifiability:** Low. Requires pre-existing notes and multiple accounts.
- **Evidence quality & Trap analysis:**
  - The survey notes that Mona syncs notes via iCloud. Mammut has no private cloud sync. Building a manager for cloud data that the cloud server cannot enumerate is an architectural anti-pattern.
- **Verdict:** **no**
  - *Reason:* Mastodon provides no endpoint to list accounts with notes; a device-local index will miss notes written on web/other clients and cannot sync.

---

### G14. Alt text shown inline in the timeline
- **Server support:** None required (`media_attachments[].description` is already provided in every status payload).
- **Buildability in this codebase:** High (S: 1 day).
  - In `StatusDisplayItem.java`, when `showInlineAltText` preference is enabled and an attachment has a non-empty `description`, render a clamped 2–3 line caption block under the media grid with an expand toggle.
  - Integrates directly into the existing `StatusDisplayItem` display tree without altering pagination or cursors.
- **Effort and blast radius:** S (1 day). Touches `StatusDisplayItem`, layout, and `GlobalUserPreferences`.
- **Verifiability:** High. 100% testable on a single device with any image post containing alt text.
- **Evidence quality:** Strong. Shipped in Fedicat.
- **Verdict:** **go**
  - *Reason:* Major accessibility and readability enhancement; zero network cost, low effort, isolated blast radius.
  - *Smallest viable version:* An opt-in toggle in Appearance Settings "Show image descriptions inline" that renders a subtle, clamped 2-line text card below media attachments.

---

### G15. Block everyone in a thread
- **Server support:** `POST /api/v1/accounts/:id/block` (single account only; no batch endpoint). Participants discovered via `GET /api/v1/statuses/:id/context`.
- **Buildability in this codebase:** Moderate (S–M: 1.5–2 days).
  - In `ThreadFragment.java`, participants are already loaded in `data`.
  - However, blocking is a destructive, irreversible ActivityPub action that immediately severs bidirectional follow relationships.
  - Executing 30 sequential HTTP POSTs risks rate limiting (300 requests / 5 min) and leaves the user in an inconsistent state if interrupted midway.
- **Effort and blast radius:** S–M (1.5–2 days). High blast radius for user social graph.
- **Verifiability:** Low without multi-account test threads.
- **Evidence quality & Trap analysis:**
  - High risk of accidental collateral damage (blocking mutuals or followed accounts who participated in a discussion).
- **Verdict:** **go-if**
  - *Reason:* Valuable anti-harassment tool, but only acceptable with strict safeguards: explicit multi-select preview dialog, default exclusion of followed accounts, and a hard cap of 20 accounts.
  - *Smallest viable version:* Overflow menu action in `ThreadFragment` opening a checklist dialog of candidate accounts (excluding self and followed accounts, capped at 20) with a sequential progress indicator.

---

## 3. Ranked Shortlist: What to Build Next

Ranked by **value-per-effort** (high impact, low friction, zero architectural risk):

### 1. G7: Alt text from image metadata (EXIF/XMP)
- **Size:** S (1 day)
- **Why #1:** Solves the primary cause of missing alt text on the fediverse—tedious mobile typing—by converting camera metadata into instant prefill. Uses Android's built-in framework `ExifInterface` with **zero new dependencies** and zero backend risk.

### 2. G11: Search operators as UI
- **Size:** S (1 day)
- **Why #2:** Mastodon 4.2+ has a remarkably capable search engine (`from:me`, `has:media`, `in:library`), but mobile users cannot remember operator syntax. A simple chip bar in `SearchQueryFragment` exposes this power immediately with zero API risk and graceful fallback on older instances.

### 3. G14: Alt text shown inline in the timeline
- **Size:** S (1 day)
- **Why #3:** Makes alt text a first-class reading experience rather than a hidden compliance flag behind a tap. Requires zero network calls, fits cleanly into `StatusDisplayItem`, and provides immediate everyday value to all users.

### 4. G8: Post as a shareable image
- **Size:** S–M (1.5–2 days)
- **Why #4:** Clean, standalone View-to-Bitmap rendering that solves the widespread habit of posting ugly, cropped screenshots to Signal or Instagram, while carrying post styling and domain branding.

### 5. G5: Wellbeing controls (End of feed & Grayscale media)
- **Size:** S (1–1.5 days)
- **Why #5:** Directly aligns with Mammut's ethos of respectful, intentional browsing. Extremely cheap `ColorMatrix` filter and auto-scroll clamp in `StatusListFragment`.

---

## 4. Traps Catalog: Features to Reject

These ideas look appealing in feature checklists but are architecturally fragile, technically dishonest, or maintenance sinkholes:

### 1. The Deceptive "Undo": G2 Undo-send window
- **The Illusion:** Giving users a 10–20 second window to "Undo" an already published post via `DELETE /api/v1/statuses/:id`.
- **The Reality:** In ActivityPub federation, a status is broadcast to remote servers immediately upon POST. Remote servers fire push notifications instantly to mentioned users. Deleting 10 seconds later cannot un-sound phone notifications or wipe data from relays or non-compliant servers. Calling this "Undo" provides false security. Conversely, holding posts locally without a persistent outbox means Android process death silently destroys unsent posts.

### 2. The Unsynced Index: G13 Notes manager
- **The Illusion:** A unified screen organizing all private notes written about other users.
- **The Reality:** Mastodon has no list API for notes. A client cannot ask the server "which accounts have notes?". The only way to build this screen is a private device-local database that knows nothing about notes written on web Mastodon or other apps, and vanishes upon app reinstall.

### 3. The Rate-Limit Exhaustion: G9 Year in Posts / personal stats
- **The Illusion:** A Spotify-Wrapped style annual recap of user post activity.
- **The Reality:** The app's `CacheController` only caches the home timeline and notifications; it holds no local history of the user's past posts. Generating stats requires paginating through thousands of statuses over REST (40 items/page), easily exceeding instance rate limits (300 req / 5 min) and consuming substantial battery and bandwidth.

### 4. The Protocol & Battery Quagmire: G4 Live streaming timeline
- **The Illusion:** Real-time timeline updates without pull-to-refresh.
- **The Reality:** Severe divergence between Mastodon (SSE) and GoToSocial (WebSocket only), heavy Android Doze/battery drain, reconnection edge cases, and reading disruption caused by live inserting posts into an active RecyclerView.

### 5. The Phantom Gap: G6 Per-account boost suppression
- **The Illusion:** A proposed new feature to suppress boosts on a per-account basis.
- **The Reality:** Already fully implemented and shipping in `ProfileFragment.java:808` and `AccountViewHolder.java:262`. Proposing it was a research oversight.
