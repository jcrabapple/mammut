# Mammut creative direction

**Date:** 2026-11-07
**Inputs:** `docs/research/fediverse-feature-survey-2.md`, `docs/research/feature-viability-review.md`, `docs/FEATURE-PLAN.md`, and the current View-based screens.

**Reading the brief:** Mammut does not need another advantage on a feature matrix. It needs to make a different promise: you can read your corner of the fediverse with enough context to understand it, and then be finished.

## Positioning

**Mammut is the fediverse client for people who want to read their timeline and be done with it.** It is for someone who has chosen people to follow, reads in a few deliberate sittings, and cares about the words, images, and conversation more than the stream's volume. Tusky and Pachli already cover the capable general-purpose client; Mona and Ivory already court people who enjoy dense controls and timeline information. Mammut should optimize for the moment those clients treat as an absence: the end. It should feel quiet without being empty, editorial without becoming an algorithmic news app, and finite without pretending a capped fetch contains everything. Not a wellness tracker. Not a minimal skin. A personal paper whose views you edit yourself.

## Directions considered

### 1. The power desk — every fediverse control, one thumb away

- **Mechanism:** Combine the thread composer, search builder, Collections, safety batch actions, filters, and account controls into a dense command surface.
- **Precedent:** Mona's activity statistics and collections; Fedicat's safety actions.
- **Risk:** This is a roadmap, not a personality. It also puts the most brittle features at the center of the product.
- **Verdict:** Keep the useful controls. Do not make this the identity.

### 2. The description-first client — the fediverse with its captions turned on

- **Mechanism:** Treat image descriptions as post content in the timeline, composer, and shared image.
- **Precedent:** Fedicat's inline alt text and automatic alt display on its detail screen.
- **Risk:** A single feature, however good, is easily copied; descriptions also make a dense feed substantially taller.
- **Verdict:** This is one signature move, not the whole position.

### 3. The last page — a social client with a real ending

- **Mechanism:** Turn Home into named, bounded reading views; carry descriptions and conversation context into those views; make every reading session end with an honest account of what was read.
- **Precedent:** *The Economist Espresso*, which makes completion part of the reading experience rather than a failure to find more content.
- **Risk:** A finite view can be incomplete on a busy account. The cap and time window must always be visible, with an explicit route to older posts.
- **Buildable first step:** The existing F10 plan already permits a bounded 24-hour/200-post catch-up using `GetHomeTimeline`; no new dependency or feed protocol is needed.

**Pick: the last page.** It gives the palette, FAB, per-tab filters, accessibility work, and thread work one job. The other two directions become ingredients rather than competing personalities.

**The safe one I am refusing:** “Mammut is the customizable Android power client.” A coral theme, custom icons, and more toggles would make a better-skinned Moshidon. They would not give anyone a reason to remember it.

## The three signature moves

These moves share one grammar: **a stated scope, context in the reading flow, and a visible end.** That combination is the identity. None should ship as an isolated toggle with a marketing name.

### 1. The Last Page — every Home view can be finished

**What the user sees**

- **Home** gains **Catch up** at the top of the existing timeline overflow, beside **Timeline view**. It is a reading destination, not another item under Display settings.
- Catch up opens with a plain scope line: **“Home · last 24 hours”** and **“The most recent 200 posts”** when that cap applies. The current view's rules appear in words: **“Replies hidden · no boosts · image descriptions on.”** Tapping that line opens the existing view-filter sheet.
- Posts are grouped by author. The author header appears once, followed by that person's posts in chronological order; boosts form a separate, collapsible section. This uses F10's existing grouping plan and the display items' existing `FLAG_NO_HEADER` behavior rather than inventing a feed item.
- The last item is a real footer with a leaf-colored rule: **“You’re caught up.”** Beneath it: **“This view includes the 126 posts Mammut fetched from the last 24 hours.”** The secondary action is **“Load older posts.”**
- At the 200-post cap, the title changes to **“End of this catch-up”** and the copy says **“Showing the most recent 200 posts from the last 24 hours. Older posts in this window aren’t included.”** It never claims that everything in the window was read. Ordinary Home reuses the footer only when the server has no older posts; reaching the bottom of a loaded page is not an ending.
- The current **New posts** affordance remains. Mammut never inserts a fresh batch under the user's finger merely to keep the session going.

**Why it fits**

The per-tab filters stop being preference plumbing and become the editorial rules of a personal view. The FAB remains the fast way around the app; Catch up is the reason to stay in one place long enough to read.

**What it deliberately does not do**

