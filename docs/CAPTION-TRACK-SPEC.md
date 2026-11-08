# The Caption Track: composer half — spec

Status: decision-complete. Companion to `docs/CREATIVE-DIRECTION.md` §2. G7 in
`docs/research/fediverse-feature-survey-2.md`.
The reading half of this move already shipped (G14 inline descriptions in Catch up). This is the
writing half: descriptions stop being a modal chore and become part of writing the post.

## 1. What the reader gets

Two changes in the composer, one small, one structural:

1. **Prefill.** When an image is attached and it carries a description in its own metadata, that
   text fills the description field as a draft the author can edit.
2. **Inline editing.** Every attached image gets a visible, editable description field beneath the
   media strip. The ALT badge stops being the only way to write one.

## 2. Metadata prefill (G7)

**When:** an attachment is added to the composer, it is an image, and the description for that
attachment is currently empty.

**What counts as metadata**, in this order, first non-empty wins:

1. EXIF `ImageDescription` (tag 270).
2. XMP `dc:description` — `rdf:Alt` items, preferring `x-default`, then `en`.
3. IPTC `Caption-Abstract` (present as XMP `photoshop:Headline`/`Iptc4xmpCore` caption where the
   library exposes it).
4. Windows `XPTitle` / `XPSubject` as a last resort.

**Rules**

- Read through `androidx.exifinterface:exifinterface` (add the dependency; it also parses XMP).
- Read **off the main thread**; the composer must not stall on a large JPEG.
- Local content URIs only. Remote or already-uploaded attachments are never read.
- Never overwrite a non-empty description, and never overwrite text the author typed while the read
  was in flight. If the field has been touched, the prefill is dropped silently.
- Sanitize with the composer's existing `sanitizeMediaDescription`, collapse newlines to single
  spaces, trim, and truncate to 1500 characters on a word boundary.
- The prefill is a draft. It is never published, sent, or claimed as anything other than text that
  happened to be in the file.

## 3. Inline description fields

**Where:** a block directly beneath the media strip in the composer, above the toolbar. One row per
image attachment, in attachment order.

**Each row:** a 32dp thumbnail of that image, the label **"Image 1"**, and a multiline editable
field. The field shows two lines at rest and grows to four, then scrolls. Placeholder:
**"Describe this image"**. An empty field carries a quiet helper line: **"Describe what matters in
this image."**

**Limit:** 1500 characters, matching Mastodon's description limit. A counter appears only in the
last 200 characters, reading "1400/1500". Paste beyond the limit is truncated, not rejected.

**The ALT badge stays on the tile** but changes job: it becomes a status chip — filled when a
description exists, outlined when it does not — and tapping it focuses that image's field. No
second editor. The existing ALT dialog is removed for images in the composer so the two editors
cannot disagree.

**Video and audio** keep their current description path; this slice is images only.

**Reminder behaviour is unchanged**: the pre-publish alt-text reminder still fires, still counts
attachments without descriptions, and still offers "Post anyway".

## 4. Layout and lifecycle

- The block is a sibling of the media strip inside the composer's media container, so it scrolls
  with the composer and disappears when there are no attachments.
- Rows are rebuilt when attachments change (add, remove, reorder). A row is identified by the
  attachment's id, so removing Image 2 does not shift the text of Image 3.
- Rotation and process death must preserve typed text: the fields write through to the attachment
  model on every text change, and the composer already saves that model.
- The block is hidden while a poll is attached, matching how the poll and media coexist today.

## 5. Accessibility

- Each field has a content description naming its image ("Description for image 2 of 3").
- The counter is announced with `accessibilityLiveRegion="polite"` only while it is visible.
- Tapping the ALT chip scrolls the field into view and requests focus, showing the keyboard.

## 6. Prohibitions

- No AI-generated descriptions, no network calls, no invented text. Only what the file says.
- No prefill into an attachment the author has already described.
- No automatic publishing of anything that was prefilled.
- No new dialog for editing descriptions; one editor per image.

## 7. Acceptance criteria

1. Attaching a JPEG whose EXIF `ImageDescription` reads "A red bicycle against a wall" fills that
   image's field with that text, and the ALT chip shows filled.
2. Attaching a JPEG with no metadata leaves the field empty with its placeholder; the chip shows
   outlined.
3. Typing a description updates the chip state immediately, and publishing sends that exact text as
   the media description.
4. Attaching three images shows three rows labelled Image 1–3, each bound to its own attachment;
   removing the middle one leaves the other two texts intact and correctly paired.
5. A description at 1490 characters plus a 40-character paste truncates at 1500 and shows the
   counter.
6. With a poll attached, the description block is hidden and no description is sent.
7. Rotation preserves typed text.
8. `assembleDebug` passes; no change to reading surfaces, timelines, or the notification path.

## 8. Notes for the implementer

- The composer's media state lives in `ComposeMediaViewController`; the strip is
  `ComposeMediaLayout`. Put the new block next to the strip, not inside a tile: tiles are sized for
  thumbnails and a per-tile field would fight the tile measurement for no gain.
- Description text is already the source of truth for uploads
  (`saveAltTextsBeforePublishing`); write through to the same field rather than introducing a
  parallel store.
- Prefix the metadata reader with `MAMMUT:` comments and keep it in its own class
  (`ui/utils/ImageDescriptionMetadata` or similar) so it can be read in one place and tested by eye.
