# Direction: thread visualisation grammar

**Task:** t_71a77d0c · **Date:** 2026-11-08  
**Inputs:** `docs/research/thread-visualization-survey.md`, `docs/CREATIVE-DIRECTION.md`, `docs/READING-ROOM-SPEC.md`  
**Method:** Edward de Bono's lateral provocations, chiefly Escape: *Po: a conversation has no connectors, only addresses, exits, and positions.* The first three answers refused were a restored rail, a smaller inset, and numbers on every row.

**Reading the brief:** Mammut needs to show the relationships it actually knows, in words and structure that survive both layouts, without turning a conversation into a reply-tree explorer.

## Diagnosis

The old 36 dp line did one useful thing: it told the reader that two visible posts belonged together before they had parsed either one. But it was an adjacency mark, not a tree; it drew only when a parent and child happened to be consecutive list items, so it said nothing about a distant parent, a named addressee, a side branch, or what remained. Full-width mode did not merely hide a decoration. It removed the only structural cue and exposed how little that cue knew. Losing it matters because a flat sequence of replies is harder to scan, but restoring it as the main answer would preserve the same ambiguity. The replacement should carry the relationship in the reading flow: who is answering whom, when the sequence leaves its current path, what is outside it, and where the fetched view honestly ends.

## The constraint I imposed

**One semantic grammar, two renderings.** The layout may change the decoration, but never the information. Full-width body text stays at 16 dp. No inferred depth, no reordered Conversation, no relationship encoded only by colour or pixels, and no formatting or allocation during drawing. Structure belongs in an existing header, a bound text row, or the gap between rows.

## Directions

### 1. The address line — name the parent, not the wire

**Idea:** Replace the anonymous connector with a small **“Replying to Mara”** line for cross-author replies, and a quiet **Thread** badge for self-replies so “replying to yourself” does not become wallpaper.

- **What the reader learns:** The direct parent's identity, even when that parent is twenty rows above. If the parent post is absent, the reader learns that too instead of seeing a connector that implies continuity. A self-reply badge distinguishes a chain from an unrelated run of posts by the same person.
- **Indented layout:** Put the address in the existing reply-line position above the author header. The old grape rail may remain only as a secondary cue where actual parent/child adjacency is known; no information may be rail-only. Self-replies use the inline badge or the existing Unroll numbering, not another address line.
- **Full-width layout:** Render the same address or badge at 16 dp. Body text, media, and actions keep their full width. There is no substitute rail, fake gutter, or abbreviated address painted over the content.
- **Cost:** One short line on cross-author replies; an inline badge on self-replies. Account resolution and fallback states are the main complexity. Labels and badges are prepared when items are built or bound, so there is no new drawing work and no per-frame allocation.
- **Screen reader:** Speak **“Replying to Mara”**, **“Self-reply, Mara's thread”**, or **“Replying to a post that isn't shown”** before the post body. The visible words already carry the relationship; an icon never has to translate it.
- **Closest precedent:** **P11**, Elk's `StatusReplyingTo` with `isSelfReply` suppression, plus **P9**, Phanpy's honest bare **Thread** fallback when a total is unknown. Mammut already has an upstream reply-line display item that Conversation currently suppresses.
- **Why it might be wrong:** A busy exchange can acquire a repetitive stack of little addresses. Long display names and missing accounts will make some rows clumsy, and naming the parent still does not let the reader jump to it.
- **First step:** Prototype four rows in both layouts: a cross-author reply, a self-reply, a known parent whose post is absent, and an unresolved parent account. Test long names, large type, and TalkBack before adding any new decoration.

### 2. The cross-reference — let a reply point back to its exact parent

**Idea:** Give posts that have replies a small conversation-local reference mark, and let **“Reply to Mara, reference 2”** scroll to that parent and offer **“Back to reply.”**

- **What the reader learns:** Not just the addressee, but exactly which earlier post is being answered. The reader can inspect that context and return without losing their place. This is navigation information, not chain progress.
- **Indented layout:** Keep references in the author header or address line, not in the avatar gutter. Any retained adjacency rail is independent of the reference action.
- **Full-width layout:** Use the same header treatment at 16 dp. The reference and return action consume vertical header space, never body width.
- **Cost:** A stable post-id/reference map, scroll-to-parent behavior, and a one-entry return stack. More interaction logic than the address line, but ordinary bound views rather than decoration. Labels and reference strings are cached; scrolling does not create them.
- **Screen reader:** Expose real actions named **“Go to Mara's parent post”** and **“Return to reply.”** A spoken number alone is meaningless; reference marks cannot be the only label.
- **Closest precedent:** **No exact shipped precedent in the survey.** It combines **P11**'s named parent with **P13**'s Discourse post-number navigation, without importing the minimap.
- **Why it might be wrong:** Reference numbers make a social exchange feel like a legal document. They also ask the reader to remember arbitrary labels and can imply a complete, canonical transcript when the fetch is partial.
- **First step:** Test one nonadjacent parent/child pair with a jump and a reliable return. If the return is not effortless, the reference numbers are not worth keeping.

