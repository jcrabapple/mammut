# Mammut feature plan

## Purpose and scope

This plan turns the 24 findings in [the competitive survey](research/mastodon-feature-landscape.md) into implementation order, ownership boundaries, and release checks.
It assumes the current `rewrite`-based architecture and the ported/blocked inventory in [MAMMUT-PARITY.md](MAMMUT-PARITY.md).
Effort means one engineer's implementation and verification time, not calendar time.

Paths below are relative to `mastodon/src/main/java/org/joinmastodon/android/` unless stated otherwise.
“New” request classes are proposals; all other named classes and endpoints were checked in this tree.

Do not reopen the already ported features, the launcher icon work, or the known compose/remote-loading/alt-text blockers.
In particular, this plan does not smuggle scheduled statuses or drafts back in through a differently named scheduling feature.

## Architecture rules for every feature

1. **Keep display items fragment-free.**
   `StatusDisplayItem` reaches its screen only through `StatusDisplayItem.Callbacks`, implemented by `BaseStatusListFragment`.
   New row actions must add a default callback method and route it through that interface.
   Do not add a `parentFragment` field, cast the callbacks object to a fragment, or construct navigation inside a display item.
2. **Make preferences real.**
   A persisted option needs a field, a backward-compatible default, load/save behavior, a `CheckableListItem`/`ListItem` in the appropriate settings screen, and a consumer.
   `TimelineDefinition` is already persisted inside `AccountLocalPreferences.timelines`, so its new fields reuse that save path rather than adding duplicate top-level preferences.
3. **Keep pagination in raw data.**
   View filtering must not discard the raw statuses that own `maxID`/`minID` cursors.
   Rebuild visible display items from raw data when a setting changes.
4. **Parse forward-compatible values safely.**
   Unknown enum values and absent optional fields must degrade to a safe presentation, never crash the timeline.
5. **Use the authenticated account for ordinary API calls.**
   Anonymous browsing is the exception and must use the existing `execNoAuth(domain)` pattern.

## Recommended first wave

Build these in order.
F3 and F2 are independent and may overlap if separate worktrees are used; F1 should land before F10 so their `HomeTabFragment` changes are reviewed serially.

### 1. F3: hidden-domains manager: 1 engine-day

**Why first.**
This closes an irreversible-looking user action with a standard, old API and almost no architectural risk.
Blocking a domain already exists; only discovery, pagination, and reversal are missing.
It is the shortest path to visible user value.

**Implementation.**
Add `DomainBlocksFragment` under `fragments/settings/`, backed by `BaseSettingsFragment<String>`.
Add one `ListItem` to `SettingsAccountFragment` beside the other account-safety controls.
The new `api/requests/accounts/GetDomainBlocks` extends `HeaderPaginationRequest<String>` and calls `GET /api/v1/domain_blocks`; use the existing `nextMaxID`/`nextSinceID` pagination path in `BaseSettingsFragment`.
Each domain row opens a confirmation and calls the existing `SetDomainBlocked(domain, false)`, which issues `DELETE /api/v1/domain_blocks`.
Remove the row only after success and expose retry on failure.

**Touch points.**
`SettingsAccountFragment`; new `DomainBlocksFragment`; new `GetDomainBlocks`; existing `HeaderPaginationRequest`, `SetDomainBlocked`, and `BaseSettingsFragment`.
Reuse `ic_domain_disabled_24px` and the generic settings/list layouts.

**Resources and API behavior.**
Add settings title, explanation, empty state, unblock confirmation, and success/error strings.
No new layout or drawable is required.
`domain_blocks` has existed since Mastodon 1.4, so no version gate is needed.
A server that rejects the endpoint gets the normal inline loading error and retry; the local row remains until deletion succeeds.

### 2. F2 blur filter action: 1 engine-day

**Why second.**
The server already returns `filtered` and `FilterResult`; the missing work is one action value and its rendering semantics.
It is small, but it must be correct for mixed filters and unknown future values rather than treated as a simple enum addition.

**Implementation.**
Add `BLUR("blur")` to `FilterAction` and make unknown wire values decode as `null`.
In `EditFilterFragment`, replace the independent warn/hide choice with one mutually exclusive Warn/Blur/Hide selector; `CreateFilter` and `UpdateFilter` already serialize the enum and need no new endpoint.
Offer Blur for creation only when cached `Instance.getApiVersion()` reports Mastodon API v5 or newer.
Regardless of creation capability, render a blur result whenever a server sends one.

