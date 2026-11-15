# Mammut 1.3.5 full-app audit — triage

Blind read-only audit of `2d595665f` (v1.3.5) across five kanban lanes on board `mammut`.
Baseline: 128/128 JVM unit tests green. Every MUST-FIX and security MEDIUM below was
verified against the cited source by the orchestrator before acceptance. Review cards:
R1 `t_2576bc3e`, R2 `t_8e3ed679`, R3 `t_a39fcbb7`, R4 `t_b49b78bc`, S1 `t_9b9cc617`.

Totals: 21 MUST-FIX (R1 4, R2 4, R3 5, R4 8) + 4 security MEDIUM + 23 SHOULD-FIX + 4 NICE-TO-HAVE
+ 3 security LOW. Dedupes applied (see map). Improvement opportunities are separated at the
bottom for product decision — they are not part of the bug-fix wave.

## Dedupe map

- **Settings import crash** — R4 F8 == S1.3 (same `SettingsAboutAppFragment` + `GlobalUserPreferences.load`). One fix.
- **Share activity hardening** — R1 F4 (EXTRA_STREAM loss) + S1.1 (file:// confused-deputy) + S1 hardening #6 (null ClipData) all land in `ExternalShareActivity`. One fix.
- **Export streams** — R4 F11 == S1 hardening #5. One fix.
- **Thread unread badge** — R3 F10 lives in `ThreadFragment`, which R4 F7 (Unroll) also touches. Owned by the navigation card; the unread subscription is specified there.

## Fix cards (serial chain — one coder lane at a time; shared repo)

### Fix 1 — Upload request bodies & staging
Owns: `api/ResizedImageRequestBody`, `api/AvatarResizedImageRequestBody`,
`api/requests/accounts/UpdateAccountCredentials`, `api/UploadStaging`, `api/UploadSource`,
`api/requests/statuses/UploadAttachment`.
- MUST R2 F1: stage avatar/header through the verified local-file pipeline; repeatable disk body even when no resize (NULL provider size, null/empty cursor, expired grant).
- MUST R2 F2: fix `"image/png".equals(MediaType)` (always false) → compare type/subtype; preserve PNG alpha + MIME + filename.
- MUST R2 F3: resized body repeatable for the whole request lifetime; explicit terminal cleanup on success/error/cancel/constructor-failure (no first-write deletion).
- NICE R2 F10: one shared transform for all 8 EXIF orientations on the legacy path.
- NICE R2 F11: failure cleanup wraps the whole post-copy staging operation (zero-byte / failed-JPEG temp leak).
Tests: NULL-size 400×400 avatar uploads nonzero with contentLength==bytes, repeatable after grant revoke; transparent oversized PNG keeps PNG magic + alpha; retry-after-retryable-failure does two identical nonempty writes then cleans up; cancel-before-write leaves no temp; empty provider stream leaves no `mastodon_upload_*`.

### Fix 2 — Compose media lifecycle & request cancellation
Owns: `ui/viewcontrollers/ComposeMediaViewController`, `api/MastodonAPIRequest`, `api/MastodonAPIController`.
Depends on Fix 1 (staging API).
- MUST R2 F4: defer queue advancement + polling while the retained composer is detached; drive from a reattachment/resume hook; guard stale callbacks (NPE at :360).
- SHOULD R2 F5: prefer stored `attachment.mimeType` over a fresh resolver query (file:// returns null → maxSize 0 → oversized original sent).
- SHOULD R2 F6: on parcel restore, requeue UPLOADING / recreate PROCESSING polling or move to retryable ERROR (no stuck publish block).
- SHOULD R2 F7: evaluate HEIC/HEIF preflight by effective output MIME (image/jpeg) so transcoding servers accept it.
- SHOULD R2 F9: make `canceled` safely visible; recheck under the request monitor immediately before enqueue (cancel/enqueue race orphaning attachments).
Tests: detach between response and callback with two attachments → no crash, one queued upload after reattach; large JPEG draft from file:// reaches matrix limit; parcel round-trip of in-flight states resumes or becomes retryable; HEIC accepted on JPEG-only allowlist; cancel during body construction → zero HTTP calls.

### Fix 3 — Composer text, drafts, share intents
Owns: `fragments/ComposeFragment`, `drafts/ComposeDraft`, `ExternalShareActivity`,
`ui/viewcontrollers/ComposePollViewController`, `ui/text/LengthLimitHighlighter`,
`ui/views/FloatingHintEditTextLayout`, `fragments/drafts/DraftsListFragment`, scheduled page fragment.
Depends on Fix 2.
- MUST R1 F1: persist + restore reply/quote/thread-anchor ids and the first-segment idempotency key across process death (or reload draft by id and merge).
- MUST R1 F2: fingerprint the full request payload per key; reuse only for identical retry, mint new on any text/CW/poll/media/anchor/visibility/language/quote/order change.
- MUST R1 F3: body highlight from body-only count with weighted→raw offset mapping; separate CW highlighter; never pass combined count into a body range (negative span crash).
- MUST R1 F4 + S1.1: read `EXTRA_STREAM` ArrayList first (API-33 typed overload), fall back to ClipData, filter null URIs; reject `file:` at the external boundary (content: only).
- SHOULD R1 F5: publish resumed quote via `restoredQuotedStatusId` when `quotedStatus==null`.
- SHOULD R1 F6: persist language + quote policy in ComposeDraft and saved state.
- SHOULD R1 F7: `hasDraft()` includes main CW state.
- SHOULD R1 F8: CW grapheme count (not UTF-16).
- SHOULD R1 F9: one poll validity method (trimmed, unique, grapheme max) for publish + schedule + request.
- SHOULD R1 F10: refresh hidden Scheduled tab on show / create event.
- NICE R1 F11: apply bundled typeface to programmatic CW label/error + sending overlay.
Tests: recreate resumed reply + partial tail keeps anchor/key; edited/reordered payload uses fresh key, identical process-death retry keeps it; empty body + limit+1 CW no exception; EXTRA_STREAM-only multi-share keeps attachments, file:// rejected; resumed quote id retained; CW-only draft saves; astral-emoji CW boundary; poll duplicates/blank/51-grapheme rejected; hidden Scheduled refresh.

### Fix 4 — Notifications, push, unread store
Owns: `model/PushNotification`, `PushNotificationReceiver`, `UnifiedPushNotificationReceiver`,
`api/PushSubscriptionManager`, `ui/utils/UnreadNotificationStore`, `ui/utils/UnreadNotificationCounter`,
`ui/utils/NavToolbarUtils`, `fragments/NotificationsListFragment`, `fragments/BaseNotificationsListFragment`,
`ui/displayitems/FooterStatusDisplayItem`, `api/StatusInteractionController`, `model/Notification`,
`model/NotificationType`, `api/CacheController`, `api/session/AccountSession`, `utils/UnifiedPushHelper`.
Depends on Fix 3.
- MUST R3 F1: set `pn.notificationId` in `fromNotification()` (PendingIntent collision).
- MUST R3 F2: add quote/quoted_update/severed_relationships/moderation_warning push types; null-guard the type switch + account access in `fromNotification`; account-less system title/body; admin types in NotificationType or null-guard.
- MUST R3 F3: key unread state + push/read ops by account with a generation guard; reject stale-account responses.
- MUST R3 F4: handle `StatusCountersUpdatedEvent` in BaseNotificationsListFragment; rebind matching footers (error rollback).
- MUST R3 F5: null-guard account postprocess + map system types to empty account lists in V1 conversion.
- SHOULD R3 F6: UnifiedPush pause guard. F7: V1 marker fallback completes the count. F8: count by `compare(id,marker)>0` (deleted marker). F9: bell long-press refresh after save instead of forcing zero. F11: menu mark-all server-success-only via shared helper. F12: push boost eligibility matches footer.
- LOW S1.7: redact push endpoint capability URLs + full handles from logs (debug gate).
Tests: distinct stable PendingIntent keys per fallback notification; parameterized push-type matrix (all types + admin + unknown) through FCM + UP without throwing; interleaved A/B refresh/switch/push/long-press isolation; failed action rolls back tint+count without rebind; V1 severed/moderation fixtures parse; pause blocks UP; empty/failed marker publishes; deleted-marker count; push-between-fetch-and-save keeps badge 1; menu marker failure leaves state; private-mention boost eligibility.

### Fix 5 — Navigation, Catch up, Reading Room
Owns: `fragments/HomeFragment`, `fragments/CatchUpFragment`, `fragments/ThreadFragment`,
`ui/views/NavMenuView`, `fragments/HomeTabFragment`, `ui/utils/UiUtils` (palette fallback),
`ui/utils/ConversationChain`.
Depends on Fix 4 (ThreadFragment/HomeFragment unread overlap).
- MUST R4 F1: restore Home child fragments before onCreateView use; register page listener once (recreation NPE).
- MUST R4 F3: compute shownCount before formatting scope.
- MUST R4 F4: separate network pagination cursor from oldest displayed; capture raw oldest before filtering (repeating page).
- MUST R4 F5: do not advance catchUpLastAt for a CAP-truncated fill.
- MUST R4 F6: drop the "all posts this server has" completeness claim; state factual fetched originals.
- MUST R4 F7: call updateUnrollBackCallback after automatic Unroll exit; one transition path (stale Back callback).
- SHOULD R4 F9: reject future catchUpLastAt (use 24h floor). F10: hide inert filtered-empty Load older. F12: external compose uses global palette fallback + account override. F14: Catch up rule line noninteractive.
- SHOULD R3 F10: subscribe ThreadFragment to its account unread store (live badge).
Tests: recreate Home on Home/Local/Notifications no NPE + one callback; cap fill scope names nonzero count immediately; hidden-trailing-page extension advances cursor; capped fill leaves marker + remainder reachable; server-end mixed set footer honest; auto Unroll exit then Back leaves Conversation; future marker keeps recent posts; thread badge updates on push/mark-read.

### Fix 6 — Settings import/export, preferences, security hardening
Owns: `fragments/settings/SettingsAboutAppFragment`, `GlobalUserPreferences`,
`api/session/AccountLocalPreferences`, `fragments/settings/SettingsDisplayFragment`,
`AndroidManifest.xml`, `res/xml/network_security_config.xml`, `PanicResponderActivity`,
`fragments/settings/SettingsServerAboutFragment`, `assets/server_about_template.htm`.
Depends on Fix 5.
- MUST R4 F2 + S1.3: preserve int-vs-long types on import; versioned schema allowlist + type/enum/range validation before any write; atomic transaction; cap input size; safe fallback enum/index reads in load(); malformed → failure toast (persistent crash loop).
- MUST S1.2: panic responder trusted-package allowlist + caller UID/signature check (preserve legit panic-app compatibility).
- MUST S1.4: remove user CAs from release base trust anchors; move to debug-overrides.
- SHOULD R4 F11 + S1 #5: try-with-resources export streams; delete cache export file; null provider stream → failure.
- SHOULD R4 F13: remove or wire stale showNavigationLabels / doubleTapToSearch switches.
- LOW S1.5: Server-about WebView — sanitize HTML, disable JS, scheme allowlist (https/http/mailto only).
- LOW S1.6: backup rules excluding accounts.db + credential stores (dataExtractionRules).
- HARDENING S1 #3: key exported account prefs by domain_id not numeric self.id.
Tests: export/import long prefs > Integer.MAX_VALUE round-trip exact, no ClassCastException; malformed/array/wrong-type/unknown-enum/out-of-range import leaves prefs unchanged + no crash; panic trigger from untrusted package ignored; release config has no user trust anchor; export closes streams + complete document; WebView non-allowlisted scheme not dispatched.

## Re-review

Two blind re-review cards gated on the fixes: RR-A on Fix 1-3 (uploads + composer), RR-B on
Fix 4-6 (notifications + navigation + settings/security). Each judges the result against this
doc and the original findings, with a required failing test per accepted finding. Orchestrator
verifies the merged diff + full suite + red-checks before any release.

## Improvement opportunities (product decision — NOT in the bug-fix wave)

Composer (R1): edit scheduled posts from a row (M); complete localized schedule times (S);
autocomplete on extra thread segments (M); honor per-instance URL weighting (S); debounced
edit-time autosave (M).
Uploads/media (R2): age-based startup temp janitor (S); move staging/resizing off the API
worker (M); preflight byte limits as well as matrix limits (M); explicit HEIC sRGB conversion
(S); bound stale share-render work (M).
Notifications (R3): shared notification fixture matrix (M); reconcile group summaries on
dismissal (M); replace undecryptable-UP fetch-newest heuristic with standard Web Push (L).
Navigation/settings (R4): extract Catch up pagination/marker state machine (M); pure
ConversationChain test matrix (S); versioned typed export allowlist (M); sync CATCH-UP-SPEC
with shipped posts-only behavior (S); remove duplicate onConversationChanged call (S).
Security (S1): OAuth state + PKCE (M); host-meta XXE/size hardening (S); share-as-image respect
CW/filter-hidden state (S); redact debug request logging (S); Keystore-wrapped credential
encryption at rest (M).