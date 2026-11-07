# Mammut: competitive feature landscape

Research note for the Mammut feature plan. Date: 2026-11-06. Author: researcher (kanban t_dba05b56).
Scope: Android Mastodon/fediverse clients, plus Phanpy and Elk as web benchmarks. Read-only survey; the only
repo change is this file.

## Method

1. Read `docs/MAMMUT-PARITY.md` first and treated both its "Ported" table and its "Not ported, and why" table as
   ground truth. Nothing there is re-proposed as a discovery.
2. For every candidate feature, grepped the fork before writing it down: `api/requests/**`, `model/**`,
   `GlobalUserPreferences`, `api/session/AccountLocalPreferences`, `fragments/**`, `fragments/settings/**`,
   `ui/displayitems/**`, `res/layout*`, `AndroidManifest.xml`. If a request class existed but nothing called it,
   that is noted.
3. Competitor claims come from the sources at the bottom (changelogs, READMEs, app-store listings, docs). Where I
   could not read a source directly and am reasoning from the API shape or from the app's general behaviour, the
   confidence notes at the end flag it.
4. Sizes: **S** = roughly a day or less, one screen or one display item; **M** = 1.5-3 days, one subsystem
   touched; **L** = 5+ days or a new persistent subsystem.

## Already known, not counted as findings

Straight from `docs/MAMMUT-PARITY.md` "Not ported, and why" — repeating these as discoveries would be dishonest:

| Item | Why it is not in the findings list |
|---|---|
| Scheduled statuses screen | Blocked on compose-side scheduling, which does not exist. See also F22, which is the *device-side* variant other clients use to sidestep the server API. |
| Drafts / draft support in compose | The parity doc records that `ComposeFragment` has no scheduling **or draft** support. Tusky [S2], Pachli [S6], Fedilab [S5] and Moshidon [S19] all ship persistent drafts, so this is a real gap, but it is the same compose-persistence subsystem already flagged as blocked — not a new discovery. |
| `showPostsWithoutAlt` (alt-text feed filter) | Needs `AltTextFilter` + `WarningFilteredStatusDisplayItem`; known blocker. |
| `allowRemoteLoading` | Master-only remote-fetch subsystem; known blocker. |
| `autoRevealEqualSpoilers` | No per-status spoiler reveal state in this fork; known blocker. |
| `relocatePublishButton` / custom publish text | Publish action is a toolbar icon; needs a compose layout pass. |

Verified-present features that a survey reader might expect to see proposed were excluded after grepping:
quote posts (`GetStatusQuotes`, `QuoteApproval`), community filters with warn/hide (`FilterAction`), notification
grouping (`GetNotificationsV2` `grouped_types`, `user_and_x_more_*` plurals), poll "see results" without voting
(`poll_see_results`), audio attachment descriptions (`attachment_description_audio`), account notes
(`SetPrivateNote` is wired into `ProfileFragment`), exclusive lists (`CreateList.exclusive`), cross-account
favourite/boost on long-press (`FooterStatusDisplayItem.onBoostLongClick`), hidden/blocked account lists, and
per-instance content settings.

---

## Findings

### Table-stakes gaps (most clients have it; Mammut does not)