Centralize effective-action selection so **Hide > Warn > Blur**, with an unknown action treated as Warn.
`AccountSession.filterStatuses` continues removing Hide results.
`StatusDisplayItem` must create the whole-post spoiler only for Warn or unknown results, not Blur.
`MediaGridStatusDisplayItem` covers media for an active Blur result while leaving text readable.
Add a transient filter-media reveal flag on `Status`, separate from `sensitiveRevealed`, so revealing filtered media does not silently reveal independently sensitive media.

**Touch points.**
`model/FilterAction`, `model/FilterResult`, `model/Status`, `fragments/settings/EditFilterFragment`, `ui/displayitems/StatusDisplayItem`, `ui/displayitems/MediaGridStatusDisplayItem`, and optionally a small capability helper in `model/Instance`.

**Resources and API behavior.**
Add the three action labels, Blur explanation, media-cover title, and reveal string; reuse existing spoiler/media layouts and icons.
On pre-4.4 servers, Blur is absent from the editor and Warn/Hide continue unchanged.
A null/unknown action behaves as Warn.
A Blur result with no media leaves the post readable rather than hiding the whole post.

### 3. F1: per-tab timeline view filters: 2–3 engine-days

**Why third.**
Boosts, replies, and media-only are everyday controls and no Mastodon API extension is needed.
The risk is not the predicates; it is preserving cursors, gap loading, live insertion, and tab independence.
That puts it behind the two one-day items.

**Implementation.**
Add `showBoosts=true`, `showReplies=true`, and `mediaOnly=false` to `TimelineDefinition`; include them in `copy()` but not identity/equality.
Existing JSON gains safe defaults, and `AccountLocalPreferences` already loads and saves the timeline list.
Add a **Timeline view** action to `HomeTabFragment`'s overflow for the current tab and open a new `TimelineViewSettingsFragment extends BaseSettingsFragment<Void>`.
Its three `CheckableListItem`s write to that tab's definition, save, post the existing `StatusDisplaySettingsChangedEvent`, and rebuild the visible list.

Add a protected predicate in `StatusListFragment` and apply it in every `buildDisplayItems` path: initial load, append, prepend/live insertion, refresh, and full rebuild.
Keep all raw statuses in `data`.
Definitions are: hide boosts removes `status.reblog != null`; hide replies removes `status.inReplyToAccountId != null`; media-only requires `status.getContentStatus().mediaAttachments` to be nonempty.
Predicates combine with AND.
Scope the first version to tabs created by `HomeTabFragment`, not threads, profiles, bookmarks, search, or the legacy Home timeline mode dropdown.

When a raw page produces no visible rows, continue loading older pages until a visible row arrives or the server has no more data.
Only then show the empty state.
Never derive the next cursor from the filtered display list.

**Touch points.**
`TimelineDefinition`; `HomeTabFragment` and `res/menu/home_overflow.xml`; new `TimelineViewSettingsFragment`; `StatusListFragment`; `BaseStatusListFragment` only if the loading hook cannot be contained in the subclass.
Reuse `AccountLocalPreferences.timelines`, `StatusDisplaySettingsChangedEvent`, and the existing filter/settings icons.

**Resources and API behavior.**
Add menu/screen titles, three option labels, a per-tab explanation, and filtered-empty text.
No new drawable or endpoint is required.
This works on any server that can return a timeline; replies, boosts, and attachment metadata are local predicates, not server flags.

### 4. F10: Catch-up digest: 3 engine-days

**Why fourth.**
This is the clearest differentiator: a useful reading mode that no surveyed Android client ships.
A deliberately bounded, on-demand version avoids a database, background worker, notification channel, and ranking service.
The cap and grouping behavior need careful testing, so it follows the smaller reading controls.

**Implementation.**
Add **Catch up** to `HomeTabFragment`'s overflow and a `CatchUpFragment` using the standard recycler/settings resources.
Fetch the existing `GetHomeTimeline` sequentially with `maxID`, newest first, using up to five pages of 40 statuses and a 24-hour cutoff.
Stop at the cutoff, cap, or end of pagination.
Base inclusion on the wrapper's `createdAt`, so a boost inside the window is not lost because its original post is old.
Show a “most recent 200 statuses” notice when the cap truncates the window; do not claim a complete digest.

