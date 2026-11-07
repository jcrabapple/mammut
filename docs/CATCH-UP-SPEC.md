# Catch up — specification

**Date:** 2026-11-07
**Status:** Implementation-ready. The implementer should not need to make product decisions.
**Sources of intent:** `docs/CREATIVE-DIRECTION.md` §"The three signature moves", move 1 "The Last Page" (and "The first proof"); `docs/FEATURE-PLAN.md` §F10; `docs/research/feature-viability-review.md` (G4/G5/G9/G13 verdicts and the traps catalog).
**Slice:** Catch up is the first slice of the Last Page. Description-always-on in Catch up, Unroll, and everything in §9 are later slices.

Catch up is a bounded reading view reached from Home. It answers "what happened while I was away", states its own scope in words, and ends with an honest footer. It is a snapshot fetch, not a mode of Home and not a second timeline.

---

## 1. Entry point

- **Where:** `res/menu/home_overflow.xml`, top group, directly above the `timeline_view` item — "beside Timeline view" per the creative direction. The existing top-group order becomes: **Catch up**, **Timeline view**, then **Edit timelines** (announcements stays hidden as today).
- **Label:** `catch_up` → "Catch up".
- **Icon:** `ic_fluent_book_clock_24_regular` — already in the tree (book + clock reads as "read what you missed"); no new drawable.
- **Visibility rule:** the item is shown only while the currently visible tab's `TimelineDefinition` is `TimelineType.HOME`. Lists, hashtags, and custom local tabs have no home timeline; showing Catch up there would fetch data that is not that tab's, which is exactly the dishonesty this direction refuses. Visibility is recomputed in `HomeTabFragment.updateOverflowMenu()` next to the existing `timeline_view` visibility logic.
- **Scroll position and new-posts pill are irrelevant.** Catch up does not read or change Home's scroll position, and opening it does not clear or trigger the "New posts" affordance. There is no "already at top" special case.
- **Brand-new account / nothing fetched:** Catch up is always offered. If the fetch returns nothing inside the window, the view shows the empty-window state (§7), not a disabled menu item.
- **Destination:** a new `CatchUpFragment` (a `StatusListFragment` subclass) opened with `Nav.go`, carrying `accountID` and the current tab's `TimelineDefinition` index, exactly the way `TimelineViewSettingsFragment` receives its tab today.

## 2. Scope and window

Two constants, one place:

```java
// In CatchUpFragment:
public static final long CATCH_UP_WINDOW_MS = 24L * 60 * 60 * 1000; // 24 hours
public static final int  CATCH_UP_MAX_POSTS = 200;
```

- **Not user-adjustable in this slice.** F10's sequencing hazard says the first Catch-up version is preference-free (`TimelineDefinition`/`AccountLocalPreferences` schema is owned by F1/F18/F19); a settings surface with no proven demand is a dead toggle, which the feature plan explicitly calls a defect.
- **What "24 hours" means:** the cutoff is `System.currentTimeMillis() - CATCH_UP_WINDOW_MS`, compared against each status **wrapper's** `createdAt` (an absolute epoch instant). The window never touches local wall-clock time: no "since midnight", no calendar-day boundary, no `Calendar`/`LocalDate` arithmetic anywhere in the view. Consequences, all intended: crossing a timezone changes nothing; a DST transition changes nothing (the window is always the elapsed 24 hours before the view was opened); a user who changed the device clock is lying to themselves, not to the view. The scope line's "last 24 hours" is therefore always literally true.
- **Inclusion rule:** a wrapper whose `createdAt` is at or after the cutoff is in the window, boost or not — a boost of a week-old post inside the window counts (F10 rule). The cutoff applies to the wrapper, never to the boosted original.
- **Where the constants are *shown*:** the fixed header (§4) carries the scope line. The cap is named in the header only when it truncated the window (§6, §7).

## 3. Fetch strategy