| # | Feature | User benefit | Ships in | API / degradation | Size | Class |
|---|---|---|---|---|---|---|
| F1 | **Per-timeline view filters** (show boosts / show replies / media-only, scoped per tab) | Lets you read a list or local timeline without boost noise, or a hashtag tab as a media feed, without touching the server. | Tusky "Timeline filtering" with `Show boosts` / `Show replies` and "Per-timeline preferences" [S2]; SubwayTooter "media TL" and filter set [S10] | Pure client-side filtering of returned statuses; no server support needed. Must be applied in the list adapter, not only at fetch time, so paging stays consistent. | M | Table stakes |
| F2 | **Filter action `blur`** | Mastodon 4.4 lets a keyword filter blur only the media instead of hiding the whole post; useful for sensitive-topic accounts. | Tusky 29.0 "Support for the blur filter action (Mastodon 4.4)" [S1] | `filter_action: "blur"` per the filters API, added in Mastodon 4.4 / API v5 [S16]. On older servers the value never appears; unknown values must be treated as `warn`, which is the documented fallback. Mammut currently models only `WARN` and `HIDE` (`model/FilterAction.java`). | S | Table stakes |
| F3 | **Hidden-domains manager** (list blocked domains, unblock from app) | Completes the "block domain" flow: today you can block a domain from a profile but cannot see or undo the list. | Tusky and Pachli both ship a "Hidden domains" screen (`title_domain_mutes`, `action_view_domain_mutes`) [S2][S6] | `GET /api/v1/domain_blocks` (returns `string[]`, paginated via `Link` header) and `DELETE /api/v1/domain_blocks` [S17]. Standard since 1.4.0, so no degradation needed beyond hiding the screen on error. | S | Table stakes |
| F4 | **Oldest-first reading order** | Read a timeline forward in time (oldest at top) so new posts append at the bottom instead of pushing the reading position. | Tusky "Reading order: Newest first / Oldest first" [S2]; Fedilab "reverse mode" for timelines [S3] | Client-side pagination/insertion order only. Mammut already has marker-based position restore; this changes where new pages are appended and where the scroll anchor sits. The hard part is doing it consistently in `BaseStatusListFragment` + `HomeTimelineFragment` gap handling. | M | Table stakes |
| F5 | **Settings search** | With ~40 Mammut settings spread across Behaviour/Display/Notifications, a search field is faster than remembering which screen owns a toggle. | Fedilab 3.39.0 "Reorganize settings with categories and search" [S3] | Local only: build an index over `ListItem` titles/summaries in `fragments/settings/*`, filter as the user types. No API. | S | Table stakes (power-client) |
| F6 | **Media aspect-ratio display options** | Choose between uniform crops, original ratios, or a compact grid, which matters for art/photography accounts. | Trunks "Media aspect ratio options" [S8] | Client-side layout change in `MediaGridStatusDisplayItem` / `PhotoLayoutHelper`; no API. Note `FixedAspectRatioImageView` already exists but is used for compose/preview, not as a user preference. | S | Table stakes |
| F7 | **Custom emoji size setting + emoji autocomplete in CW and poll fields** | Custom emoji are often unreadably small, and typing `:shortcode:` in a CW or poll option currently has no completion. | Fedilab 3.40.x "custom emoji independent size setting with tap to preview" and "emoji autocomplete in CW and poll fields" [S3] | Local only. `CustomEmojiPopupKeyboard` and `ComposeAutocompleteViewController` exist; this is a size multiplier plus widening the autocomplete trigger to those EditTexts. | S | Minor/table stakes |
| F8 | **Small timeline/compose polish bundle** (refresh all timelines at once; poll durations up to instance `max_expiration`; reverse sort for saved posts) | Removes three daily annoyances: pulling to refresh each tab, poll durations capped below what the server allows, and bookmarks always newest-first. | Fedilab 3.39.0 (refresh all), 3.39.0 (custom poll durations from `max_expiration`), 3.40.x (reverse sort for bookmarks/favourites) [S3] | All client-side. Poll durations currently hard-coded to 1h/6h/1d/3d/7d in `ComposePollViewController`; `Instance.Configuration.maxExpiration` is already parsed in `model/Instance.java`. | S (bundle) | Minor |
| F9 | **Tablet / foldable multi-column or two-pane layout** | On a tablet or unfolded Fold, use the width for list + detail instead of one stretched column. | SubwayTooter multi-column with tablet mode [S10]; Trunks "large screen and foldable support" [S8]; Kurage multi-column [S9] | Client-side. Mammut has only `res/layout` (no `layout-sw600dp`, no pane layouts, no `SlidingPaneLayout`); `MainActivity` is single-pane. This is the largest UI change in the list and touches navigation, not one screen. | L | Table stakes on large screens |