### 3. Scene changes — mark the moment the sequence leaves its path

**Idea:** When a post replies to an earlier, nonadjacent parent instead of continuing the post immediately above, insert a full-width band that says **“Branch from Mara's earlier post.”**

- **What the reader learns:** The linear exchange has jumped back to a different parent, and whose post it returned to. The mark describes a transition, not a depth level and not a claim that every following row belongs to one tidy subtree.
- **Indented layout:** Stop any adjacency rail at the transition. Place the band across the content width, then resume the rail only for a subsequently verified adjacent reply.
- **Full-width layout:** Use the same horizontal band at 16 dp, with Grape as quiet structure and words doing the actual work. No left inset and no second rule styled like the ordinary post divider.
- **Cost:** Vertical space only at nonadjacent parent transitions, plus a bind-time parent lookup. It needs no depth calculation, no reordering, and no path drawing. Band text and visibility are computed before rendering.
- **Screen reader:** Announce **“This post replies to an earlier post by Mara”** at the transition. Do not expose it as a region heading unless the following rows really are grouped under that branch.
- **Closest precedent:** **P15**, the unshipped branch-separator request in Bluesky's feedback issue, combined with **P11**'s parent name. There is no validated shipped precedent for this exact grammar.
- **Why it might be wrong:** Server order can interleave replies badly enough that the bands stutter on and off. At that point the grammar is visual noise, and a second kind of horizontal rule will be mistaken for Mammut's existing divider.
- **First step:** Run the rule against a set of long, interleaved conversations and count the transitions before drawing anything. If bands appear constantly or fail to form understandable reading sections, kill the direction.

### 4. The running header — keep the current exchange in view

**Idea:** Borrow the running head from a book: once a post's own header scrolls away, a slim line under the app bar says **“Jo, replying to Mara · post 6 of 14 fetched.”**

- **What the reader learns:** Where the visible text sits in the fetched conversation, who is speaking, and whom they are answering, even halfway through a long post. The denominator is a fetched-set position, never a claim that six posts were read or that fourteen posts are the whole conversation.
- **Indented layout:** Keep the ordinary rows and any secondary adjacency rail unchanged. Add the running header only after the relevant author/parent header has left the viewport.
- **Full-width layout:** Use the identical overlay at 16 dp. It costs a narrow strip of viewport height while active, not horizontal text space.
- **Cost:** One reusable header view, row-boundary tracking, and cached context strings. Updates happen when the current post changes, not on every animation frame; there are no allocations in the scroll callback's hot path. It also needs careful focus, large-type, and reduced-motion behavior.
- **Screen reader:** Provide the same context as an on-demand navigation element. Do not send live announcements as the visual list scrolls; TalkBack focus and pixel scroll position are not the same thing.
- **Closest precedent:** **No exact shipped precedent.** Closest are **P11** for the named parent, **P9** for position, and **P13** for Discourse's reduced mobile progress indicator.
- **Why it might be wrong:** It solves lost context inside a post more than structure between posts. A changing sticky line can become twitchy chrome, and the screen-reader version cannot depend on the visual behavior that triggers it.
- **First step:** Prototype it only after a long post's header disappears. If it appears on ordinary short posts or competes with the app bar, it has already failed.

### 5. Only the exits — show concealed structure, not visible cabling

**Idea:** Remove connectors entirely and mark only what the current reading flow leaves out: **“2 replies to Mara's post,” “An earlier post isn't shown,”** and **“End of the 14 posts Mammut fetched.”**