Group original posts by author and put boosts in a separate section, also grouped by booster.
Default sections to most-recent activity; offer a menu sort using the local sum of reply, reblog, and favourite counts as a documented heuristic.
Reuse `SectionHeaderStatusDisplayItem` and `StatusDisplayItem.buildItems` with headers suppressed inside each group.
Navigation and post actions stay on existing `StatusDisplayItem.Callbacks`; no display item receives a fragment reference.
Cancellation, rotation, offline errors, an empty window, and a partially filled cap all need explicit states.

**Touch points.**
`HomeTabFragment`, `res/menu/home_overflow.xml`; new `CatchUpFragment` and a small catch-up grouping/sorting class; existing `GetHomeTimeline`, `SectionHeaderStatusDisplayItem`, `StatusDisplayItem`, and `BaseStatusListFragment` callbacks.

**Resources and API behavior.**
Add screen/section/sort/cap/empty/error strings and a small sort menu resource; reuse the base list layout and current icons.
No new request class, dependency, background permission, or persistent preference is required.
Every instance with a home timeline supports it; unavailable timeline access produces the ordinary retryable error.

## Riskiest assumption

The Catch-up proposal assumes that a bounded 24-hour/200-status slice is useful on real home timelines without a persistent read-position cache.
Busy accounts may make the digest partial even with the truncation notice.
Validate that product assumption before expanding the cap, adding background fetching, or investing in richer ranking.

## Second wave and backlog

“Build” means credible after the first wave, not approval to start all rows in parallel.
“Defer” means the feature is reasonable but its prerequisites or cost lose to higher-value work.

### Reading and timeline controls

| Finding | Disposition | Effort and boundary |
|---|---|---|
| F11 Boosts carousel | **Build after F1** | 2 days. Reuse raw timeline data and existing display items; make carousel and Hide boosts mutually exclusive per tab. Route row actions through callbacks. |
| F18 Home-only mute | **Build after F1** | 2 days. Store account IDs per local account, add manager UI, and apply only to Home raw-data projection; keep server mutes distinct. |
| F19 Mute posts by app | **Build after F1** | 1–2 days. Store `Status.application.name`, add a manager row, and leave statuses without application metadata unmatched. |
| F4 Oldest-first jump | **Defer** | 2–3 days. Needs server-side reverse paging or a bounded cursor walk and conflicts with Home gap markers and prepend logic. |
| F16 Split new/old timeline | **Reject** | Two independently paging projections plus marker restoration cost more than improving F4 or the existing unread marker. |
| F24 Anonymous instance browsing | **Defer** | 2–3 days. Requires a session-less navigation state, 401 handling, and sign-in handoff; custom local timelines already cover some reading value. |
| F15 Unified multi-account timeline | **Defer** | 5+ days. Requires N cursor states, account-scoped actions, partial failures, and dedupe by canonical status URI rather than instance-local ID. |

### Settings and small polish

F7 and F8 are split into independently shippable tickets so a compose change cannot hold up a display or saved-post improvement.

| Finding | Disposition | Effort and boundary |
|---|---|---|
| F5 Settings search | **Build after F3** | 2–3 days. Create a declarative settings catalog with destination classes; do not instantiate every settings fragment to discover rows. |
| F6 Media aspect ratio | **Build** | 1 day. Add global preference, Display settings consumer, and `MediaGridStatusDisplayItem`/`PhotoLayoutHelper` support. Sequence after F2. |
| F7a Custom emoji size | **Build** | 1 day. Global display preference plus shared HTML/emoji rendering consumer; avoid composing-only behavior. |
| F7b Emoji autocomplete | **Build separately** | 1 day. Extend `ComposeAutocompleteViewController`; coordinate with all compose work. |
| F8a Poll duration limits | **Build** | 0.5–1 day. Read existing instance poll configuration in `ComposePollViewController`; preserve current fallback when absent. |
| F8b Reverse saved posts | **Build** | 1 day. Add per-screen sort state to bookmarked/favorited fragments without changing server pagination semantics. |
| F8c “Refresh all” | **Defer** | 1 day alone, but fan-out and rate-limit behavior need a design; do not bundle unrelated F8 items into one ticket. |
| F20 Bundled fonts | **Defer** | 2–3 days plus asset/license review. Hardcoded sizes and global typeface invalidation make this broader than a font picker. |