### Differentiators (nobody in the Android space has it, or only one client does)

| # | Feature | User benefit | Ships in | API / degradation | Size | Class |
|---|---|---|---|---|---|---|
| F10 | **Catch-up digest** | A bounded "what did my people post in the last N hours" view, grouped by author, with boosts separated and sortable by engagement — the antidote to infinite reverse-chron scrolling. | Phanpy, "Catch-up (beta)" added 2024-03-06 [S13], described on phanpy.social [S12] and in Fediverse Report ep. 59 [S14] | Client-side: fetch home timeline pages back to a time window, group by author, sort locally. No API beyond `GET /api/v1/timelines/home`. Phanpy added an "email-inspired" sort/filter UI on top. | M | Differentiator — no Android client ships it |
| F11 | **Boosts carousel** | Pulls reblogs out of the main timeline into a horizontal strip, so people who boost heavily stop drowning out original posts. | Phanpy "Boosts Carousel™️" [S11][S12][S13] | Client-side regrouping of `reblog != null` items. No API. Must stay consistent with F1's "hide boosts" setting (they are two answers to the same problem). | M | Differentiator — no Android client ships it |
| F12 | **Thread unroll / article reader** | Turns a long thread into one readable article instead of N cards with repeated headers and avatars. | Trunks "Unroll threads … single, easy-to-read article style view" [S8] | Client-side from `GET /api/v1/statuses/:id/context` (descendants), ordered by `in_reply_to_id` chain from the root. Degrades to the normal thread view when the chain is broken by deleted or unfetched posts. | M | Differentiator (one Android client) |
| F13 | **Nested replies / thread tree** | Shows who replied to whom with indentation (or a visual map), instead of a flat list sorted by time. | Phanpy "Nested comments thread" [S11]; Fedilab "Conversation thread maps" [S5] | Client-side from the same context endpoint; no API. Risk: deep trees on mobile width; needs a depth cap and a "continue thread" affordance. | L | Differentiator |
| F14 | **Reply sorting** (chronological / most-liked; "controversial" is not derivable) | In a 100-reply thread, lets you read the good replies first instead of the earliest ones. | Trunks "Comment sorting: best, chronological, or controversial" [S8] | Client-side using `favourites_count` / `reblogs_count` on descendants. Note: "best" and "controversial" as Trunks describes them are product-defined, not Mastodon data; only chronological and by-likes are honest here. | S | Differentiator (with caveat) |
| F15 | **Unified multi-account timeline** | One feed merging several accounts' home timelines, colour-coded per account, for people who run a personal and a project account. | Kurage "unified timeline" with per-account colours and cross-account actions [S9] | Client-side merge of N `GET /api/v1/timelines/home` cursors; dedupe by status id; interleave by `created_at`. Degrades to per-account tabs if any account fails. Needs a stable sort across pages, hence L. | L | Differentiator |
| F16 | **Split timeline / resume pane** | Pins the newest posts at the top while you continue reading backwards from where you left off — both halves visible at once. | Kurage "Split timeline" button [S9] | Client-side; two independently paginated lists over one timeline, one anchored at the saved marker. No API. | M | Differentiator |
| F17 | **App lock (PIN / biometric)** | Stops someone who picks up an unlocked phone from reading DMs and notifications. | Kurage "app lock with PIN / biometric authentication" [S9] | Local: `BiometricPrompt` + a stored PIN hash; gate `MainActivity.onCreate` and the recents thumbnail. No API. | S | Differentiator |
| F18 | **Home-only mute** | Mute an account from the home feed but keep it visible in lists — the standard way to build curated list timelines without losing the person entirely. | Fedilab "users can be muted just on home timeline while still appearing on lists" (and lists can auto-include home-muted users) [S5] | Client-side mute set applied only to the home timeline; `mutes` API stays the server-side mute. Must be per-account local state. | M | Differentiator |
| F19 | **Mute by posting client/app** | Hides everything posted by a given app (crossposters, bots, a specific client's formatting), without muting the humans. | SubwayTooter "app mute" [S10] | Client-side match on `status.application.name` (present on statuses). Degrades silently when the server omits `application`. | S | Differentiator |
| F20 | **Font family choice / accessible fonts** | Dyslexia-friendly and larger-font users can pick a face, not just a text-size multiplier. | Pachli "a variety of accessible fonts in different sizes" [S6][S7] | Local: a small font picker that sets `Typeface` on text views. Mammut hard-codes `sans-serif` in layouts and only has `enhanceTextSize`. | S | Differentiator (accessibility) |
| F21 | **Auto-CW for long posts** | Folds very long posts behind a content warning automatically, so one essay does not eat three screens of timeline. | SubwayTooter "Auto CW that folds very long texts automatically" [S10] | Client-side length heuristic in `StatusDisplayItem.buildItems`; must not fight server-provided spoilers or filters. | S | Differentiator |
| F22 | **Device-side scheduled posting** | Schedules a post locally (WorkManager + alarm) and publishes at the right time even on servers whose scheduled-status API is missing or buggy — the dependable option on GoToSocial, which has had `scheduled_at` timezone bugs reported against real clients. | Fedilab "Schedule messages from the device" [S3][S5]; the failure mode on servers without working `scheduled_statuses` is documented in a widely shared Tusky + GoToSocial bug report [S22] | Standard `POST /api/v1/statuses` with `scheduled_at` when the server supports it (Mastodon 2.7+ [S18]); fall back to a local queue + `WorkManager` when `GET /api/v1/scheduled_statuses` 404s or the server mis-parses the date. Needs a small local status store, so L, and it overlaps the known compose blocker. | L | Differentiator |
| F23 | **Translate profile bio** | One tap to read a bio in your language, matching the translate button posts already have. | Fedilab 3.45.x "Translate button for the profile bio" [S4] | **Not** standard: `/api/v1/statuses/:id/translate` only translates statuses. Fedilab does this with its own translation stack, which on F-Droid includes an Apertium engine and an external translator app integration [S3], on top of its built-in translation feature [S5]. Degrade by hiding the button unless a translation provider is configured. | M | Differentiator (cost: a provider) |
| F24 | **Anonymous instance browsing** ("pseudo account") | Read a server's public timeline before making an account — useful for evaluating an instance. | SubwayTooter "pseudo account that can read instance timeline without login" [S10] | `GET /api/v1/timelines/public` without a token. Mastodon 4.5 added per-server granular timeline access: unauthenticated requests can now return `401`, and `Instance.timelines_access` says whether guest access is allowed [S21][S23]. Degrade: show only servers whose instance metadata permits it, and an inline "sign in to read this timeline" state otherwise. | M | Differentiator |

---

## Notes on the findings that need care

**F1 (view filters) is the highest-value small item.** It is the one gap that affects every session: Tusky's
`Show boosts` / `Show replies` switches are per-timeline, and Mammut has nothing equivalent. Implementation has to
live in the list layer (`BaseStatusListFragment`, and the adapter that builds items), not in the request, or the
paging cursors drift. It also composes with F11: hide-boosts and boosts-carousel are mutually exclusive answers.

**F2 (blur) is a real API-version gap, not a cosmetic one.** `model/FilterAction.java` only knows `warn` and
`hide`, and `EditFilterFragment` writes `WARN` or `HIDE` from a single "show with content warning" switch. The
documented rule is that unknown `filter_action` values are treated as `warn` [S16], so adding `BLUR` is safe
against old servers and only needs a three-way control and a blur branch in `StatusDisplayItem` (where
`applyFilterHighlights` and the filter spoiler are already built).

**F3 (hidden domains) is the cheapest completeness win.** `SetDomainBlocked` and `GetDomainBlockPreview` exist;
`GetDomainBlocks` does not appear anywhere in `api/requests/`. The endpoint predates Mastodon 2.0 [S17], so this
works on every server, including GoToSocial and Akkoma.

**F10/F11 share plumbing.** Catch-up needs a time-windowed fetch and local grouping; the carousel needs reblog
extraction and a horizontal container. Both are rendering over data the home timeline already returns. Building
F11 first is the cheaper proof that the grouping code works.

**F12/F13/F14 all read `GET /api/v1/statuses/:id/context`.** The current `ThreadFragment` already walks that
response; the new work is presentation (unroll, indent, sort) and the edge cases: deleted parents, replies the
server did not return, and very deep chains. Do not attempt all three at once — pick unroll (F12) as the first,
because it degrades to the existing view most gracefully.

**F15/F16 are the most architecturally invasive differentiators.** A unified timeline means N paginators with
independent cursors and a merge sort that stays stable while new posts arrive; the split timeline means two
adapters over one data source. Both are plausible, but neither is a first-wave item.

**F18 (home-only mute) is not the same as the Mastodon mute.** Mastodon's mute can already suppress notifications
only, but it also removes the account from lists territory the user may want. Fedilab's trick is a purely local
set consulted only by the home timeline [S5]. It is a per-account local preference, so it belongs in
`AccountLocalPreferences`, not `GlobalUserPreferences`.

**F22 is the one to scope carefully.** Mastodon's own scheduling is server-side (`scheduled_at`, then
`/api/v1/scheduled_statuses`) [S18]. GoToSocial has an implementation but has had date-parsing failures reported
with real clients, including Fedilab [S22]; a client that assumes the server handled it will either post
immediately or double-post. A device-side queue removes that class of bug entirely, at the cost of a local status
store and WorkManager constraints — the same store drafts would need.