- No engagement ranking, “recommended” insertion, infinite autoplay, streak, score, or congratulatory animation.
- No grayscale media as a punishment for opening the app. G5's opt-in bot collapse is compatible; its grayscale treatment is not.
- No scheduled digest or notification. The user chooses when to read.
- No hidden truncation. The time window, cap, and route beyond it remain visible.

**Buildable basis**

F10 is already an approved plan item with a bounded-window, non-streaming implementation. G5 supplies an optional bot-post fold. Existing per-tab filters are F1. The direction does not depend on G2, G4, G9, or G13.

**Honest failure mode**

On a high-volume account, “200 posts” is not a calm experience and may not represent the full window. Start with the cap and grouping, do not add an algorithm to conceal the problem, and let the user narrow the view with the filters they already control.

### 2. The Caption Track — an image’s description is part of the post

**What the user sees**

- **Home / Local / Federated:** **Image descriptions** appears in the existing per-tab **Timeline view** controls, default off in the ordinary stream. When enabled, a present description appears in a full-width caption band immediately below its image. Two lines are visible with **“Read full description”** inline; the ALT badge is not duplicated on top of the image.
- **Catch up:** descriptions are on as part of that explicitly chosen reading mode. This avoids making the default Home feed twice as tall while giving the caption-first layout a place where it is the point, not an interruption.
- **Composer:** after metadata prefill, the editable **Image description** field is visible beneath the attachment rather than represented only by a green badge. The user can see and correct what will be published before tapping Post.
- **Thread:** descriptions use the same caption component and can expand without losing the reader's place.
- **Share as image:** the shared card has one canonical Mammut layout: post text, image, visible description, author, and server. There are no decorative themes to manage.

**Why it fits**

A reading client should not treat a substantial part of the post as a hidden accessibility annotation. The same editorial care runs through reading, writing, and sharing, so this is a behavior of the app rather than a timeline checkbox.

**What it deliberately does not do**

- No automatic alt-text generation or auto-translation. The author's words remain the author's words.
- No red “missing ALT” badge on other people's posts and no scolding the person reading them.
- No full-screen modal for routine description reading.
- No sticker, background, font, or layout workshop in the share sheet.

**Buildable basis**

G14, G7, and G8 are all **go**. The timeline change is `MediaGridStatusDisplayItem` plus a `TimelineFilter`; metadata prefill uses `ExifInterface`, which is already available. The share card is a Bitmap/Canvas draw operation. No new dependency is required.

**Honest failure mode**

Long descriptions can still dominate a post. Clamp them at two lines, keep expansion inline, and never turn descriptions on silently in the ordinary timeline. Catch up is the explicit exception because its purpose is deliberate reading.

### 3. The Reading Room — a conversation can become a story

**What the user sees**

- **Thread** becomes **Conversation** in the app bar, with a factual subtitle such as **“14 posts · 5 people.”** The standard reverse-chronological reply view remains the default.
- When the opened post belongs to a connected same-author chain, the app bar offers **Unroll**. The reader follows the `in_reply_to_id` chain containing that post: the selected author's connected ancestors and descendants, in reading order.
- The unrolled view suppresses repeated author headers, numbers the posts unobtrusively, and uses a grape reading line to connect them. Side branches are not presented as part of the story. A small inline marker says **“2 replies to this post · Show in full conversation.”**
- **“All 14 posts”** remains visible in the app bar. The reader is always one tap from the complete conversation, and the subtitle explicitly says **“Unrolled · Mara’s 4 posts.”**
- The footer says **“End of Mara’s thread”** and, when relevant, **“The other 10 posts are side replies, not part of this story.”** The reply field remains available below the story.
- Mixed-author conversations are not forced into this shape. If there is no connected same-author chain, Unroll does not appear. Reply sorting stays in the standard Conversation view.

**Why it fits**

The current thread view optimizes for traversal. Mammut should also optimize for comprehension. A self-thread is closer to an article than to a stack of independent timeline rows, and the UI should admit that without turning every argument into a nested tree.

**What it deliberately does not do**

- No infinitely indented reply tree, engagement ranking, or “best replies.”
- No fabrication of missing links, no aggressive purging of older ancestors, and no silent filtering presented as the full conversation.
- No mass-block control in the serene reading surface. G15 belongs in the standard conversation's safety overflow, behind its participant preview and per-account block loop.
- No automatic unroll of long conversations. The reader chooses it.

**Buildable basis**

