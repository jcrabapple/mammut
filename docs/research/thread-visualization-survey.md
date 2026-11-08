# Thread visualisation survey — how shipped clients show that posts are connected

**Task:** t_2f66d0e7 · **Date:** 2026-11-08 · **Target:** Mammut (`app.mammut.android`), version 1.1.6
(`mastodon/build.gradle`), commit `7f7a6f043` on `feat/parity`.

**Question.** Mammut draws one vertical reply line in the avatar gutter at 36 dp. The new
**Full-width posts** display setting removes that gutter, and commit `7f7a6f043` suppresses the line
on full-width rows rather than drawing it through the words. In full width, a conversation now has
no visual structure at all. What do other shipped clients do, and which of those things still work
when there is no left gutter?

**Method.** Five kinds of evidence, labelled in the table and the sections:

- **source** — the client's own published source (Tusky, official Mastodon Android, Elk, Phanpy,
  Bluesky, Threads' production CSS as torn down by a third party).
- **doc** — vendor documentation, help centre, or design write-up.
- **screenshot** — a shipped screenshot described in words (Phanpy's README assets and marketing page).
- **second-hand** — a user comparison or review, used only where no source or vendor doc exists, and
  labelled as such.
- **unverified** — I could not confirm the shipped behaviour and say so rather than implying it.

No app was installed, no emulator was run, no app code was edited.

---

## 1. The constraint, stated precisely

Mammut's indented layout: a 40 dp avatar at 16 dp from the edge, so its centre is at **36 dp**; the
reply line is drawn at `x = V.dp(36)` (`fragments/ThreadFragment.java`, `ReplyLinesItemDecoration`).
Body text in that layout starts at 48 dp inside the item, i.e. **64 dp** from the screen edge
(`TextStatusDisplayItem` adds 48 dp; `display_item_text.xml` adds 16 dp).

Mammut's full-width layout: the item adds **0 dp**, so body text starts at **16 dp**
(`TextStatusDisplayItem.java`: `V.dp(item.fullWidth ? 0 : 48)`), matching the setting's own summary:
*"Post text, media and actions start at the edge of the timeline instead of lining up after the
avatar"* (`res/values/strings_mo.xml`). The header row still carries a 40 dp avatar at 16 dp
(`display_item_header.xml`), but the body does not respect that column.

That yields the hard geometric rule this survey is built on:

> **Any vertical connector at 36 dp is inside the text in full-width mode.** A vertical line can only
> exist in a column that body text respects. Full width deliberately removes that column. Therefore
> no variant of "a line beside the text" can be the answer; the answer has to live in the header row,
> in the gap between rows, or in the text itself.

Two further facts about the data shape matter more than any UI decision:

- `GET /api/v1/statuses/:id/context` returns `ancestors` and `descendants` as **flat arrays of
  Status, with no depth and no position field** ([Context entity](https://docs.joinmastodon.org/entities/Context/)).
  Any nesting or "which parent" structure is client-side inference from `in_reply_to_id`.
- Mammut's existing rail only draws when **two consecutive list items** are parent and child
  (`ReplyLinesItemDecoration`: `connectUp = previous != null && previous.id.equals(current.inReplyToId)`;
  `connectReply = next != null && itemID.equals(next.inReplyToId)`). In a context list in server
  order, a parent and child are usually *not* adjacent, because other people's replies sit between
  them.

So the line Mammut is losing was never a tree. It was an **adjacency mark**. By construction it can
only draw where a reply happens to sit next to its parent in the list, which in a context fetch is
mostly the self-thread case — which is exactly why it reads as "thread structure" in a screenshot of
a self-thread and as nothing at all in a busy argument. (Inference from the code and the API shape,
not a measurement across threads.)

**Bottom line for the reader in a hurry:** the vertical connector is geometry, not taste — it cannot
survive a 16 dp body. What survives is everything that fits in a header line (who this replies to,
where you are in a chain, who is present), in the gap between rows (an end mark), or as a row of its
own (a collapsed "3 more replies"). The strongest of those is also the one the rail never provided:
**the name of the person being answered.**

---

## 2. What Mammut already does (so this survey does not propose duplicates)

| Already shipped | Where | What it is |
|---|---|---|
| **Single gutter rail** | `ThreadFragment.ReplyLinesItemDecoration`, 36 dp, 2 dp stroke, `colorM3OutlineVariant`; suppressed on full-width rows by `7f7a6f043` | Vertical segments under the avatar, only where two adjacent rows are parent/child |
| **Reading line + numbering + side-branch markers (Unroll)** | `docs/READING-ROOM-SPEC.md` §5; `ThreadFragment.UnrollReadingLineDecoration` | 44 dp item offset, line at 30 dp, `1/4`-style numbering right-aligned at ~24 dp, `"2 replies to this post · Show in full conversation"` |
| **Conversation counts** | `ConversationChain.java`, `READING-ROOM-SPEC.md` §3 | `"14 posts · 5 people"`, from the fetched set only |
| **Collapsed-header chain rendering** | `buildUnrolledPostItems()` uses `FLAG_NO_HEADER` for chain posts after the first | Continuity shown by *not* repeating the author header |
| **Honest end footer** | `docs/CREATIVE-DIRECTION.md` §1 (leaf rule, "You're caught up") | A stated end, not a thread mark |

**Consequence for the survey:** "numbered chain with progress" and "repeat-suppressed author header"
are partly taken. A new idea must either (a) carry information the rail cannot — *who is answering
whom*, *where a branch leaves the chain*, *how much of this conversation is not on screen* — or (b)
replace the line's job in the full-width layout at no horizontal cost.

Also worth noting: Unroll currently adds a **44 dp gutter** (`outRect.left = V.dp(44)`) while setting
`item.fullWidth = true`. It is a full-width *row* with a non-full-width *inset*. Any recommendation
for the Conversation screen should not repeat that contradiction.

---

## 3. Pattern catalogue

Cost is expressed as work in a `RecyclerView` list like Mammut's (`StatusDisplayItem` rows, several
rows per post: header, text, media, footer).

| # | Pattern | Where it ships (evidence) | What it communicates | Works with no left gutter? | RecyclerView cost |
|---|---|---|---|---|---|
| **P1** | **Single gutter rail** — one vertical rule at a fixed x, segmented per row | Mammut 1.1.6 (source); official Mastodon Android `master` (source); Tusky `develop` (source); X timeline (doc); Threads' `.post--withLine` (source, third-party teardown); Fedilab's "thin rainbow lines" with posts left aligned (second-hand) | "This row is adjacent to the one above/below, and related" | **No** — the rule is the gutter | Decorations only; zero layout change; needs a reserved column of 36–48 dp |
| **P2** | **Avatar-hung connector** — 1–2 dp line from the avatar's bottom edge to the next reply's avatar; no separate gutter | Threads 48 px `threadline` grid column + SVG swirl (source, teardown); Bluesky `REPLY_LINE_WIDTH = 2` (source); Elk `connectReply` line inside the avatar column (source) | "This post continues into the reply below; the chain is drawn between faces" | **No** — body must be indented past the avatar column or the line hits text | Same as P1 but costs only the avatar column it already has; still a column |
| **P3** | **Staircase indent** — each nesting level shifts text right | Reddit (doc/help threads); Hacker News 40 px per level (source, third-party reimplementations); Trunks indents per level (second-hand) | "This is one level deeper than the post above" | **Technically yes, practically no** — it steals the width the setting exists to give back | `outRect.left += depth * n`; cheap, but variable insets force re-measure/re-layout and deep threads run out of width |
| **P4** | **Elbow / tree connectors** — indent plus branch lines with rounded corners | Phanpy "Nested comments thread" (screenshot); Bluesky tree view `TREE_INDENT`/`TREE_AVI_WIDTH` + branch spacers (source) | "This reply hangs off *that* parent, in a tree with siblings" | **No** — needs both a gutter and real depth | Offsets + per-depth path drawing; the most expensive pure-decoration option; depth must be computed and is unreliable from a flat context |
| **P5** | **Repeat-suppressed author header** — one author header for a run of posts from the same person; the run itself is the connection | Mammut Unroll `FLAG_NO_HEADER` (source); email conversation grouping is the same idea at conversation level (Gmail/Outlook, doc) | "Same person, continuous thought" | **Yes** — uses no horizontal space at all | Reuses the existing header flag; nearly free |
| **P6** | **Collapsed descendant stub with count** — "3 replies", "Continue thread…", "N older messages" | Bluesky `ThreadItemReadMore` "Continue thread…" and `ThreadItemShowOtherReplies` "Show more replies" (source); Discourse "n Replies" (doc); Reddit "continue this thread" (doc/help threads); Gmail "N older messages" (doc); Outlook expand arrow (doc) | "Structure exists below this point, and this much of it" | **Yes** — it is a row | One row (or a footer variant); no decoration; counts must be honest |
| **P7** | **Truncated chain with "Show this thread"** — show first 2–3 and last post, hide the middle | X: "2 or 3 posts … connected by a line … 4 or more … truncated … **Show this thread**" (doc) | "There is a longer thread; here is its shape" | Yes, but it is a timeline affordance | A row plus preview data; medium |
| **P8** | **Topic / recipient header container** — the conversation is a named object; posts inside are flat | Zulip recipient bar + topic (doc); Slack thread panel with channel summary (doc) | "Everything under this header is one conversation" | **Yes** — header only | Header view; cheap |
| **P9** | **Numbered chain with progress** — "Thread 2/5", "1/4" | Phanpy badge "THREAD 1/X" (screenshot + source); Bluesky `opThreadPostIndex`/`opThreadPostCount` from the AppView (source); Mammut Unroll `1/4` (source); Mona numbers posts it auto-splits (doc, composer-side) | "Where you are, and how much is left" | **Yes** — a badge or prefix | `TextView` in the header, or `drawText` in a decoration; chain already computed in `ConversationChain` |
| **P10** | **Terminus mark** — an explicit end-of-thread glyph or rule | official Mastodon Android `thread_end_mark.xml`, a 16×9 dp glyph at the end of every thread (source); Mammut Catch up "You're caught up" footer (product doc) | "The structure ends here" | **Yes** — horizontal band at the end | One small display item (~25 dp); trivial |
| **P11** | **Parent chip / breadcrumb** — "Replying to @handle", accent-coloured | Elk `StatusReplyingTo.vue` with `isSelfReply` suppression (source); Mastodon web "Replying to" (doc); Bluesky's 2 dp parent stub above the focused post instead of a chip (source) | "Who this post answers" — the one thing the rail never said | **Yes** — it is text in the header | One `TextView` + account lookup (`inReplyToAccountId`/`knownAccounts`); cheap |
| **P12** | **Participant facepile** — small avatars + count in a summary row | Slack: "thumbnails of discussion participants right in the channel, as well as the number of replies" (doc); Threads reply counts (doc, counts only) | "Who is in here, and how big it is" | **Yes** — header row | Header view; distinct accounts already counted in `ConversationChain` |
| **P13** | **Minimap / timeline rail** — a second scroll surface with post ticks | Discourse topic timeline; on mobile it collapses to a bottom progress bar (doc); Discourse "Conversation Map" theme component adds previews on hover/tap (doc) | "Position in a long document; jump anywhere" | Yes, as an overlay — but it competes with edge gestures | Highest: scroll listener, mapping, hit-testing, preview state; a second navigation model |
| **P14** | **Tap-the-rail to collapse** — the connector doubles as a collapse control | Reddit `[-]` / tapping the indent line (doc/help threads); Hacker News `[-]` (source, third-party reimplementation) | "You can fold this branch away" | **No** for the rail form; a chevron in the header survives | Touch handling in the list (`OnItemTouchListener`) plus per-node state; medium–high |

### Adding the one pattern nobody ships

| # | Pattern | Where it ships | What it communicates | Gutter? | Cost |
|---|---|---|---|---|---|
| **P15** | **Branch separator band** — a slightly larger gap plus a hairline between reply *branches*, same spacing inside a branch | **Unverified as shipped.** Requested repeatedly in Bluesky's thread feedback issue ("slight gap or thicker horizontal line between different reply threads") — a user request, not a shipped feature | "A new branch starts here; the posts above are a different conversation" | **Yes** — horizontal only | One decoration drawing a 1 dp rule in the gap; must not be confused with Mammut's existing divider setting |

---

## 4. The patterns worth considering, in detail

### P1 Single gutter rail

**Ships in.** Mammut 1.1.6 (`ThreadFragment.ReplyLinesItemDecoration`); official Mastodon Android
`master` ([`ThreadFragment.java`](https://github.com/mastodon/mastodon-android/blob/master/mastodon/src/main/java/org/joinmastodon/android/fragments/ThreadFragment.java),
same 36 dp `lineX`, same adjacency test); Tusky `develop`
([`ConversationLineItemDecoration.kt`](https://github.com/tuskyapp/Tusky/blob/develop/app/src/main/java/com/keylesspalace/tusky/components/viewthread/ConversationLineItemDecoration.kt),
line at `status_line_margin_start` = 36 dp per
[`dimens.xml`](https://github.com/tuskyapp/Tusky/blob/develop/app/src/main/res/values/dimens.xml),
height set by `timeline_status_avatar_height` = 48 dp and `account_avatar_margin` = 14 dp); X, in its
own words: threads "appear on your timeline connected by a line"
([help.x.com](https://help.x.com/en/using-twitter/create-a-thread)).

**What it says.** "These two rows belong together." Because every implementation tests *list
adjacency*, it says nothing when a parent and child are separated by someone else's reply.

**What it costs the reader.** Very little attention; the line is peripheral and needs no decoding.
That is its strength and its ceiling: it carries no direction, no author, no position, no branch.

**Gutter dependence.** Total. Tusky's implementation is explicit about the geometry: the divider is
drawn at `parent.paddingStart + statusLineMarginStart`, with its top set to the avatar's centre when
there is no parent above and its bottom set to the row bottom when the row below is a child.

**Cost in a RecyclerView.** One `ItemDecoration` with a reused `Paint` and `Rect`; no `getItemOffsets`
change, no relayout. Cheapest pattern in the catalogue.

**Useful anecdote.** Pachli, a Tusky fork, dropped the line and a user filed
[issue #148](https://github.com/pachli/pachli-android/issues/148): *"Compared to Tusky the connection
line between to in-thread posts is missing. This is slightly unfortunate as you loose context while
scrolling."* The line's value is real but modest: readers notice its absence, and cannot say much
about what it told them. The same impulse built Woolly, which shipped "connective lines between posts
to help you follow the replies as you scroll down" as a headline feature precisely because the
official app did not have them
([TechCrunch](https://techcrunch.com/2023/03/24/woolly-introduces-a-twitter-and-tweetdeck-inspired-mastodon-app)).

### P2 Avatar-hung connector

**Ships in.** Threads: production CSS uses
`--barcelona-threadline-column-width: 48px` and a `grid-template-areas` variant `.post--withLine`
where the line occupies its own grid row under the avatar
([ishadeed.com teardown](https://ishadeed.com/article/threads-app-css), 2023 — treat exact pixel
values as dated, the structure as stable). Bluesky: `REPLY_LINE_WIDTH = 2`, `LINEAR_AVI_WIDTH = 42`,
`TREE_AVI_WIDTH = 24`, `TREE_INDENT = tokens.space.lg`
([`PostThread/const.ts`](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/const.ts)).
Elk: `connectReply` renders `<div w-1px border="x base" mb-9 />` inside the avatar column
([`StatusCard.vue`](https://github.com/elk-zone/elk/blob/main/app/components/status/StatusCard.vue)).

**What it says.** Same as P1, but the connector is visually anchored to the face it belongs to, which
makes the chain read as "this person continues" rather than "these rows are related".

**A useful variant.** Bluesky uses the connector sparingly: only the focused post gets a 2 dp stub
above it when it is not the root (`ThreadItemAnchorParentReplyLine`,
[`ThreadItemAnchor.tsx`](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/components/ThreadItemAnchor.tsx)).
Everything else is a stub row or an indent in tree view. That is the cheapest honest use of the
pattern: attach the post the reader opened to its parent, and do not try to draw the rest.

**What it costs the reader.** Slightly more legible than P1 because the anchor is a face, not a
column. It also quietly implies the chain is same-author, because the line runs face to face.

**Gutter dependence.** The line lives in the avatar column, so it needs the avatar column to be
respected by the body — which is exactly what full width removes.

**Cost.** Same as P1; a decoration drawing a short line under each avatar.

### P3 Staircase indent

**Ships in.** Reddit (indent per level, with `?depth=N` to cap it and "continue this thread" to go
deeper — [r/help](https://reddit.com/r/help/comments/37sjhr/how_can_i_not_see_comment_replies));
Hacker News, 40 px per level, depth encoded in the width of a spacer image
([refined-hacker-news](https://github.com/plibither8/refined-hacker-news) documents the 40 px
default; [this userscript](https://greasyfork.org/scripts/409640-collapse-hackernews-parent-comments/code)
reads depth as `width / 40`); Trunks, "thick lines on the side, indenting replies"
([seankhliao.com](https://seankhliao.com/blog/12024-07-02-mastodon-app/), second-hand).

**What it says.** Depth, unambiguously — as long as the depth is knowable.

**What it costs the reader.** Width. On a 360 dp screen, 16 dp per level means level 5 has 280 dp of
text column and level 10 has 200 dp. Reddit's own `?depth` cap and "continue this thread" are the
admission that the pattern does not scale; users complain about being "scooted over
waaaaaaaaay too much" ([r/BadDesigns](https://reddit.com/r/BadDesigns/comments/1im5i43/just_a_reddit_comment_thread)).

**Gutter dependence.** It works without a gutter — it *creates* one, per row. That is the problem:
the setting exists to remove exactly this.

**Cost.** `outRect.left` arithmetic is trivial; the expensive part is that variable insets change row
measurement, and depth must be reconstructed from a flat context array without a depth field.

**Verdict.** Rejected for Mammut. See §7.

### P4 Elbow / tree connectors

**Ships in.** Phanpy's "Nested comments thread — effortlessly follow conversations, semi-collapsible
replies" ([README](https://github.com/cheeaun/phanpy/blob/main/README.md)); the shipped asset (a
redacted skeleton, which still shows the layout) has a spine down the left margin, an elbow into each
reply's avatar, and further indentation for nested replies
([asset](https://phanpy.social/assets/nested-comments-thread-hv59kZLc.jpg)). Bluesky's
`tree` view draws per-depth right borders and rounded bottom-left elbows
([`ThreadItemTreePost.tsx`](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/components/ThreadItemTreePost.tsx)).

**What it says.** Parent identity *and* sibling order: the elbow points at the parent, the spine says
which parent.

**What it costs the reader.** The most decoding work of any pattern here. It is also the only pattern
that honestly represents a tree. Bluesky's own thread-feedback issue asks for different colours
because "with a lot of vertical lines of similar colour, when the thread is long it sometimes gets
confusing to track which part a reply belongs to"
([#1550](https://github.com/bluesky-social/social-app/issues/1550)) — i.e. even the best-executed
version is hard to read at depth.

**Gutter dependence.** Needs a gutter *and* depth.

**Cost.** Offsets plus path drawing per depth; the branch-spacer logic is the fiddly part, and in a
Mastodon context the depth is frequently wrong because the parent was not returned.

### P5 Repeat-suppressed author header

**Ships in.** Mammut's own Unroll already does it: chain posts after the first render with
`FLAG_NO_HEADER`, so one author header carries a run of posts (`buildUnrolledPostItems()`). The same
idea at conversation level ships in Gmail, which stacks a conversation and shows "5 older messages"
([Stony Brook KB](https://it.stonybrook.edu/help/kb/understanding-conversation-view-in-google-mail)),
and in Outlook, which groups a conversation behind one expandable row and can optionally "group
messages by branches within conversations"
([Microsoft support](https://support.microsoft.com/en-us/outlook/mail/view-email-messages-by-conversation-in-outlook)).
Zulip's version is container-level rather than sender-level: one recipient bar per group of messages,
with the topic as the unit ([pointer docs](https://zulip.readthedocs.io/en/12.2-0/subsystems/pointer.html)).
**Verified precedent for the per-sender form is Mammut's own Unroll**, not an external client.

**What it says.** "Same voice, continuous." No graphics at all.

**What it costs the reader.** Nothing to decode; the risk is the opposite — a long same-author run
whose header has scrolled away leaves the reader unsure who is speaking.

**Gutter dependence.** None. This is the cheapest pattern that survives full width completely.

### P6 Collapsed descendant stub with count

**Ships in.** Bluesky: "Continue thread…" with a down arrow
([`ThreadItemReadMore.tsx`](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/components/ThreadItemReadMore.tsx))
and "Continue thread..." upwards for parents
([`ThreadItemReadMoreUp.tsx`](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/components/ThreadItemReadMoreUp.tsx)),
plus "Show more replies" for hidden ones
([`ThreadItemShowOtherReplies.tsx`](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/components/ThreadItemShowOtherReplies.tsx)).
Discourse: an "n Replies" button that expands inline replies and can filter the stream to them
([optional filtered replies view](https://meta.discourse.org/t/optional-filtered-replies-view/178578)).
Reddit: "continue this thread". Gmail: "5 older messages".

**What it says.** "There is more, and roughly how much." It carries the one fact a rail cannot: the
*quantity* of what is not on screen.

**What it costs the reader.** A tap, and the risk that the number is wrong. Bluesky's own lexicon
describes `moreReplies` as *"a numeric value, which is best-effort and might not be accurate"*
([defs.json](https://github.com/bluesky-social/social-app/blob/main/lexicons/app/bsky/unspecced/defs.json)) —
a useful standard to hold Mammut to.

**Gutter dependence.** None; it is a row.

### P7 Truncated chain with "Show this thread"

**Ships in.** X, documented by X: "A thread from someone you follow with 2 or 3 posts will generally
appear on your timeline connected by a line to distinguish them as a bundle. When there are 4 or more
posts in a thread, the posts will be truncated, and you'll see an option to **Show this thread**"
([help.x.com](https://help.x.com/en/using-twitter/create-a-thread)).

**What it says.** "A longer thing exists; here is its top and its tail."

**What it costs the reader.** One tap and a loss of the middle. It is the right trade in a *timeline*,
where the reader has not asked for the thread; it is the wrong trade on a conversation screen, where
they have.

### P8 Topic / recipient header container

**Ships in.** Zulip's recipient bar names the channel/topic above a group of messages; Slack's thread
panel is titled by the parent message, and the channel shows a summary row
([Slack design history](https://slack.design/articles/threads-in-slack-a-long-design-journey-part-2-of-2);
[Slack help](https://get.slack.help/hc/en-gb/articles/115000769927-Message-and-file-threads)).

**What it says.** "All of this is one conversation." It is a container, not a connector: no post is
shown to be the child of another.

**What it costs the reader.** Almost nothing, but it answers a different question (scope, not
structure). Mammut already answers that question with "Conversation" and "14 posts · 5 people".

### Related: reply gating, and the obligation to say what is hidden

Bluesky's threadgate is not a visual pattern — it is a record (`app.bsky.feed.threadgate`) that
carries `allow` rules and up to 300 `hiddenReplies`
([lexicon](https://github.com/bluesky-social/atproto/blob/main/packages/api/lexicons/app/bsky/feed/threadgate.json)).
But it has two consequences a thread view must handle: the client renders a **"Who can reply"** label
in the post anchor ([`WhoCanReply`](https://github.com/bluesky-social/social-app/blob/main/src/components/WhoCanReply.tsx)),
and the thread item itself carries `hiddenByThreadgate` so a client knows a reply exists and was
deliberately withheld
([defs.json](https://github.com/bluesky-social/social-app/blob/main/lexicons/app/bsky/unspecced/defs.json)).
Mammoth has no equivalent server record, but it has the same obligation for a different reason: the
fetched context can be truncated by depth, by filters, or by the server. A pattern that hides
structure must say so. That is what the Reading Room's side-branch markers and counts already do.

### P9 Numbered chain with progress

**Ships in.** Phanpy: a "THREAD 1/X" pill under the username, plus a variant that renders `1/5` when
the index and total are known ([`thread-badge.jsx`](https://github.com/cheeaun/phanpy/blob/main/src/components/thread-badge.jsx));
the shipped badge screenshot shows the pill as the only thread indicator on those rows
([asset](https://raw.githubusercontent.com/cheeaun/phanpy/main/readme-assets/thread-number-badge.jpg)).
The README is candid about the cost: it walks `inReplyToId` "until the root post is found", caps at
"3 API requests", and falls back to a bare "Thread" when the index cannot be found
([README](https://github.com/cheeaun/phanpy/blob/main/README.md)). Bluesky does the same numbering
from the server: `opThreadPostIndex` / `opThreadPostCount`, 1-indexed, "only present when this post
is part of the OP thread"
([defs.json](https://github.com/bluesky-social/social-app/blob/main/lexicons/app/bsky/unspecced/defs.json);
rendering in [`ThreadItemPostNumber.tsx`](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/components/ThreadItemPostNumber.tsx)).
Mammut's Unroll already ships `1/4`.

**What it says.** Position and remaining, without any spatial structure. It is the only pattern in
this survey that tells the reader "there are three more of this person's posts and then it ends".

**What it costs the reader.** A small number to parse, and a claim to trust. "2/5" is a promise that
the chain has five members; if the fetch returned four, the number lies. Phanpy's fallback to the bare
word "Thread" is the honest version.

**Gutter dependence.** None — it goes in the header line or as a prefix.

### P10 Terminus mark

**Ships in.** official Mastodon Android: `getAdapter()` appends an `ImageView` with
`R.drawable.thread_end_mark`, a 16×9 dp vector tinted `colorM3OutlineVariant`, **25 dp tall,
`MATCH_PARENT` wide**, at the end of every thread
([`ThreadFragment.java`](https://github.com/mastodon/mastodon-android/blob/master/mastodon/src/main/java/org/joinmastodon/android/fragments/ThreadFragment.java),
[`thread_end_mark.xml`](https://github.com/mastodon/mastodon-android/blob/master/mastodon/src/main/res/drawable/thread_end_mark.xml)).
Mammut's own product direction already has the same move for Catch up: a leaf-coloured rule and
**"You're caught up."** (`docs/CREATIVE-DIRECTION.md` §1).

**What it says.** "Nothing follows." It is the inverse of a connector and, in a product whose promise
is that reading can end, arguably the more on-brand signal.

**What it costs the reader.** One glance at the end; nothing mid-thread.

**Gutter dependence.** None; it is a full-width band.

### P11 Parent chip / breadcrumb

**Ships in.** Elk: `StatusReplyingTo` renders a "replying to @user" line above the card, with
`isSelfReply` used to show a different treatment for self-replies
([`StatusReplyingTo.vue`](https://github.com/elk-zone/elk/blob/main/app/components/status/StatusReplyingTo.vue));
the line under the avatar is drawn only when `connectReply` is true
([`StatusCard.vue`](https://github.com/elk-zone/elk/blob/main/app/components/status/StatusCard.vue)).
Mastodon's web interface uses the same "Replying to" convention. Bluesky does not use a chip in the
thread view; instead the focused post carries a **2 dp parent stub** above it, in the 42 px avatar
column, only when the post is not the thread root
([`ThreadItemAnchor.tsx`](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/components/ThreadItemAnchor.tsx),
`ThreadItemAnchorParentReplyLine`) — a useful hybrid: connector above the anchor, no gutter anywhere
else. A third-party client's release notes describe Bluesky's feed convention of styling the replied-to
handle in accent and bold "so it reads as a mention instead of flat body text" — **second-hand**
([Skyscraper](https://apps.apple.com/id/app/skyscraper-for-bluesky/id6754198379)).

**What it says.** **Who** this post answers. This is the one relationship the rail has never carried:
a line says "related", a chip says "to Mara".

**What it costs the reader.** One short line of header. In a self-thread it is noise, which is why
Elk suppresses it (`isSelfReply`) — exactly the rule Mammut would need.

**Gutter dependence.** None; it is text.

**Accessibility.** This is the only pattern in the catalogue whose structure is *already* spoken by a
screen reader. "Replying to Mara" is the relationship, in words.

### P12 Participant facepile

**Ships in.** Slack: "Messages with threaded replies will show thumbnails of discussion participants
right in the channel, as well as the number of replies posted"
([BrainStation, quoting Slack's announcement](https://brainstation.io/magazine/slack-adds-thread-messages)). Threads reports a
reply count that "will include the count of all replies in the thread, not just those who replied
directly to your original post"
([Threads help](https://help.instagram.com/1328624721203027)).

**What it says.** "This many people are in here." Note the Threads count semantics: the number is
*descendants*, not direct replies — a definition Mammut's `ConversationChain.totalPosts` already
matches.

**What it costs the reader.** Identity space (faces are the most attention-grabbing thing on a row)
and the risk of reading as an engagement metric, which is off-brand.

**Gutter dependence.** None; header row.

### P13 Minimap / timeline rail

**Ships in.** Discourse's topic timeline: post numbers, dates, back-to-top, drag-to-scrub; "on
smaller screens, select the bottom progress bar to expand it"
([Discourse blog](https://blog.discourse.org/2016/12/discourse-new-user-tips-and-tricks)). A theme
component, Conversation Map, adds hover/tap previews and a "Return to #…" control, defaulting to
appearing only above 12 posts
([meta.discourse.org](https://meta.discourse.org/t/conversation-map-a-mini-map-on-discourse-timeline/415792)).

**What it says.** Where you are in a long document, and how to jump. Nothing about who replies to whom.

**What it costs the reader.** A second thing to look at, and a second navigation model on a surface
where scrolling already works. Discourse itself reduces it to a progress bar on a phone.

### P14 Tap-the-rail to collapse

**Ships in.** Reddit: `[-]` next to the username, or tapping the indent line, collapses a subtree
([r/help](https://reddit.com/r/help/comments/37sjhr/how_can_i_not_see_comment_replies)); Hacker News
`[-]` does the same. Bluesky's thread-collapsing request describes exactly this ("clicking the
vertical line to the side of the comments") and is still an open feature request
([#1547](https://github.com/bluesky-social/social-app/issues/1547)) — **unverified as shipped in
Bluesky**.

**What it says.** "You can fold this away." Useful in a 500-comment forum; questionable in a
conversation the reader deliberately opened.

### P15 Branch separator band (novel)

**Status.** No shipped precedent found. The closest thing is the request in Bluesky's feedback issue:
"a slight gap or thicker horizontal line between different reply threads… it might be better if the
vertical lines don't touch this separating bar"
([#1550](https://github.com/bluesky-social/social-app/issues/1550)). It is listed here as an
experimental option, not a recommendation.

**What it would say.** "A new branch starts here." It is the only horizontal-only spatial signal
available: it costs no width, needs no depth, and works identically in both layouts.

**Why it might be wrong.** Mammut already has a user-controlled divider setting between posts; a
second, semantically different rule in the same visual language would be read as another divider.
That ambiguity is probably fatal unless the separator is unmistakably not a divider (e.g. an indent
of the rule, or a mark at the branch root only).

---

## 5. What survives with no gutter — the synthesis

Only four kinds of signal survive, and they map cleanly onto the catalogue:

1. **Things in the header line** — a parent chip (P11), a chain badge (P9), a participant facepile
   (P12). Cost: vertical space only; all of them can carry a text label, and therefore all of them
   can be made accessible.
2. **Things in the gap between rows** — a terminus mark (P10), an experimental branch separator
   (P15). Cost: one horizontal band; no width.
3. **Things that are rows** — collapsed stubs (P6), truncated chain previews (P7), group headers
   (P5, P8). Cost: a row each; no decoration.
4. **Things that are absences** — a suppressed repeated author header (P5). Cost: zero; the strongest
   signal per pixel in the entire catalogue, and the one Mammut already implements in Unroll.

Everything vertical is gone: P1, P2, P3, P4, P14. That is not a coincidence of implementation; it is
the geometry of the setting.

---

## 6. Ranked shortlist

Ranked by how much *new* information they carry per unit of width, how well they read in both
layouts, and whether the information exists without a server change. Each has a named precedent.

### 1. Parent chip with author accent — P11

**Precedent:** Elk `StatusReplyingTo`; Bluesky's parent stub above the focused post; Mastodon web's
"Replying to".
**Why first:** It is the only pattern that answers "who is this a reply to", which the rail never
did, and it costs no width at all. It is also the only one that is accessible by construction.
**Tradeoff:** It adds a header line to every reply, and in a self-thread it is redundant — it must be
suppressed when the parent's author is the post's author (Elk's `isSelfReply` rule). It also needs
`inReplyToAccountId` resolution from `knownAccounts`, and a fallback that does not leak a raw id.

### 2. Chain badge with progress — P9

**Precedent:** Phanpy "THREAD 1/X"; Bluesky `opThreadPostIndex`; Mammut's own Unroll `1/4`.
**Why second:** It tells the reader where they are and how much is left, in both layouts, at the cost
of a few characters. `ConversationChain` already computes the chain, so it is free in Mammut.
**Tradeoff:** A number is a claim. If the fetched chain is incomplete, `2/5` is a lie; the honest
fallback is Phanpy's bare "Thread" or `2/…`. It is also only meaningful for self-threads, so it
should not appear on ordinary replies.

### 3. Collapsed descendant stub with count — P6

**Precedent:** Bluesky "Continue thread…" / "Show more replies"; Discourse "n Replies"; Reddit
"continue this thread"; Gmail "N older messages".
**Why third:** It carries *quantity* — how much of the conversation is not on screen — which no rail
can. It is a row, so it works in both layouts, and it composes with the Reading Room's existing
side-branch marker.
**Tradeoff:** Each stub is a tap, and Mammut's promise is "read and be done" — a screen full of
"show more" is the opposite. Use it once per post at most, and only where a real off-chain branch
exists (which `ConversationChain.sideBranchCounts` already knows).

### 4. Terminus mark plus honest end line — P10

**Precedent:** official Mastodon Android's `thread_end_mark` at the end of every thread; Mammut's
Catch up footer.
**Why fourth:** It is the cheapest thing on the list, it survives both layouts, and it belongs to the
product's stated identity more than any connector does. It also fixes a current gap: in Conversation
there is no statement that the fetched list ended.
**Tradeoff:** It does nothing mid-thread, and it is only worth having if the counts are honest — an
"I'm at the end" mark over a truncated context fetch would undermine the whole Reading Room.

### 5. Repeat-suppressed author header — P5

**Precedent:** Mammut's own Unroll (`FLAG_NO_HEADER`); Gmail/Outlook conversation grouping is the
same idea at conversation level.
**Why fifth:** Zero width, zero graphics, near-zero cost, and it communicates continuity by absence.
**Tradeoff:** It must be gated on actual reply adjacency (`inReplyToId`), or two unrelated posts by
the same person will read as a chain — a hierarchy that lies. It also loses the speaker's identity
once the header scrolls off; a sticky author header would be needed to fix that, which is a much
larger change.

### 6. Participant facepile in the conversation header — P12

**Precedent:** Slack participant thumbnails + reply count; Threads' descendant-count semantics.
**Why sixth:** It answers "how big and how mixed is this conversation" at a glance, which is
genuinely useful before deciding to read, and it survives full width.
**Tradeoff:** Faces are the loudest thing on a screen, and a facepile plus a number is one step from
an engagement metric. It also duplicates avatars the reader is about to see anyway. Only worth it if
kept to the header and paired with the existing factual subtitle ("5 people").

### 7. Branch separator band — P15 (experimental, no precedent)

**Why last:** If it works, it is the only spatial signal that fits both layouts. But it is novel, it
collides with Mammut's existing divider setting, and no shipped client validates it. It should be
prototyped only after the six above are ruled out.

**Not shortlisted:** staircase indent (P3), elbows (P4), minimap (P13), tap-to-collapse (P14) — see
below.

---

## 7. Rejected, and why

| Rejected | Why |
|---|---|
| **Staircase indent (P3)** | It buys hierarchy with the one resource full-width mode exists to protect. Reddit's `?depth` cap and "continue this thread" are the pattern admitting its own limit; on a 360 dp phone, level 5 already loses a third of the text column. Mammut's context fetch also has no depth field, so the indent would be reconstructed from an incomplete chain. |
| **Elbow / tree connectors (P4)** | The most expensive decoration and the hardest to read at depth — Bluesky's own users ask for colour-coding to disambiguate the lines. It needs a gutter *and* trustworthy depth, and Mammut has neither in full-width mode. Phanpy earns it because its whole product is a tree; Mammut's is a reading flow. |
| **Thread minimap (P13)** | A second navigation model on a phone-sized surface, for conversations that are usually two or three screens. Discourse, the pattern's home, reduces it to a bottom progress bar on mobile. High RecyclerView cost (scroll mapping, hit-testing, previews) for information the scrollbar already gives. |
| **Tap-the-rail to collapse (P14)** | It rewards hiding the conversation on a screen the reader deliberately opened, and it puts a hit target in a 2 dp line. It also needs per-node state and animation, and it cannot exist in full width because the rail cannot. |
| **Colour-coded depth (Fedilab's "thin rainbow lines", second-hand)** | Colour doing semantic work has no text equivalent, fails under custom themes, and fails for colour-blind readers. Mammut's palette assigns colour to *jobs* (Coral = action, Sky = context, Leaf = completion, Grape = structure), not to depth. |
| **Numbering written into the post text (Mona's auto-split)** | Mammut must not edit the author's words, and the number becomes wrong the moment the thread is extended or replied to mid-chain. Mona's numbering is a publishing convenience, not a rendering grammar. |
| **"Show this thread" truncation inside Conversation (P7)** | Correct in a timeline, where the reader did not ask for the thread; wrong on the screen where they did. Truncating the conversation to a preview would re-hide what the reader just opened. |
| **Keeping the rail and insetting full-width text** | It negates the setting: the setting's own summary promises text "at the edge of the timeline instead of lining up after the avatar". A 36 dp inset is the thing being turned off. |
| **Per-row facepile thumbnails (Slack-style on every post)** | Duplicates the avatars already on the row, adds noise, and turns a conversation into a participation scoreboard. Participant identity belongs in the header if anywhere. |

---

## 8. What I could not verify

Stated plainly rather than implied:

- **Ivory (Tapbots).** Its changelog documents thread behaviour ("when in the post detail view of a
  'read more' post, all other posts in that thread are automatically expanded",
  [changelog mirror](https://indieappcatalog.com/app/187/changelog)), but I could not verify whether
  Ivory draws a rail, indent, or nothing in its thread view. **Unverified.**
- **Mona.** Documented behaviour is composer-side (auto-split into a numbered thread:
  [Lifehacker](https://lifehacker.com/tech/mona-best-mastodon-client)). Its thread-view grammar is
  **unverified**.
- **Mammoth (the iOS app).** The App Store listing documents "Threader Mode", "Hide replies" and
  "View and set who can reply" ([App Store](https://apps.apple.com/us/app/id1667573899)) but not how
  a conversation is drawn. **Unverified.**
- **Threads' current rail.** Verified from a 2023 production-CSS teardown
  ([ishadeed.com](https://ishadeed.com/article/threads-app-css)); the exact widths are dated and may
  have changed. The *structure* (a dedicated thread-line column with an SVG curve into the avatar) is
  the part being relied on.
- **Megalodon and Fedilab thread grammar.** Only second-hand comparison
  ([seankhliao.com](https://seankhliao.com/blog/12024-07-02-mastodon-app/), 2024): Fedilab "thin
  rainbow lines… posts are still all aligned"; Megalodon "a flat list of replies". Treat both as
  second-hand.
- **Official Mastodon iOS.** I found no connector or rail drawing in the public source tree: the
  thread screen's cells (`StatusThreadRootTableViewCell`, `ThreadReplyLoaderTableViewCell`) and
  `ThreadMetaView` (date + reblog/favourite counts) contain none. Absence in source is not proof of
  absence on screen, so: **no rail found in source; not visually verified.**
- **Bluesky thread collapsing by tapping the rail.** Open feature request
  ([#1547](https://github.com/bluesky-social/social-app/issues/1547)), not shipped as far as I can
  verify.

---

## 9. Implementation notes for whoever builds the chosen direction

- **Never allocate in `onDraw`.** Mammut's existing decorations already hoist `Paint` and `Rect`;
  keep that. A `Rect` or `Path` created per row per frame is a defect at 120 Hz.
- **`getItemOffsets` is a layout change, not a paint change.** Anything that adds a left inset
  (indent, elbow margin, Unroll's 44 dp) forces re-measure and reflow; header-only patterns
  (chip, badge, facepile) do not.
- **Adjacency is the only structural fact the list has.** `inReplyToId` gives parent identity;
  list order gives nothing. Any pattern that implies depth or a tree needs its own depth computation
  and must degrade honestly when a parent is missing from the fetched context.
- **Counts are claims.** Bluesky labels its own `moreReplies` "best-effort"; Mammut's Reading Room
  already promises "counts describe what was fetched, not what exists". Every stub, badge and total
  must follow that rule.
- **Accessibility.** The rail has no screen-reader representation today. Prefer patterns that are
  text: "Replying to Mara", "Thread 2 of 5", "3 replies not shown". Then the structure exists twice,
  which is the only version that satisfies the product's own accessibility standard.

---

## 10. Sources

**Clients and source code**

- Mammut 1.1.6: `mastodon/src/main/java/org/joinmastodon/android/fragments/ThreadFragment.java`
  (`ReplyLinesItemDecoration`, `UnrollReadingLineDecoration`), `ui/ConversationChain.java`,
  `ui/displayitems/StatusDisplayItem.java`, `ui/displayitems/TextStatusDisplayItem.java`,
  `res/layout/display_item_text.xml`, `res/layout/display_item_header.xml`,
  `res/values/strings_mo.xml`, commit `7f7a6f043`.
- Official Mastodon Android:
  [ThreadFragment.java](https://github.com/mastodon/mastodon-android/blob/master/mastodon/src/main/java/org/joinmastodon/android/fragments/ThreadFragment.java),
  [thread_end_mark.xml](https://github.com/mastodon/mastodon-android/blob/master/mastodon/src/main/res/drawable/thread_end_mark.xml),
  [mastodon-ios tree](https://github.com/mastodon/mastodon-ios/tree/main/Mastodon/Scene/Share/View/Content).
- Tusky:
  [ConversationLineItemDecoration.kt](https://github.com/tuskyapp/Tusky/blob/develop/app/src/main/java/com/keylesspalace/tusky/components/viewthread/ConversationLineItemDecoration.kt),
  [dimens.xml](https://github.com/tuskyapp/Tusky/blob/develop/app/src/main/res/values/dimens.xml).
- Pachli: [issue #148, "Connection line missing in conversation threads"](https://github.com/pachli/pachli-android/issues/148).
- Elk:
  [StatusCard.vue](https://github.com/elk-zone/elk/blob/main/app/components/status/StatusCard.vue),
  [StatusReplyingTo.vue](https://github.com/elk-zone/elk/blob/main/app/components/status/StatusReplyingTo.vue).
- Phanpy: [README](https://github.com/cheeaun/phanpy/blob/main/README.md),
  [thread-badge.jsx](https://github.com/cheeaun/phanpy/blob/main/src/components/thread-badge.jsx),
  [thread number badge screenshot](https://raw.githubusercontent.com/cheeaun/phanpy/main/readme-assets/thread-number-badge.jpg),
  [nested comments screenshot](https://phanpy.social/assets/nested-comments-thread-hv59kZLc.jpg).
- Bluesky:
  [PostThread/const.ts](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/const.ts),
  [ThreadItemTreePost.tsx](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/components/ThreadItemTreePost.tsx),
  [ThreadItemReadMore.tsx](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/components/ThreadItemReadMore.tsx),
  [ThreadItemReadMoreUp.tsx](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/components/ThreadItemReadMoreUp.tsx),
  [ThreadItemShowOtherReplies.tsx](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/components/ThreadItemShowOtherReplies.tsx),
  [ThreadItemPostNumber.tsx](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/components/ThreadItemPostNumber.tsx),
  [ThreadItemAnchor.tsx](https://github.com/bluesky-social/social-app/blob/main/src/screens/PostThread/components/ThreadItemAnchor.tsx),
  [WhoCanReply](https://github.com/bluesky-social/social-app/blob/main/src/components/WhoCanReply.tsx),
  [unspecced/defs.json](https://github.com/bluesky-social/social-app/blob/main/lexicons/app/bsky/unspecced/defs.json),
  [threadgate lexicon](https://github.com/bluesky-social/atproto/blob/main/packages/api/lexicons/app/bsky/feed/threadgate.json),
  [issue #1550](https://github.com/bluesky-social/social-app/issues/1550),
  [issue #1547](https://github.com/bluesky-social/social-app/issues/1547),
  [issue #1044](https://github.com/bluesky-social/social-app/issues/1044).

**Vendor documentation and design write-ups**

- X: [How to create a thread](https://help.x.com/en/using-twitter/create-a-thread).
- Threads: [production CSS teardown](https://ishadeed.com/article/threads-app-css);
  [reply-count semantics](https://help.instagram.com/1328624721203027).
- Slack: [threaded messaging, as quoted by BrainStation](https://brainstation.io/magazine/slack-adds-thread-messages);
  [thread design history](https://slack.design/articles/threads-in-slack-a-long-design-journey-part-2-of-2);
  [message and file threads](https://get.slack.help/hc/en-gb/articles/115000769927-Message-and-file-threads).
- Gmail: [Group emails into conversations](https://support.google.com/mail/answer/5900);
  [conversation view walkthrough](https://it.stonybrook.edu/help/kb/understanding-conversation-view-in-google-mail).
- Outlook: [View email messages by conversation](https://support.microsoft.com/en-us/outlook/mail/view-email-messages-by-conversation-in-outlook);
  [conversation view options, incl. "Use Classic Indented View"](https://ablebits.com/office-addins-blog/outlook-conversation-view).
- Discourse: [why replies are not a tree](https://meta.discourse.org/t/disable-showing-forked-replies-as-strays-at-the-bottom/110444);
  [filtered replies view](https://meta.discourse.org/t/optional-filtered-replies-view/178578);
  [topic timeline on mobile](https://blog.discourse.org/2016/12/discourse-new-user-tips-and-tricks);
  [Conversation Map theme component](https://meta.discourse.org/t/conversation-map-a-mini-map-on-discourse-timeline/415792).
- Zulip: [pointer and recipient bar](https://zulip.readthedocs.io/en/12.2-0/subsystems/pointer.html);
  [reading strategies](https://chat.zulip.org/help/reading-strategies).
- Mastodon API: [Context entity](https://docs.joinmastodon.org/entities/Context/).

**Second-hand comparisons (labelled as such above)**

- [seankhliao.com, "mastodon app"](https://seankhliao.com/blog/12024-07-02-mastodon-app/) — Fedilab,
  Trunks, Megalodon, Tusky, Pachli thread comparisons, July 2024.
- [TechCrunch on Woolly](https://techcrunch.com/2023/03/24/woolly-introduces-a-twitter-and-tweetdeck-inspired-mastodon-app) —
  a client adding "connective lines between posts" specifically because the official app lacked them.
- [Hacker News indent reimplementations](https://github.com/plibither8/refined-hacker-news) —
  40 px per level, depth read from spacer width.
- [Reddit collapse / continue-thread threads](https://reddit.com/r/help/comments/37sjhr/how_can_i_not_see_comment_replies).