### Conversations

| Finding | Disposition | Effort and boundary |
|---|---|---|
| F14 Newest/oldest replies | **Build** | 1 day. Sort the already returned `GetStatusContext.descendants` in `ThreadFragment`; keep ancestors unchanged. |
| F12 Thread unroll | **Build after F14** | 2–3 days. Add a focused reader over existing context data with depth/cycle protection and accessible nesting. |
| F13 Nested replies | **Defer** | 3–5 days. Needs depth-aware models and indentation limits; evaluate only after unroll proves demand. |

### Security and multi-window

| Finding | Disposition | Effort and boundary |
|---|---|---|
| F17 App lock | **Defer** | 2–3 days. First design `FragmentActivity` inheritance, Android Auto/external-share behavior, recents privacy, biometric fallback, and lock recovery on minSdk 23. |
| F9 Tablet two-pane layout | **Defer** | 5+ days. Requires navigation and dual-fragment lifecycle ownership, not just `sw600dp` layouts. Do alongside a deliberate navigation revision. |
| F22 Device-side scheduling | **Reject** | 5+ days plus draft storage and background reliability; it also reintroduces the known scheduling/drafts blocker under another name. |
| F21 Auto-CW long posts | **Reject** | The ported `collapseLongPosts` already supplies the reading control. A second spoiler-like folding state adds conflicting UI. |
| F23 Bio translation | **Reject server-side design; defer provider decision** | `TranslateStatus` is not a general text endpoint. Reconsider only with a chosen external/on-device provider, privacy terms, and failure UX. |

## Explicit rejections and attractive traps

These are not postponed first-wave candidates.
They need a new product or architecture decision before any implementation ticket.

| Proposal | Engineering reason not to build as presented |
|---|---|
| Markdown rendering/compose | Mastodon stores HTML, not a Markdown source field. Rendering stored HTML again and treating `*` as markup corrupts existing posts; authoring needs a server-specific format contract. |
| Dedicated DM/chat inbox | DMs are private-visibility statuses and already arrive as notifications. A chat surface implies conversations, delivery state, and realtime behavior the API does not provide here. |
| F22 scheduled posts/boosts | Reliable alarms, draft persistence, retries, timezone changes, and process death are a subsystem. Existing scheduled-status APIs do not remove the compose/draft blocker. |
| Regex filters | Untrusted expressions can stall the UI thread; safe execution needs timeouts/isolation and ReDoS-resistant semantics. Ordinary filters already cover the main use case. |
| Full offline rewrite | Timeline cursors, media, writes, conflicts, and account isolation would require a new sync/storage architecture. Catch-up must remain bounded and online. |
| Alternate-frontend link rewriting | No Mastodon API is involved; it creates a content policy, URL trust, and maintenance problem inside rendering. |
| Export all posts | Large media exports need resumable jobs, storage permissions, quotas, and cancellation; it is a backup product, not a small client option. |
| Multiple launcher icons | Requires manifest activity-alias and relaunch handling, and icon design is already in flight. Do not attach feature work to it. |
| GitHub link preview cards | A second HTML fetch/parser adds network, privacy, cache, and lifecycle work for one site's metadata. |
| Multi-hashtag timeline | Federated hashtag APIs do not provide arbitrary OR groups; list-backed approximations are not the advertised feature. |
| F21 auto-CW long posts | Duplicates `collapseLongPosts` and introduces a second reveal model. |
| F23 server-side bio translation | Uses an endpoint outside its contract and makes a provider/privacy decision implicitly. |

## Sequencing hazards