This is F12's existing thread-unroll item, built after F14 so the complete ancestor/descendant context is present. It reuses the existing context fetch, `ThreadFragment`, `StatusDisplayItem`, and reply line data. It requires no Compose rewrite and no new network service.

**Honest failure mode**

An unroll is an editorial view of a graph, not the graph itself. The persistent post counts, side-reply markers, and **All posts** escape are mandatory; otherwise a useful reading mode becomes misleading.

## What still reads as generic

Right now the app says **“Moshidon with a better mark and a warmer palette.”** It should say **“a reading instrument with its own idea of completion.”** The gap is not more decoration. It is that the main surfaces still inherit the same hierarchy and language as the upstream client.

| Surface | What it currently says | The smallest meaningful change |
|---|---|---|
| **Home** | The FAB and per-tab subtitle are distinctive, but **Timeline view** still opens a settings-style collection of checkboxes. The body remains a standard endless status list. | Rename the action **View filters**, keep the existing sheet, and put **Catch up** beside it. In Catch up, turn the active rules into the one-line scope of the edition. Keep the ordinary Home subtitle, but write **“Boosts off · replies off · descriptions on”** instead of compressed settings shorthand. |
| **Settings** | **App Settings → Behavior / Display** is an upstream taxonomy. Display mixes text, media, filters, CWs, marquee text, divider lines, pronouns, and pagination in one long list. | Reorder the existing Java `ListItem`s into **Reading**, **Appearance**, and **Interaction**. Reading owns pagination, CWs, long posts, bot collapsing, image descriptions, and counters. Appearance owns theme, palette, type, links, dividers, media shape, pronouns, and other purely visual choices. Interaction owns confirmations, reply behavior, haptics, swipes, and custom tabs. Keep server-owned preferences under the account. F5's settings search is useful later, but search is not a substitute for this taxonomy. |
| **Composer** | A large identity header, a visibility pill, an empty text field, a 36dp icon strip, and a bare number. It is a competent generic posting form. Media descriptions disappear behind a thumbnail badge. | Let the text begin higher. Reduce identity to a small account chip; put audience and quote policy in one plain line such as **“Quiet public · anyone can quote.”** Show **Image description** directly under an attachment when present. Label the counter **“500 characters left”** and reveal it only when the limit becomes relevant. Keep the single-post composer; do not turn it into a publishing suite. |
| **Thread** | **Thread / From Home**, a larger root, flat descendant rows, reply lines, and a generic reply bar. It is a timeline that happens to be vertical. | Use **Conversation** and factual post/participant counts in the standard view. Add the bounded Unroll reader above. Keep branch navigation and reply sorting in the standard view; do not make the reader carry every moderation and sorting control. |
| **Profile** | A 155dp banner, avatar overlap, follow button, post/follower counts, **Featured / Timeline / About**, and another row of filter chips. It is the standard social CV. | Reduce the banner to 112dp and let the bio and recent writing lead. Keep the existing tabs, but put the filter chips into **View filters** so they do not occupy two rows. Replace the own-profile **“1,587 posts”** subtitle with the server name; post volume is not the person's identity. Move QR and safety actions into overflow. If Collections ever ship, a read-only **“Included in collections”** block belongs inside About, not in a new tab. |

The launcher mark, palette, and action icons are good craft. They are not yet a product direction. The FAB is the one current behavior with real recognizability; keep it visible and stop hiding it during scroll.

## Voice and craft

### Voice: calm, exact, and on the reader's side

Use ordinary labels: **Catch up**, **View filters**, **Image description**, **Unroll**, **Full conversation**. The concept names in this document are for the team, not copy to plaster across the UI. Do not make the user learn “editions,” “lenses,” or “rooms” to operate the app.

The current copy often sounds like a framework default, a server exception, or a motivational coach. Replace all three with a factual statement and one useful action.

