# Mammut parity status

Baseline: upstream `LucasGGamerM/moshidon` default `master` (legacy feature set) against
our `rewrite`-based fork. Branch: `feat/parity`. Every commit below builds locally
(`assembleDebug`, `assembleGithubRelease` with R8) and passes CI.

## Ported

### Display / behavior preferences
| Preference | Where it now works |
|---|---|
| enhanceTextSize, toolbarMarquee, underlinedLinks, likeIcon, spectatorMode, autoHideFab | phase 1 |
| showAltIndicator, showNoAltIndicator, collapseLongPosts | phase 2 |
| showDividers | status list divider paint falls back to the surface colour (activity restarts) |
| loadNewPosts, showNewPostsButton | HomeTimelineFragment no longer refreshes on every show |
| showNavigationLabels, doubleTapToSearch | tab bar labels hidden; re-tap search gated |
| overlayMedia | audio focus ducks in AudioPlayerService and PhotoViewer |
| removeTrackingParams | browser, link copy, post-link copy, text copy, publish |
| defaultToUnlistedReplies | compose reply visibility + notification replies |
| translateButtonOpenedOnly | header translate action hidden in timelines |
| displayPronounsInTimelines / Threads / UserListings | UiUtils.extractPronouns + header subtitle and account rows |
| showMediaPreview | previewless attachment list (new display item + file rows) |

### Notifications
uniformNotificationIcon, swapBookmarkWithBoostAction (bookmark is the default third action),
enableDeleteNotifications (per-notification delete + clear all), follow requests screen with
badge, conversation mute confirmation, UnifiedPush.

### Screens and features
Panic responder, share-target account picker, follow requests, blocked/muted account lists,
per-instance settings (content types, emoji reactions, local-only, Glitch mode),
own-profile overflow menu (lists, hashtags, muted, blocked, browser), privacy settings
(default visibility, locked account, tracking removal), UnifiedPush.

## Not ported, and why

| Upstream feature | Blocker |
|---|---|
| `relocatePublishButton`, `publishButtonText` | The rewrite's publish action is a toolbar icon (`R.id.publish`), not the text button these prefs style. Needs a compose layout design pass, not a port. |
| `autoRevealEqualSpoilers` | The rewrite does not track `spoilerRevealed` per status at all, so there is no reveal state to copy between a thread's posts. Requires spoiler state plumbing first. |
| `showPostsWithoutAlt` (alt-text filter) | Needs `AltTextFilter` plus `WarningFilteredStatusDisplayItem` and the client-side filter merge in `StatusDisplayItem.buildItems`. |
| `allowRemoteLoading` | Master's remote account/instance loading subsystem (`loadRemoteInfo`, `execRemote`, remote session) does not exist in the rewrite; `PaginatedAccountListFragment` here is 38 lines. |
| Scheduled statuses screen | Needs `CreateStatus.getDraftInstant()`, `DeleteStatus.Scheduled`, `ScheduledStatus.toFormattedStatus()` and scheduling/draft support in `ComposeFragment`, which has none. The screen alone cannot view, edit or delete anything useful. |
| `disableSwipe`, `doubleTapToSwipe` | The rewrite's `TabBar` has no swipe gesture; there is nothing to disable. |
| `confirmBeforeReblog`, `disableM3PillActiveIndicator`, `replyLineAboveHeader` | Dead upstream: declared but never consumed. |

## Deliberately skipped (dead or duplicated upstream)

`ListsFragment` (the fork has the whole lists feature; upstream's variant is the legacy
duplicate), `AddListMembersFragment`, `AddList`/`EditListName`/`RemoveList` (unused wrappers),
`GetInstance`, `GetExtendedDescription`, `GetWeeklyActivity`, `BlocksListFragment`,
`MutesListFragment`, `GetDomainBlocks`, `DomainBlock`, `PhotoViewerInfoSheet`,
`EmojiReactionButton`.

## Verified environment facts

- Local build: `JAVA_HOME=$HOME/.jdks/jdk-17.0.21+9`, `ANDROID_HOME=$HOME/Android/Sdk`, SDK 35,
  build-tools 35.0.0. `./gradlew :mastodon:assembleDebug` (~20 s incremental) and
  `:mastodon:assembleGithubRelease` (R8, ~1-2 min).
- CI `.github/workflows/build-apk.yml` builds and signs `githubRelease` for every `feat/**` push.
- Tool output redacts resource names starting with `sk_` as if they were API keys; read them
  from files, not from terminals.