| Collision area | Features | Required order or isolation |
|---|---|---|
| `StatusListFragment`, `BaseStatusListFragment` | F1, F4, F11, F18, F19 | Land F1 first. Build F11/F18/F19 against its projection hook; keep F4 in a separate later change because it alters cursor direction and gaps. |
| `StatusDisplayItem`, `MediaGridStatusDisplayItem` | F2, F6, F11 | F2 establishes filter/reveal state first; F6 follows. Rebase F11 carousel work after both if it shares row construction. |
| `HomeTabFragment`, `home_overflow.xml` | F1, F8c, F10, F11 | F1 → F10, then carousel/refresh-all. Menu-only edits are small but should not race behavior changes in the same class. |
| `TimelineDefinition`, `AccountLocalPreferences` | F1, F10, F18, F19 | F1 owns timeline schema changes. Keep first Catch-up version preference-free; add later local sets with defaults and copy/save tests. |
| `SettingsAccountFragment`, settings catalog | F3, F5, F18/F19 managers | F3 first, then define the catalog, then register mute managers. Avoid search indexing stale or duplicate destinations. |
| `EditFilterFragment`, filter models | F2, future regex/alt-text filters | F2 is the sole action-semantics change. Regex is rejected; alt-text remains blocked and must not be patched into this work. |
| `ThreadFragment`, context consumption | F12, F13, F14 | F14 → F12 → F13, with each rebased on the previous context representation. |
| Compose classes | F7b, F8a, blocked publish/scheduling work | Serialize all compose changes. Poll limits and emoji autocomplete are separate tickets; do not combine them with publish-button or scheduling redesigns. |
| `GlobalUserPreferences`, Display settings | F6, F7a, F20 | One preference/schema change at a time; verify each setting has a rendering consumer before starting the next. |
| `MainActivity`, manifest, session/navigation | F9, F17, F24 | These are architecture-sized efforts and should not run concurrently without an explicit navigation/session design. |

## Definition of done

### Shared release bar for every shipped feature

- The implementation and resource references compile, and the implementer runs `./gradlew :mastodon:assembleDebug` successfully.
  This planning task intentionally does not run that build.
- Every new preference has a default, persistence across process recreation, a settings/control surface, and an exercised consumer.
  No dead toggles or write-only fields.
- Existing accounts and JSON from before the feature retain current behavior.
  New enum values and missing server fields fail safely.
- Pagination, refresh, rotation, cancellation, empty content, offline/error retry, and account switching are checked where applicable.
- No display item stores or casts to a fragment.
  New row navigation/actions use default methods on `StatusDisplayItem.Callbacks` and `BaseStatusListFragment`.
- No feature claims server support it has not observed.
  Capability-gated controls disappear or fall back without breaking existing behavior.

### Feature-specific acceptance

| Feature | Shipped means |
|---|---|
| F3 Hidden domains | Multiple Link-paginated pages load; unblock removes exactly one domain only after DELETE succeeds; failure keeps the row and offers retry; empty and endpoint-error states work; account settings opens the correct account's list; debug build passes. |
| F2 Blur filters | JSON fixtures for Warn/Blur/Hide/unknown and mixed results pass; Hide is removed, Warn covers the whole post, Blur covers only media, unknown behaves as Warn; filter reveal is independent of sensitive reveal; pre-v5 instances do not offer Blur creation but can render Blur; debug build passes. |
| F1 Timeline view filters | Two tabs retain independent settings after recreation; all three predicates and their AND combination work on initial/append/prepend/rebuild/gap paths; raw cursors remain intact; an all-filtered page fetches onward and only a truly exhausted timeline shows empty; no setting affects threads or saved posts; debug build passes. |
| F10 Catch-up | The 24-hour cutoff and 200-status cap are honored and truncation is labelled; boosts use wrapper time, originals group correctly, and both sorts are deterministic; no duplicate rows or cursors; cancel/rotate/offline/empty states work; all actions use existing callbacks and only `GetHomeTimeline` is called; debug build passes. |

## The short “do not build” note

The most attractive trap is a **small-looking scheduling or offline promise**.
This base has no dependable draft/sync substrate, and adding WorkManager would solve only execution timing, not account-scoped drafts, retry semantics, conflicts, or user recovery.
Keep Catch-up online and bounded.

The second trap is **turning content presentation into a second parser**.
Markdown, alternate frontends, GitHub cards, and bio translation all introduce fetch/render/privacy behavior outside Mastodon's status contract.
Prefer features that use fields already present in `Status`, `FilterResult`, and timeline responses.

Finally, do not build a settings item as evidence of progress.
In this codebase a toggle without a live consumer is a defect, and a display item that reaches around callbacks for a fragment is architectural debt that will make the next upstream sync harder.