| Where | Current sound | Mammut copy |
|---|---|---|
| Empty timeline | **“This list is empty.”** | **“Nothing new in the last 24 hours.”** Action: **“Load older posts.”** |
| Filtered timeline | **“Everything on this timeline is hidden by your view filters.”** | **“No posts match this view.”** Action: **“Change view filters.”** Do not claim everything is hidden when only part of the timeline was loaded. |
| Search | **“Nothing found”** | **“No results for ‘{query}’.”** Supporting line: **“Try fewer words or remove a filter.”** |
| Network failure | **“Couldn’t reach the server. Check your connection and try again?”** | **“Can’t reach {instance}.”** Supporting line: **“Your place in this timeline is unchanged.”** Action: **“Try again.”** |
| Post failure | **“Failed to send post”** plus a raw error | **“Couldn’t publish this post.”** Supporting line: **“Your text is still in the composer. Check your connection, then try again.”** This promises no outbox or recovery after leaving the screen. |
| Missing post | **“It may have been deleted, or it might have never existed in the first place.”** | **“This post isn’t available.”** Supporting line: **“It may have been deleted, or your server can’t fetch it.”** |
| Composer hint | **“Type or paste what’s on your mind”** | **“Write a post”**; for a reply, **“Add something to the conversation.”** |
| First interaction with a stranger | **“Hello, new connection!” / “Let’s make a great first impression” / “Embrace kindness”** | **“You haven’t interacted with @{name} before.”** Keep **Reply** and **Not now**. Context, not etiquette coaching. |
| Old post | **“You’re replying to an old post” / “It might not be relevant anymore”** | **“This post is 5 months old.”** Let the reader decide whether the reply is relevant. |

### Motion

- Use 180–240ms transitions for the FAB sheet and ordinary screen changes. The current 700ms search morph is a demonstration, not navigation; reduce it to roughly 240ms.
- Do not animate a celebration when somebody finishes reading. The leaf rule and final sentence are enough.
- Preserve scroll position when descriptions expand, replies sort, or a view changes. That stability matters more than another transition.
- Respect reduced motion by replacing shared-axis movement and rotation with short fades. Never animate toolbar text to solve truncation.

### Color

Give each palette color a job. **Coral** is the primary action and compose. **Sky** marks context, links, and descriptions. **Leaf** marks completion and successful states. **Grape** carries quiet structure: reading lines, secondary text, and selected neutral surfaces. Do not spray all four across every screen to prove the brand exists.

### Emptiness and errors

An empty state should say which view is empty, in what time window, under which filters. An error should name the operation and the server, preserve whatever is actually preserved, and offer the next action. Never promise “saved,” “queued,” or “undo” unless the underlying implementation makes that true.

## What to refuse

1. **Refuse G1's thread composer.** It moves the center of gravity toward publishing, inherits a difficult abort/rollback contract, and competes with clients already built for power composition. Do not build it under this direction. Mammut's thread work should help people read threads instead.
2. **Refuse G3 Collections CRUD and a Collections destination.** Curator onboarding and collection management create a different product. A read-only **Included in collections** line on a profile is compatible with the reading position if it stays quiet and collapses when empty.
3. **Refuse G5's grayscale treatment.** It makes photographs and artwork worse to look at in order to train the user. The bounded catch-up and bot fold provide the same stopping support without punitive design.
4. **Refuse decorative customization in G8.** One well-composed, caption-preserving share card is recognizable. A background/font/sticker editor is generic feature weight.
5. **Refuse a full G11 query workbench.** Search operator chips are useful for finding something to read; a power-user query builder is not an identity. Keep a small set of plain-language chips and show the resulting query as an optional detail.
6. **Do not market G10/G15 as “safety theater.”** Build them as small, competent boundary controls where they belong. Mammut should feel calm partly because the boundaries work, not because it displays shields and warnings.
7. **Keep the review's four engineering traps closed.** G2 undo-send, G4 auto-reply translation, G9 native Pleroma quote posts, and G13's high-volume home streaming switch would add fragility, not distinction. This direction does not need to reopen any of them.

**Cut first from the current app: marquee toolbar titles.** They add motion to a space that should be still, make a title harder to read, and solve a layout problem by keeping it moving. Truncate with an accessible full title on the destination screen.

## The first proof

Build the smallest vertical slice of the Last Page: **Catch up in Home overflow → existing 24-hour/200-post fetch → existing view filters → author grouping → honest leaf-colored end footer.** Leave grayscale, sorting experiments, scheduling, and decorative celebration out.

That slice should feel recognizably different with ordinary avatars and placeholder media. If it does not, more theming will not rescue the direction. The next two slices then deepen the same promise: descriptions join the catch-up reading flow, and Conversation gains Unroll.

## Mockups

Each file is self-contained HTML/CSS at **360×780**, with no external assets or dependencies. They were rendered with headless Chromium at that size; the unrolled view was revised so its end marker remains fully visible above the reply bar.

| Signature move | HTML |
|---|---|
| The Last Page | `/tmp/mammut-creative/signature-1-finite-catch-up.html` |
| The Caption Track | `/tmp/mammut-creative/signature-2-image-descriptions.html` |
| The Reading Room | `/tmp/mammut-creative/signature-3-unrolled-conversation.html` |

Rendered checks are beside them as `.png` files with the same basenames.