**F23 needs a translation provider.** Mammut already has `TranslateStatus` for posts, which is server-side. A bio
is not a status, so this feature is only honest if the app either (a) calls a configurable external engine, or
(b) hides the action. Do not ship a button that only works on instances running a particular fork.

**F24 (anonymous browsing) has a moving floor.** Mastodon 4.5 made unauthenticated timeline access a per-server
setting with a documented `401` [S23]. The feature is still useful, but it must read `timelines_access` and fail
into a sign-in prompt rather than an empty screen.

---

## Top 10 by value-to-effort

Ranked on user value per engine-day, not on novelty. Effort assumes one engineer familiar with the fork.

| Rank | Finding | Effort | Why this rank |
|---|---|---|---|
| 1 | F1 Per-timeline view filters | M (~1.5-2 d) | Biggest daily-quality-of-life gap; no API risk; one place (list layer) to get right. |
| 2 | F3 Hidden domains manager | S (~0.5 d) | Completes an existing flow with a decade-old endpoint; near-zero risk. |
| 3 | F2 Filter action `blur` | S (~0.5 d) | Closes a Mastodon 4.4 parity gap with a documented, safe fallback. |
| 4 | F10 Catch-up digest | M (~2-3 d) | The single strongest differentiator available: no Android client has it, and it is built entirely on the home timeline. |
| 5 | F11 Boosts carousel | M (~2 d) | Same plumbing as F10, immediately demo-able, and it pairs with F1's hide-boosts switch. |
| 6 | F17 App lock | S (~1 d) | Cheap, visible, and genuinely missing from the Android mainstream (Kurage only). |
| 7 | F5 Settings search | S (~1 d) | Straightforward local index; pays off every time a setting is hunted for. |
| 8 | F14 Reply sorting | S (~1 d) | Small surface, clear benefit in long threads; only chronological/by-likes are defensible. |
| 9 | F4 Oldest-first reading order | M (~2 d) | Table stakes for a vocal subset; the risk is pagination, so it needs its own testing pass. |
| 10 | F12 Thread unroll | M (~2-3 d) | Strong reader-mode differentiator; naturally degrades to the existing thread view. |

