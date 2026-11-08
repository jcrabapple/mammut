# Composer 2.0 — spec

Status: decision-complete. Three slices, one data model. Build in order; each slice ships on its own.
`CreateStatus.Request` already carries `inReplyToId` and `scheduledAt`, so this is UI and persistence
work, not API archaeology.

## 0. The shape of it

A post stops being "one text box that evaporates". It becomes a small document: one or more segments,
each with its own text, content warning, media and descriptions, saved locally as you type, publishable
now or later.

Three slices:

1. **Drafts** — a durable local store, autosave, a drafts screen, resume.
2. **Threads** — the composer holds several segments and publishes them as a chain.
3. **Scheduling** — a schedule action, a scheduled-posts list fed by the server, cancel.

Slice 1 establishes the model that 2 and 3 consume. Do not build a single-post draft model and widen it
later; that is the rework this spec exists to prevent.

## 1. Data model

```java
ComposeDraft {
  String id;              // UUID, also the directory name
  long updatedAt;         // epoch millis, for ordering
  String replyToStatusId; // nullable: the post this draft replies to
  StatusPrivacy visibility;
  List<Segment> segments;
}

Segment {
  String text;
  String spoilerText;     // nullable
  boolean sensitive;
  List<DraftAttachment> attachments;
  PollDraft poll;         // nullable, first segment only
}

DraftAttachment {
  String filePath;        // app-private copy, see §2
  String mimeType;
  String description;     // Caption Track text, may be empty
  String focus;           // "-1,-1" when unset
}
```

Polls keep the composer's existing shape (options, expiry, multiple, hide totals). Visibility applies to
the whole draft; per-segment visibility is not a thing and must not appear in the UI.

## 2. Storage

- Root: `getFilesDir()/drafts/<accountID>/<draftId>/`, containing `draft.json` and the media copies.
- **Media is copied in, never referenced.** A draft that holds a `content://` URI will one day open to a
  deleted file, and the failure is silent. Copy on add/edit, name files `media-<n>.<ext>`, keep them
  alongside `draft.json`.
- Write `draft.json` atomically: temp file then rename.
- Drafts are account-scoped. Switching accounts must not show the other account's drafts.
- Delete the directory only after a successful publish or an explicit discard.

## 3. Slice 1: drafts

**Autosave.** On `onPause` and on back, if any segment has text, media or a poll, write the draft. The
composer holds a `draftId` for its lifetime, so a second autosave updates the same draft instead of
creating a second one. Empty composer writes nothing.

**Resume.** Opening a draft restores text, CW, sensitivity, visibility, media with descriptions, and the
poll. Restoring media must not re-run metadata prefill: a restored description is the author's text, not
a suggestion.

**Drafts screen.** One new FAB menu row, **"Drafts"**, placed directly under Compose. It opens a screen
with two tabs, **Drafts** and **Scheduled** (the second is empty until slice 3).

Draft row: first ~120 characters of the first segment as the title (single line, ellipsised), then a
factual subtitle — `3 posts · 2 images · edited 2 hours ago`. Overflow: **Delete**. Tap: resume in the
composer. Empty state: "No drafts. Anything you start writing is saved here."

**Discard.** The composer's existing discard confirmation gains a third outcome when there is content:
**Save draft**. Choosing it writes the draft and leaves.

**Editing an existing post** never touches drafts and never autosaves.

## 4. Slice 2: threads

**Structure.** The composer renders a vertical list of numbered segments, each a card with its own text
field, counter, CW toggle and media strip. Below the list: **"+ Add another post"**. A single-post compose
is a thread of one, so there is no mode switch and no separate screen.

**Rules**

- Letters and media count per segment against the instance's own limits, same as today.
- A poll is allowed on the first segment only; adding one disables media on that segment as today, and
  the other segments stay plain.
- Visibility, and the reply/quote policy, apply to the whole thread. They appear once, above the
  segments.
- Reordering: each segment's overflow offers **Move up** and **Move down**. Drag-and-drop is out of scope.
- Deleting: a segment can be removed unless it is the only one.
- Editing an existing post is always a single segment; the add button is hidden there.

