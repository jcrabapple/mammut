# Search operator chips — spec

Status: decision-complete. G11 in `docs/research/fediverse-feature-survey-2.md`.

## 1. What the reader gets

Search stops requiring memorised syntax. A chip row under the search field toggles the operators that
cover most real searches, and shows which ones are active in the current query. The syntax keeps
working — chips write ordinary text into the field, they do not introduce a query language of their own.

## 2. Ground truth (Mastodon 4.7, verified against the server's own parser)

The server supports these prefixes: `has`, `is`, `language`, `from`, `before`, `after`, `during`, `in`.
Property values (`has:` and `is:` both map to the same filter; the split is convention only):
`image`, `video`, `audio`, `media`, `poll`, `link`, `embed`, `sensitive`, `reply`, `quote`.

Quoting with `"…"`, negation with `-`, and `+` also work.

**A query with filters but no keyword is rejected** ("At least one keyword or phrase is required"),
with two exceptions: `in:library`, and `from:me`/`from:<self>`.

## 3. Where the chips live

Directly under the toolbar search field in the search screen, above the suggestion list. One
horizontally scrolling row, no wrapping, 8dp gaps, M3 assist chips with the app's existing colours.
The row scrolls away with the suggestions list; it is not pinned.

The row is hidden while the query field is empty **and** the suggestion list is showing recents — a
chip row above "Recent searches" is noise. It appears as soon as the field has content.

## 4. The chips (v1)

In this order: **Media** (`has:media`), **Links** (`has:link`), **Polls** (`has:poll`), **Replies**
(`is:reply`), **Sensitive** (`is:sensitive`), **My posts** (`from:me`), **My library** (`in:library`),
then a trailing **?** chip that opens the syntax reference (§7).

## 5. Behaviour

- **On state** is derived from the query text, never stored separately: a chip is on when the query
  contains its exact `prefix:value` token at a word boundary. Hand-edit the query and the chips follow;
  delete the token and the chip goes off.
- **Tap to add**: append the token, space-separated, at the end of the query, then run the search.
- **Tap to remove**: remove exactly that token, collapse the doubled space it leaves, then run the search.
- The rest of the query is never touched: order, quoting and the user's own `-` or `+` prefixes survive
  both operations.
- **Keyword rule** (§2): a chip tapped on an empty query, or on a query that has no keyword, does not
  fire a doomed request. It adds the token and shows an inline hint under the field:
  **"Add a word to search with filters."** The exception chips behave normally: **My library** and
  **My posts** are valid queries on their own and search immediately.
- Chips do not clear results when toggled off; they re-run the search with the new query, which is what
  the keyboard submit would have done.
- Typing stays fast: parsing the query to compute chip states happens on the text-change callback and
  must not touch the network.

## 6. Scope

**In:** the eight chips above, on/off derived from text, the keyword hint, the help chip.

**Out of v1:** `language:` and date chips (`before:`/`after:`/`during:`) because they need pickers;
`from:` for a specific account; negation chips; any chip that rewrites or reorders what the user typed.
All of those are listed in the help sheet so they remain discoverable.

## 7. Help sheet

The **?** chip opens a dialog listing the full syntax, grouped as **Filters** (`has:`/`is:` with the
property list), **People** (`from:me`, `from:@user@server`), **Time** (`before:`, `after:`, `during:`
with `YYYY-MM-DD`), **Language** (`language:en`), **Scope** (`in:library`, `in:public`), and **Syntax**
(quotes for phrases, `-` to exclude, and the keyword rule from §2). Each line shows the literal syntax,
not a description of it. One **Copy** action copies the whole reference as text.

## 8. Prohibitions

- No second query syntax. Chips produce plain text the server already understands.
- No hidden state: if the query says `has:media`, the chip reads as on, whatever route put it there.
- No rewriting of the user's query beyond adding or removing the one token the user tapped.
- No search fired that the server is guaranteed to reject, except where the hint explains why.

## 9. Acceptance criteria

1. Tap **Media** with "bicycle" in the field: query becomes `bicycle has:media`, the chip reads on, and
   results load.
2. Tap **Media** again: the query returns to `bicycle` and the chip reads off.
3. Type `has:link` by hand: the **Links** chip reads on without any chip being tapped.
4. Tap **Media** with an empty field: token is added, no request is sent, and the hint "Add a word to
   search with filters." appears.
5. Tap **My library** with an empty field: search runs immediately.
6. `"red bicycle" -has:poll has:media` edited by hand: toggling **Media** off leaves
   `"red bicycle" -has:poll` untouched, quoting and negation intact.
7. The **?** sheet lists every operator in §7 with literal syntax and copies to the clipboard.
8. `assembleDebug` green; the timeline, composer, drafts, scheduled posts, Catch up and Reading Room are
   unaffected.

## 10. Notes for the implementer

- The search screen is `SearchQueryFragment` with its `SearchViewHelper`; the query already flows through
  one submit path and the chips must reuse it rather than calling `GetSearchResults` directly.
- Token matching needs word boundaries so `has:media` does not light up for `-has:media` or for
  `foo has:mediax`. Decide the negation case explicitly and write it down: a `-` prefixed token is a
  different filter and must not show the positive chip as on.
- Keep the chip row a plain horizontal `RecyclerView` or `HorizontalScrollView` with buttons; a
  `ChipGroup` that reflows is the wrong control for a row that must not change height.