Next tier, deliberately not in the top 10: F13 nested threads (L, high risk of a mobile-width mess), F15 unified
timeline (L, cursor complexity), F22 device-side scheduling (L, shares the blocked compose subsystem), F9 tablet
layout (L, navigation rewrite), F18 home-only mute (M, niche but loyal audience), F20/F21/F19/F6/F7 (small polish).

---

## Explicitly not worth copying

| Feature | Where it ships | Why not |
|---|---|---|
| **Markdown rendering and markdown compose** | Elk renders markdown and code blocks [S15]; Trunks advertises markdown support [S8] | Mastodon has no markdown field: the API returns HTML that the client already renders. Adding a second markdown pass means double-rendering and false positives on ordinary `*asterisks*` and `_underscores_`, and it would fight `HtmlParser`/`StatusDisplayItem`. Elk itself documents that other apps see the raw markdown [S15]. |
| **Dedicated DM / chat inbox** | Tusky ships a private-messages screen built on `/api/v1/conversations` (`help_empty_conversations`, `conversation_1_recipients`) [S20][S2] | The endpoint is legacy and Mammut's official-app base already surfaces DMs in notifications; a chat UI invites a typing/read-receipt feature set the protocol does not have. High product cost on a shrinking foundation. |
| **GitHub link cards** | Elk's rich GitHub previews [S15] | Service-specific enrichment (extra API, extra failure modes) for one host, on top of a card system that already works for every other link. |
| **Multiple launcher icons** | Fedilab [S3] | Cosmetic, four drawables and a manifest alias per icon, zero daily value. |
| **Scheduled boosts** | Fedilab "schedule boosts" [S3][S5] | No standard API for scheduling a boost; it is a client-side timer that can fire after the account has logged out or the token has expired. Fragile, and an error here is publicly visible. |
| **Regex / word filters as a separate engine** | SubwayTooter "word mute, regex filter" [S10] | Mastodon 4.x filters are server-side, keyword-based and already implemented in Mammut; a second client-side regex engine adds catastrophic-backtracking risk and two sources of truth. |
| **Alternative-frontend link rewriting (Nitter/Invidious/…)** | Fedilab [S3][S5] | The target services are unstable (Nitter is effectively dead), so the maintenance cost is a treadmill for a small privacy gain. `removeTrackingParams` already covers the durable half. |
| **Full offline-first rewrite** | Pachli caches the timeline for offline reading [S6][S7] | Mammut already has `CacheController` with a SQLite status/notification cache. The delta between that and "read your whole timeline offline" is a large persistence and reconciliation project, not a feature. |
| **Export all posts** | Fedilab "export toots" [S3] | Paginating an entire account archive, formatting it and handling media is a lot of code for a rarely used escape hatch; the server already offers an archive export. |
| **Multi-hashtag single timeline** | Phanpy "#️⃣ Multi-hashtag timeline" [S11] | Mammut already has per-hashtag tabs and custom instance timelines; a combined multi-tag feed adds a new timeline type and filter semantics for a marginal gain. |