- **What the reader learns:** Where a side branch exists, how much known content sits outside the current path, where a gap is, and when the available reading actually ends. This is the one direction whose main subject is absence rather than adjacency.
- **Indented layout:** Use inline exit rows after the relevant post and a factual footer. Verified same-author runs may use the existing suppressed-header treatment; otherwise there is no rail.
- **Full-width layout:** Exactly the same rows at 16 dp. Nothing needs to be redrawn or moved into a gutter.
- **Cost:** A row only where a fetched branch, known gap, or actual list end exists. Counts and labels come from the fetched graph at bind time. There is no decoration cost, but every count needs the Reading Room's “what was fetched, not what exists” discipline.
- **Screen reader:** Branch markers are named buttons such as **“Show 2 replies to Mara's post in the full conversation.”** Gaps and endings are plain text in reading order.
- **Closest precedent:** **P6**, Bluesky's continuation stubs and Discourse's reply rows, plus **P10**, official Mastodon Android's terminus mark and Mammut's own honest Catch up footer.
- **Why it might be wrong:** A simple two-person exchange has no hidden branch and no gap, so it still looks like unrelated posts. This is an excellent honesty layer and an insufficient complete grammar.
- **First step:** Pair the existing Unroll side-branch marker with a precise Conversation footer, then test a plain linear exchange. That linear case is the control this direction cannot be allowed to skip.

### 6. The programme — survey the cast before reading the scene

**Idea:** Put an expandable, textual cast and branch summary in the Conversation header: **“Mara opened · Jo and Lee replied · 2 other participants,”** with per-person detail only on request.

- **What the reader learns:** Who is present, how uneven the participation is, and where replies concentrate before committing to the whole list. The body stays free of per-row structural furniture.
- **Indented layout:** The summary spans the screen above the unchanged list. Any existing rail remains a separate, secondary adjacency cue.
- **Full-width layout:** The same header at 16 dp, with no body inset. Use text rather than Slack's louder facepile.
- **Cost:** One collapsed header line and an expanded region whose height must be capped. Summaries are computed once from the fetched set. The complexity is editorial: only make claims such as **“Jo's 3 posts all reply to Mara”** when the parent ids support exactly that sentence.
- **Screen reader:** An expandable heading exposes the same factual statements and participant names in reading order. No avatar-only identities and no colour-coded branch map.
- **Closest precedent:** **P12**, Slack's participant summary, and **P8**, Zulip/Slack's conversation container. No surveyed client ships this exact textual relationship summary.
- **Why it might be wrong:** Small threads get a summary of the obvious; large ones get a compressed tree written as prose. Participant totals can also drift into the engagement-scoreboard language Mammut is trying to leave behind.
- **First step:** Try three named participants plus **“2 others,”** with no facepile and no counts beyond the existing app-bar subtitle. If the expansion needs its own scroll region, stop.

### 7. The through-line — follow one exchange, leave the branches visible as exits

**Idea:** Let the reader follow a chosen direct-parent path through a mixed-author conversation as a flat sequence, with **“Mara → Jo → Mara”** at the top and every off-path reply preserved as a named exit.

- **What the reader learns:** Exactly who is talking to whom along one exchange, which posts are outside it, and how much of that chosen path remains. Unlike Unroll, the path may cross authors; unlike a tree, it never pretends to show everything at once.
- **Indented layout:** The selected path can use the existing reading-line treatment and suppressed repeated headers where appropriate. Off-path replies never join its line.
- **Full-width layout:** Drop the reading line and gutter. Keep the path description, a bound position label such as **“Post 3 of 6 in this exchange,”** and the same exit rows at 16 dp.
- **Cost:** Path selection, branch-point navigation, side-branch counts, and a persistent **All posts** escape. The graph work happens before binding, but this is substantially more interaction state than a visual marker. Full-width rendering uses text rows, not drawing-time labels.
- **Screen reader:** Speak the selected path once, expose branch choices as named actions, and keep **“Show all 14 fetched posts”** available. Position labels must say **“in this exchange”**, not imply conversation-wide order.
- **Closest precedent:** **No exact mixed-author precedent in the survey.** It extends Mammut's **P5/P9** Unroll and **P6** exit stubs beyond a single author.
- **Why it might be wrong:** This is a second reading mode where the brief asked for a grammar. Choosing one path can conceal the reply that changes its meaning, and Mammut should not quietly appoint itself editor of an argument.
- **First step:** Do not build this as the fix. Revisit only if research on real mixed-author conversations shows one dominant path and readers explicitly ask to follow it; even then, prototype it as an extension of Unroll with the same counts and escape hatch.

## The safe one I am refusing

**“Keep the line, but inset full-width text just enough for it.”** That is not a compromise; it repeals the setting. A shorter rail in the padding is the same answer with a smaller brush. It still cannot name a parent, identify a side branch, or survive missing context. Also refused: numbering every row as if a flat denominator explained a graph, and adding facepiles until the structure looks busy enough to count as design.