**Publishing.** Publish in order, setting `inReplyToId` to the id the server returned for the previous
segment. Show progress as "Posting 2 of 4".

**Failure.** If segment *k* fails: keep segments *k*..*n* as a new draft, keep the already-published
prefix alone, and say so plainly — "Posted the first 2. Saved the rest as a draft." Never retry blindly;
a retry can double-post. The published prefix is never edited or deleted automatically.

**Idempotency.** `CreateStatus` already sends an `Idempotency-Key`; one key per segment, generated once
and reused if the user retries that segment from the draft.

## 5. Slice 3: scheduling

**Action.** A new **Schedule** action in the composer toolbar. Tapping it opens a date picker then a time
picker. Default: one hour from now, rounded up to the next five minutes. Minimum: five minutes ahead.
Beyond a year is refused with a message, not silently clamped.

**Scope.** Scheduling applies to a single-segment post. With a thread of two or more, the action is
disabled and explains why: a scheduled thread cannot be chained, because each segment needs the id the
previous one gets at publish time.

**Result.** On success: snackbar **"Scheduled for Sat 9:00 PM"** with a **View** action that opens the
Scheduled tab. The composer closes and the draft is deleted — the server now owns that text.

**Scheduled tab.** Rows from `GET /api/v1/scheduled_statuses`, newest scheduled time first, showing the
scheduled time (absolute, with relative in parentheses), text preview, and media count. Overflow:
**Cancel** with a confirmation, calling `DELETE /api/v1/scheduled_statuses/:id`. Cancelling deletes the
post on the server; the text is not recovered locally, and the confirmation says so.

Paginate when the server returns a `Link: next`. Show the server's own timezone-free ISO time as-is
relative to the device clock; do not attempt to convert the account's timezone.

## 6. Prohibitions

- No silent loss. A draft disappears only after a successful publish, a successful schedule, or an
  explicit discard the user confirmed.
- No automatic reposting of anything. A failed thread stays failed until the user acts.
- No draft that references media outside the app's own storage.
- No second drafts implementation inside the composer; the screen and the composer share one store.
- No per-segment visibility or per-segment CW policy beyond the segment's own spoiler text.

## 7. Acceptance criteria

**Slice 1**

1. Type "hello", leave the composer via back, choose Save draft; the draft appears in the Drafts tab with
   the text intact, and reopening restores it.
2. Attach two images with descriptions, save, restart the app, resume: both images are present with their
   descriptions, and no metadata prefill fires on the restored ones.
3. Delete a draft; its media directory is gone.
4. Drafts are invisible to a second account on the same device.
5. An empty composer writes no draft.

**Slice 2**

6. Compose three segments, publish: three statuses appear, each replying to the previous, in order.
7. Kill the network after segment 2 succeeds: the composer reports "Posted the first 2", a draft holding
   segment 3 exists, and no duplicate of segment 1 or 2 is created by retrying.
8. Reorder segments with Move up/Move down and publish; the server order matches the screen.
9. Editing an existing post shows no add-segment control.

**Slice 3**

10. Schedule a post 10 minutes out; it appears in the Scheduled tab, and the draft is gone from Drafts.
11. Cancelling from the Scheduled tab removes it from the server list after refresh.
12. A thread of two disables Schedule with an explanatory message.
13. Choosing a time two minutes ahead is refused with a message, not silently moved.

**All slices**: `assembleDebug` green; timelines, notifications, Reading Room and Catch up unchanged.

## 8. Notes for the implementer

- `ComposeFragment` is the single composer; extend it rather than forking a second one. `editingStatus`
  stays the edit path and must not gain thread or draft behaviour.
- The FAB menu rows are built in `ui/NavMenuController` — add "Drafts" there, once, not in each
  destination.
- Media upload is `UploadAttachment`; publish is `CreateStatus`. Reuse both per segment rather than
  writing a batch path.
- Keep the draft store in its own class with a synchronous API (`save`, `load`, `list`, `delete`) so the
  composer never has to know where drafts live.
- Mark additions with `MAMMUT:` comments, and prefer boring synchronous file IO on a background thread
  over an abstraction layer.