---

## Sources

- **[S1]** Tusky CHANGELOG (v29 blur filter action, poll "show results", v28 notification rendering, v27 notification policies and audio descriptions) — <https://github.com/tuskyapp/Tusky/blob/develop/CHANGELOG.md>
- **[S2]** Tusky `strings.xml` (per-timeline `Show boosts` / `Show replies`, reading order oldest-first, `Hidden domains`) — <https://github.com/tuskyapp/Tusky/blob/develop/app/src/main/res/values/strings.xml>
- **[S3]** Fedilab on F-Droid, feature list and 3.39/3.40 release notes (scheduled messages from the device, settings search, emoji size and autocomplete, custom poll durations, reverse sort, refresh all timelines) — <https://f-droid.org/packages/fr.gouv.etalab.mastodon/>
- **[S4]** Fedilab on Google Play, 3.45.x release notes (translate profile bio, poll results without voting) — <https://play.google.com/store/apps/details?id=app.fedilab.android>
- **[S5]** Fedi.Tips, "Fedilab for Android" (drafts, thread maps, home-only mute, device scheduling, link rewriting) — <https://fedi.tips/fedilab-for-android/>
- **[S6]** Pachli README (drafts, schedule, translate, offline cache) and `strings.xml` ("Hidden domains") — <https://github.com/pachli/pachli-android>
- **[S7]** Pachli 1.0 announcement (accessible fonts, offline timeline, exclusive lists, unlimited tabs) — <https://pachli.app/pachli/2023/09/06/hello-pachli.html>
- **[S8]** Trunks for Mastodon, Google Play listing (foldable support, threaded replies, unroll threads, comment sorting, media aspect ratios, markdown, quoting) — <https://play.google.com/store/apps/details?id=com.decad3nce.trunks>
- **[S9]** Kurage for Mastodon, Google Play listing (unified multi-account timeline, split timeline, multi-column, app lock) — <https://play.google.com/store/apps/details?id=jp.demo2.kurage>
- **[S10]** SubwayTooter README (multi-column/tablet, pseudo account, cross-account actions, app mute, media TL, auto CW) — <https://github.com/tateisu/SubwayTooter>
- **[S11]** Phanpy README (grouped notifications, nested comments, draft recovery, boosts carousel, multi-hashtag timeline) — <https://github.com/cheeaun/phanpy>
- **[S12]** Phanpy site (Boosts Carousel, Catch-up) — <https://phanpy.social>
- **[S13]** Phanpy CHANGELOG ("Catch-up (beta)" 2024-03-06; "Boosts Carousel") — <https://github.com/cheeaun/phanpy/blob/main/CHANGELOG.md>
- **[S14]** Fediverse Report ep. 59, catch-up feature description — <https://fediversereport.com/last-week-in-fediverse-ep-59>
- **[S15]** Elk docs, features (markdown, code blocks, connected timeline posts, grouped notifications) — <https://docs.elk.zone/guide/features>
- **[S16]** Mastodon filters API, `filter_action` `warn`/`hide`/`blur`, 4.4 / API v5 — <https://docs.joinmastodon.org/methods/filters>
- **[S17]** Mastodon domain blocks API — <https://docs.joinmastodon.org/methods/domain_blocks>
- **[S18]** Mastodon scheduled statuses API — <https://docs.joinmastodon.org/methods/scheduled_statuses>
- **[S19]** Moshidon README (drafts and scheduled posts, cross-account actions) — <https://github.com/LucasGGamerM/moshidon>
- **[S20]** Mastodon conversations API — <https://docs.joinmastodon.org/methods/conversations>
- **[S21]** Mastodon `Instance` entity, `timelines_access` (per-server guest timeline policy) — <https://docs.joinmastodon.org/entities/Instance/#timelines_access>
- **[S22]** GoToSocial issue #4409, `scheduled_at` timezone parse failures with real clients (Tuba, Fedilab) — <https://codeberg.org/superseriousbusiness/gotosocial/issues/4409>
- **[S23]** Mastodon 4.5 for Developers, timeline access: unauthenticated timeline requests may now return `401`; `Instance.timelines_access` — <https://blog.joinmastodon.org/2025/10/mastodon-4-5-for-devs>

### Confidence notes

- Claims tagged with a source above were read directly (changelogs, READMEs, store listings, API docs).
- "Ships in" for Trunks and Kurage comes from their Play listings; I did not run either app, so exact current
  versions and whether every listed feature still works are **unverified**.
- Effort sizes are estimates from the fork's structure (`BaseStatusListFragment`, `StatusDisplayItem`,
  `fragments/settings`), not measured.
- The GoToSocial scheduling failure is documented for Fedilab and Tuba in GTS issue #4409 [S22]; the Tusky
  duplicate-post incident circulates as a user report and is **anecdotal**, so it is used only to show the failure
  mode, not as proof of current behaviour.
- F14's "best"/"controversial" sorting exists in Trunks [S8] but is not derivable from Mastodon data; only
  chronological and engagement-count sorting are proposed.
