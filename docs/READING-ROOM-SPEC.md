# Reading Room: Conversation + Unroll — spec

Status: decision-complete. Companion to `docs/CREATIVE-DIRECTION.md` §3 and `docs/F1-TIMELINE-FILTERS.md`.
This is the third and last signature move. It replaces the generic "Thread" screen with two ideas:
a **Conversation** you can survey, and an **Unroll** you can read.

## 1. The promise, in one sentence

A self-thread is closer to an article than to a stack of independent rows, and the UI should say so
when the reader asks, without ever pretending the article is the whole conversation.

## 2. What exists today

`ThreadFragment` fetches `GET /api/v1/statuses/:id/context`, filters the result through the
account's filters, prepends `ancestors`, appends `descendants`, and renders the focused status
through the ordinary `StatusDisplayItem` path. The app bar says **Thread** with an origin subtitle.
There is no notion of a chain, no counts, and no alternate reading mode.

Everything below is built from that single fetch. No new endpoint, no second request, no Compose.

## 3. Conversation (the screen as it opens)

1. App bar title: **Conversation**.
2. App bar subtitle: factual counts, **"14 posts · 5 people"** — posts meaning every status in the
   fetched context including the focused one; people meaning distinct account ids. Counts describe
   what was fetched, not what "exists", and are never invented to look impressive.
3. The standard view stays default: ancestors above, focused post, descendants below, unchanged
   ordering. Reply sorting, filters, and interaction rows behave exactly as now.
4. If the conversation contains no connected same-author chain (see §4), no **Unroll** action is
   offered at all. Mixed-author threads are not forced into the reading shape.

## 4. Unroll eligibility — the detection rule

The unroll is the connected chain that contains the focused status, restricted to the focused
status's **author**:

- Start from the focused status. Walk `inReplyToId` upward while the parent is present in the
  fetched context **and** has the same author. That is the chain's head.
- From the head, walk downward following replies **whose `inReplyToId` is the previous chain
  member** and whose author matches. Each step takes at most one continuation. Where a chain member
  has more than one same-author child, the child that lies on the path to the focused status wins;
  otherwise the earliest by id wins and the other becomes a side branch.
- The chain is eligible only when it has **at least 3 members** and contains the focused status.
- **No fabrication**: if an ancestor is missing from the context response (deeper than the server
  returned), the chain simply starts at the deepest fetched member. Nothing is fetched to fill the
  gap, and no completeness is claimed about the deeper history.
- Side branches are the fetched statuses that are not on the chain, including other authors' replies.

## 5. Unroll (the reading mode)

Entered from an app bar action labelled **Unroll**. This is a mode of the same screen, not a new
destination: back returns to the Conversation.

1. **Body**: only chain members, in reading order (head first). The first post keeps a compact author
   header; subsequent chain posts render with `FLAG_NO_HEADER`, their author context carried by the
   reading line instead of a repeated avatar row.
2. **Reading line**: a single vertical rule in the grape neutral colour, drawn in the left gutter,
   connecting consecutive chain posts. It stops at the last post. It must not imply a reply by
   someone else joined the chain.
3. **Numbering**: unobtrusive, in the gutter or as a leading prefix — `1/4`, or a plain index. It
   must be smaller and quieter than post body text, and must not become a second timeline column.
4. **Side branches**: after any chain post that has fetched off-chain replies, one inline marker:
   **"2 replies to this post · Show in full conversation."** Tapping it returns to the Conversation
   view scrolled to that post. If a post has one off-chain reply the marker says "1 reply".
   Side-branch content is never interleaved into the story.
5. **App bar**: title stays **Conversation**; subtitle becomes **"Unrolled · Mara's 4 posts"** using
   the display name, plus the total remains reachable through the action that replaces Unroll.
6. **Action**: the app bar action becomes **All 14 posts**, which returns to the Conversation.
   The total count is always visible; the reader is never more than one tap from the whole
   conversation.
7. **Footer**: **"End of Mara's thread"**, and when `sideBranchCount > 0` a second line:
   **"The other 10 posts are side replies, not part of this story."** Both counts are factual and
   computed from the fetched set.
8. **Reply field** stays available below the story, unchanged.
9. **Scroll position**: entering Unroll starts at the first chain post. Exiting restores the
   Conversation's previous position.

## 6. Failures and edges

- Chain shorter than 3, or the focused post not in the chain: no Unroll action, and nothing is said
  about why — the absence is the message.
- Only ancestors fetched (deep reply whose descendants were not returned): unroll the known part,
  footer says **"End of Mara's thread"** with no claim beyond it.
- Server returned an ancestor we filtered out (keyword/blur): the filtered status breaks the chain
  at that point. The chain starts after the gap. Do not bridge a gap, do not claim continuity.
- One chain member deleted between fetch and open: the chain is computed from what is in memory at
  bind time; a missing link shortens the chain rather than showing a hole.
- Offline: the Conversation shows its existing error state. Unroll is not offered on an empty body.

## 7. Prohibitions

- No automatic unroll, ever. The reader chooses it.
- No fabricated links, no backfilling of missing ancestors, no "loading the rest of the story".
- No silent filtering presented as the full conversation: counts and the All-posts escape are
  mandatory in the unrolled view.
- No engagement ranking, no "best replies", no nested reply tree.
- No mass-block control in either view; G15 stays out.

## 8. Acceptance criteria

1. Opening a 12-post mixed conversation shows **Conversation** with a correct "N posts · M people".
2. Opening a 4-post self-thread offers **Unroll**; opening it shows 4 numbered posts, one header,
   the reading line, a footer naming the author, and an **All 4 posts** escape.
3. A self-thread with two other people replying shows side-reply markers with correct counts and
   the second footer line when applicable.
4. A single post with no replies offers no Unroll.
5. A chain where the parent was filtered out does not bridge the gap and does not claim continuity.
6. `assembleDebug` passes; no change to any timeline outside the conversation screen.

## 9. Notes for the implementer

- Chain computation belongs in a small pure class (e.g. `ConversationChain`) taking the fetched
  lists plus the focused status and returning chain, side-branch counts, author name, and totals.
  Keep it free of Android types so the rules are readable in one place.
- Reuse `StatusDisplayItem.FLAG_NO_HEADER` rather than inventing a new row type.
- The mode is a flag on `ThreadFragment`; `buildDisplayItems` branches on it. Do not add a second
  fragment, and do not touch `StatusListFragment` paging.