- **Fetch first, display once.** Opening Catch up starts a sequential backward fill of `GetHomeTimeline` and the list appears when the fill ends. Why: group headers, per-author counts, and the boosts section are only correct over the complete fetched set; a progressive reveal would reflow headers and counts under a reading finger, which this direction forbids.
- **Paging shape:** page 1 is a plain `GetHomeTimeline(null, null, 40, null)`; each later page passes `maxID` = the oldest fetched wrapper's ID and `limit=40`. Newest first, exactly the request class's existing cursor semantics. No new request class, no `since_id` use.
- **Stop conditions, checked after every page:** (a) the oldest fetched wrapper is older than the cutoff (the boundary page's older stragglers are dropped); (b) 200 wrappers fetched; (c) a page returns fewer than 40 statuses (server pagination end); (d) five pages fetched. Conditions b and d coincide by construction.
- **Request ceiling:** worst case is exactly **5 requests** (5 × 40 = 200). Because filter projection happens after the fill (§4), the ceiling holds unconditionally — filters never cause extra requests. After a "Load older posts" extension (§6) each tap adds at most one more request, always user-initiated.
- **What the user sees while it fills:** the fixed header (scope line + filter summary) renders immediately, with a centered spinner in the list area and the string "Fetching the last 24 hours…". No skeleton rows — grouping is unknown until the fill ends — and no percentage.
- **Cancellation:** leaving the screen (back, tab switch, process death) cancels the in-flight request through the existing `MastodonAPIRequest` cancellation that `BaseStatusListFragment` already performs on destroy. There is no background continuation, ever; a half-fetched set is discarded unless an error mid-fill preserved it as the partial state (§7).
- **Rotation:** on configuration change the fragment is recreated and refetches. The whole view is at most 5 requests, so refetching is cheaper and simpler than retaining a cursor-sensitive snapshot; the fetch is a snapshot, not state worth preserving.
- **Snapshot semantics:** the list is frozen at open. New posts arriving during reading never insert into Catch up (the direction's rule that Mammut never inserts a fresh batch under the user's finger). Reopening is the refresh.

## 4. Filters and inline descriptions

- **View filters:** Catch up reads the *current Home tab's* `TimelineDefinition` (`showBoosts`, `showReplies`, `mediaOnly`, combined with AND) and applies the same predicate semantics F1 established in `StatusListFragment` — hide boosts removes `status.reblog != null`, hide replies removes `status.inReplyToAccountId != null`, media-only requires non-empty attachments on the content status. Reuse/extend the F1 predicate; do not write a second implementation.
- **Filtering is projection, not fetching.** The fill runs to the window/cap regardless of filters (§3); filters then project the fetched raw list into display items. This keeps the request ceiling honest and makes filtered-out volume *countable*, which the footer copy relies on.
- **`showInlineAltText`:** honored exactly as the ordinary timeline renders it (`GlobalUserPreferences.showInlineAltText` through `MediaGridStatusDisplayItem`). No override in this slice — "descriptions always on in Catch up" belongs to the Caption Track slice, and until then the scope line must tell the truth about what the view is doing.
- **The filter summary, in words:** the second line of the fixed header always names all three rules with their current state, e.g. **"Replies hidden · no boosts · image descriptions on"** (pluralized/worded per actual state; with everything at defaults: "Replies shown · boosts shown · image descriptions off"). No compressed shorthand and no "defaults" elision: the line is the view's stated scope, and a rule that is silent cannot be part of an honest scope.
- **Changing filters:** tapping the summary line opens the existing `TimelineViewSettingsFragment` with the current tab's definition index (the same args `HomeTabFragment` passes today). On return — or on the existing `StatusDisplaySettingsChangedEvent` — Catch up rebuilds its display items from the raw statuses it already fetched. No refetch, cursors untouched (architecture rule 3).
- **Filters hiding everything fetched:** §7's filtered-to-empty state. Because the fetch is not filter-aware, "everything fetched was hidden" is the only filtered-empty case, and the copy may say exactly that without claiming anything about posts the fetch never reached.

## 5. Grouping

- **Grouping applies to the whole view, with no threshold.** A view where some regions are grouped and others are not has unpredictable scan order and unreliable heading navigation; the per-author count only means something when grouping is universal. (A "group only when busy" threshold is rejected.)
- **Original posts** (`reblog == null`) group by author. **Group order:** by each author's most recent in-window post, most recent first. **Within a group:** newest first. Why newest-first inside groups: it matches every other surface in the app and the "what happened while I was away" reading direction; oldest-first narrative ordering is rejected because Catch up would be the app's only bottom-up list. (The order is deterministic either way — F10's acceptance requires that.)
- **Header:** one header per author — display name (handle when blank) plus a pluralized count: "Mara · 4 posts" / "Mara · 1 post". Implemented as a section header display item; the posts beneath it are built with `FLAG_NO_HEADER` so no inner status repeats the author line.
- **A group of one** looks exactly like every other group — same header, count "1 post". No special-casing: the consistent heading structure is what makes the view navigable.
- **Replies are ordinary posts.** A reply groups under its author like anything else; no conversation threading, no reply context lines (non-goal).
- **Boosts** (wrappers with `reblog != null`) are removed from the author groups and form **one separate section after all original-post groups**, subgrouped by booster. Section header: "Boosts · 12 posts". Subgroup order and intra-subgroup order follow the same most-recent/newest-first rules as above.
- **Collapsible:** the boosts section header toggles the section. Expanded by default (nothing is hidden silently); the collapsed state is session-only and not persisted — reopening Catch up always starts expanded. When collapsed, only the header shows; when expanded, each boost renders as its normal row (booster attribution line + original post, as the ordinary timeline renders it).
- **Sort menu:** F10 sketches an optional engagement-based menu sort. **Cut from this slice** — it is ranking adjacent, the required content never asked for it, and the default most-recent-activity order is deterministic and sufficient. It can be added as an explicit menu action later without contract changes.

## 6. The end

The last item is a real footer display item with the leaf-colored rule (leaf = completion, per the palette's job list). It renders in exactly one of these variants:

| Case | Title | Body | Action |
|---|---|---|---|
| Caught up within the cap | **You're caught up.** | "This view includes the {n} posts Mammut fetched from the last 24 hours." ({n} pluralized) | **Load older posts** |
| Caught up at the cap | **End of this catch-up** | "Showing the most recent 200 posts from the last 24 hours. Older posts in this window aren't included." | **Load older posts** |
| Empty window | **Nothing new in the last 24 hours.** | — | **Load older posts** |

- When the cap truncated the window, the header's scope line also gains "· the most recent 200 posts" from the moment the fill ends, not only at the footer.
- **What "Load older posts" does:** it extends *this view*, in place. Each tap fetches one more page of 40 via `maxID` = the oldest fetched wrapper ID, merges it into the existing grouping, and keeps the footer last. The 24-hour window no longer applies once the user has explicitly left it — that is the point of the button — and the initial 200 cap likewise does not gate extension pages. An in-flight extension shows an inline progress row; a failed extension shows an inline error row with "Try again" and leaves all existing content untouched. When the server returns an empty page, the action disappears and the body becomes: "This view includes all {n} posts this server has for your timeline." — a completeness claim only the server's own pagination end can license.
- **After any extension**, the footer body drops the "from the last 24 hours" phrase and states the actual total: "This view includes {n} posts Mammut fetched." The title stays as its variant dictates.
- **The app must never claim:**
  - that the user has seen *everything* new — no "all caught up with everything", no "you haven't missed a post", and at the cap, never anything that implies the window was fully covered ("End of this catch-up" is the honest title, not a synonym for finished);
  - that a loaded page boundary is an ending — the footer exists only when the fetch stopped for a stated reason (window end, cap, or server end); ordinary Home still never shows this footer merely because a page ran out;
  - any streak, score, congratulations, or celebration animation — the leaf rule and the final sentence are the entire ceremony;
  - that the window was wider or narrower than it was — the scope line and the cap line always match the actual fetch parameters;
  - read tracking of any kind — the app does not record which posts the user has read.
- **Filtered-to-empty does not use the end footer.** It has its own state and copy (§7), because "no posts match this view" and "you're caught up" are different facts.

## 7. States

| State | On screen | What the user can do |
|---|---|---|
| **Loading (initial fill)** | Fixed header (scope line + filter summary) renders immediately; centered spinner in the list area; string "Fetching the last 24 hours…". No skeleton rows. | Leave (cancels the fetch, §3); change nothing else. |
| **Partially loaded (error mid-fill)** | The pages fetched so far render as the normal grouped list; an inline error card sits where the next page would be: "Can't reach {instance}." / "Your place in this timeline is unchanged." | "Try again" resumes paging from the last cursor; or leave; or read what loaded — it is not discarded. |
| **Empty window** | "Nothing new in the last 24 hours." + **Load older posts**. | Load older posts; change view filters (summary stays in the header); leave. |
| **Filtered-to-empty** | "No posts match this view." (supporting line only when it is known: "{n} posts were fetched but none match this view's filters") + **Change view filters** + secondary **Load older posts**. | Change view filters (opens `TimelineViewSettingsFragment`; the view rebuilds without refetching); load older posts; leave. Never claim "everything is hidden" — only that no *fetched* post matches. |
| **Offline / first request fails** | The header plus the error card: "Can't reach {instance}." / "Your place in this timeline is unchanged." / **Try again**. No cached snapshot exists and none is faked — the view is online-only by design (F10's explicit rejection of offline catch-up). | Try again; leave. |
| **Server error (5xx/429)** | Same presentation as offline: "Can't reach {instance}." with "Try again". Raw HTTP codes and server exception text are never surfaced in this view. | Try again; leave. |
| **Cap reached** | Full grouped list; header scope line carries "· the most recent 200 posts"; footer variant 2 (§6). | Load older posts; change filters to narrow the view (the direction's honest answer to a busy window); leave. |

Every state's copy follows the voice table in the creative direction: name the view, state what is true, offer one useful action.

## 8. Accessibility

- **Heading structure:** the scope line is the screen's first heading. Each author-group header and the boosts section header are accessibility headings (`ViewCompat.setAccessibilityHeading(true)`), so headings navigation walks: scope → author groups in view order → Boosts → end footer title.
- **Counts are part of the announced heading text**, not a separate element: "Mara, 4 posts" / "Mara, 1 post" via pluralized string resources.
- **Expand/collapse:** the boosts header is a single actionable element with `stateDescription` "Expanded"/"Collapsed"; on toggle, `announceForAccessibility` speaks the new state ("Expanded, 12 posts" / "Collapsed"). Its touch target is ≥48dp regardless of font scale.
- **End footer:** the title is also a heading (jump target); the footer reads as one region — title, body, then the "Load older posts" action.
- **Font scaling at 200%:** nothing in the header, group headers, or footer is single-line/ellipsized — every text wraps. A long author name plus count wraps as a block, never truncating the count. Filter-summary words wrap rather than scroll. Rows inherit standard `sp` scaling; no fixed-height header or footer containers. The screen remains scrollable end to end at 200% with every element reachable.
- Live regions are not used: the view is a static snapshot and nothing arrives while reading.

## 9. Non-goals

This slice does not include, and later work must argue its way past this list:

1. **No engagement ranking** and no algorithmic ordering — including F10's optional engagement sort, which is cut here (§5).
2. **No streaks, scores, or celebration.**
3. **No scheduled digest, no notification channel, no background worker.** Catch up exists only when the user opens it.
4. **No streaming or live insertion** (G4 territory; also §3's snapshot rule).
5. **No cross-tab aggregation** — Catch up is Home-only and reads one tab's filters.
6. **No read-position or unread tracking, no persistence of what was read.**
7. **No user-adjustable window or cap preferences.**
8. **No offline snapshot or cache of the catch-up view.**
9. **No grayscale media and no bot-fold work** — G5's opt-in bot collapse is compatible with this view but is its own feature; it is not built here and must compose with this view later, not be bundled.
10. **No always-on image descriptions in Catch up** — that is the Caption Track slice's change; this slice honors the existing `showInlineAltText` preference.
11. **No new request classes, dependencies, background permissions, or persistent preferences.**

---

## Done means

A reviewer can check each line against the implementation:

- [ ] `home_overflow.xml` has Catch up above Timeline view, with `ic_fluent_book_clock_24_regular`; `HomeTabFragment` shows it only when the current tab is `TimelineType.HOME`.
- [ ] `CATCH_UP_WINDOW_MS = 24h` and `CATCH_UP_MAX_POSTS = 200` exist as the only source of the window and cap, in `CatchUpFragment`.
- [ ] The cutoff is computed from epoch time minus the window; no calendar/wall-clock/local-time arithmetic appears anywhere in the view.
- [ ] Inclusion tests the wrapper's `createdAt` (a boost of an old post inside the window counts).
- [ ] The fill is sequential `GetHomeTimeline` paging at `limit=40`, stops at cutoff/cap/short-page/five pages, and the worst case is 5 requests; filters never add requests.
- [ ] The list renders once, after the fill; the header shows during loading; leaving the screen cancels in-flight work; no background fetching; rotation refetches.
- [ ] The current tab's view filters apply as projection over the raw fetched set, with the same predicate semantics as F1; `showInlineAltText` is honored as-is.
- [ ] The filter summary names all three rules in words, is tappable, opens `TimelineViewSettingsFragment` with the current tab index, and changing filters rebuilds the view without refetching.
- [ ] Author groups: one header per author with a pluralized count, `FLAG_NO_HEADER` inside groups, most-recent-activity group order, newest-first within groups; single-post groups look the same as the rest.
- [ ] Boosts live in one separate collapsible section after the author groups, subgrouped by booster, expanded by default, session-only collapse state.
- [ ] The footer shows the correct variant (within cap / at cap / empty window) with the exact copy from §6, and "Load older posts" extends this view in place, one 40-post page per tap, updating the footer body per §6.
- [ ] The footer only ever appears at a stated stopping point, and no string in the feature claims completeness except after server pagination end.
- [ ] Every state in §7 is reachable and acts as described (verified: busy account, quiet account, filtered-to-empty, airplane mode, mid-fill failure, cap).
- [ ] TalkBack: scope line, author headers, boosts header, and footer title are headings; counts read inside the header; boosts toggle announces Expanded/Collapsed.
- [ ] At 200% font scale nothing in the header, group headers, or footer is truncated or clipped.
- [ ] `./gradlew :mastodon:assembleDebug` passes; no display item holds or casts to a fragment; all row actions go through `StatusDisplayItem.Callbacks`.

## Expected file changes

Paths relative to `mastodon/src/main/`.

| File | Change |
|---|---|
| `java/org/joinmastodon/android/fragments/CatchUpFragment.java` | **New.** The view: constants, fill loop, snapshot, footer variants, states. |
| `java/org/joinmastodon/android/ui/CatchUpGrouping.java` (or a class beside the fragment) | **New.** Grouping/ordering/collapse model over raw statuses → display items. |
| `java/org/joinmastodon/android/fragments/HomeTabFragment.java` | Overflow wiring: menu item visibility (HOME tabs only) and navigation. |
| `res/menu/home_overflow.xml` | Add the `catch_up` item. |
| `java/org/joinmastodon/android/ui/displayitems/StatusDisplayItem.java` | Footer display item type (or a section-header variant) plus its build path. |
| `java/org/joinmastodon/android/ui/displayitems/SectionHeaderStatusDisplayItem.java` | Extend for count-bearing group headers and the collapsible boosts header (or subclass it). |
| `res/layout/display_item_catch_up_footer.xml` (name may vary) | **New.** Leaf rule, title, body, action. |
| `res/values/strings.xml` | `catch_up`, scope-line and cap strings, filter-summary words, footer copy (all variants), loading/empty/filtered-empty/error strings, pluralized count strings. |
| `java/org/joinmastodon/android/fragments/StatusListFragment.java` | Only if the F1 filter predicate needs a shared entry point for reuse. |

Deliberately **not** touched: `GetHomeTimeline`, `TimelineDefinition`, `AccountLocalPreferences`, `GlobalUserPreferences`, `MediaGridStatusDisplayItem`, anything in Home's own scrolling or the FAB.