## Pick: the address line

Ship **The address line** as the base grammar. It adds the relationship the rail never carried, works at 16 dp, needs no inferred depth, and is accessible by construction. It also has a buildable starting point in the reply-line display item Mammut already owns. The interesting move is not drawing a better wire. It is making the post say who it is answering.

The ship rules are deliberately narrow:

1. **Cross-author reply:** show **“Replying to {display name}”** before the author header. Use the display name, with the handle available to accessibility where names collide.
2. **Self-reply:** use the quiet **Thread** badge. Show `n/N` only where the fetched chain bounds are actually known; otherwise use bare **Thread**, as Phanpy does. Unroll keeps its existing position semantics.
3. **Missing parent:** never bridge the gap. If the account is known but the post is absent, append **“Parent post not shown.”** If the account cannot be resolved, say **“Replying to a post that isn't shown”** rather than leaking an id or inventing a name.
4. **Indented mode:** the existing rail may remain as redundant adjacency reinforcement. Removing it later is a visual cleanup, not a semantic loss. Full-width mode gets the same words and badges with no left offset.
5. **Exits and ending:** keep the existing Unroll side-reply markers. When the Conversation list is exhausted, use **“End of the 14 posts Mammut fetched.”** Do not write **“End of conversation”** unless the client actually knows there is nothing more.
6. **Do not layer on the alternatives.** No reference numbers, running header, cast summary, or branch bands in the first version. The address line needs to earn its place by being almost boring to use.

### Runner-up 1: the cross-reference

Switch if testing in long mixed conversations shows that naming the parent is not enough and readers repeatedly need to inspect the exact parent post. The switch must include jump-and-return navigation; adding reference numbers without that action is just extra literacy work.

### Runner-up 2: scene changes

Switch if real interleaved conversations produce sparse, understandable transition bands and readers do not confuse them with ordinary dividers. If the bands appear constantly, or if they imply that an interleaved server-ordered list is a set of clean branch containers, stay with the address line.

## PLAN CONFLICT

`docs/READING-ROOM-SPEC.md` §5.2–5.3 pins Unroll's reading line and numbering to a left gutter, while the current Unroll rows are full width and receive a 44 dp left offset. That repeats the geometry this direction is meant to repair.

**Smallest amendment:** in indented Unroll, keep the approved reading line and gutter numbering. In full-width Unroll, remove the 44 dp body inset and the line, and move the existing `n/N` into a bound text row or compact header at 16 dp. Side-branch markers, counts, ordering, the All-posts escape, and the honest footer stay unchanged. The number's text and accessibility label are prepared before drawing; full-width Unroll should not draw numbers in `ItemDecoration` at all.

## Performance and accessibility contract

- Full-width structure may occupy a header, a gap, or its own row. It may not add a left `outRect`, repaint the body, or reduce the text/media column.
- Precompute parent names, badges, gap labels, reference strings, and counts when the conversation model or display items change. Reuse any paint objects required by the legacy indented rail.
- No `String` formatting, `drawText`, new `Path`, new `Rect`, account lookup, or graph traversal in `onDraw`, `onDrawOver`, or a high-frequency scroll callback. At 120 Hz, per-frame allocation is a defect, not an implementation detail.
- Every visual state has a spoken equivalent in normal reading order. Colour and icons may reinforce **reply**, **branch**, **gap**, and **end**, but may not define them.
- Scroll-triggered context must not generate a stream of accessibility announcements. Navigation actions get explicit labels and preserve the reader's place.

## What this must never become

- **Fake depth.** No staircase, elbow, or “level 3” derived from an incomplete flat response and presented as fact.
- **Connector wallpaper.** Address, badge, rail, branch band, and progress line must not all appear together to prove that structure exists.
- **Thread-tree spaghetti.** Mammut is not Reddit, Hacker News, or a graph debugger. No nested connectors, minimap, collapse rail, or second navigation model in the default Conversation.
- **Hierarchy that lies.** No bridged missing parents, invented totals, inferred sibling groups, or **“End of conversation”** over a truncated fetch.
- **A participation scoreboard.** No per-row facepile, heat map, “top reply,” read streak, or celebratory completion animation.
- **Pixel-only meaning.** If TalkBack cannot hear the same relationship and act on the same branch or gap, the design is unfinished.
- **A full-width setting in name only.** Any solution that buys back a gutter by narrowing body text has failed the brief.
