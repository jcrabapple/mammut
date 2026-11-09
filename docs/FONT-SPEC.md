# Font selection

Status: spec for implementation. Owner: whoever implements `FONT_SPEC`.
Target version: 1.3.0.

## 1. What the reader gets

**Settings → Display → Typeface**, a single-choice list directly under the existing display
options:

| Entry | Family | Why it is in the list |
|---|---|---|
| **System** (default) | the platform's own | Nobody's choice is taken away. This is what the app renders today. |
| **Inter** | Inter 4.x | The UI sans most apps have converged on. Tall x-height, open apertures, holds up at 13 sp. |
| **Source Sans 3** | Adobe's humanist sans | Softer and narrower than Inter; the one to pick when Inter feels cold. |
| **Literata** | Google's reading serif | Designed for long-form screen reading. The serif for the Reading Room and Catch up. |
| **Atkinson Hyperlegible** | Braille Institute | Built for low vision: `I l 1`, `O 0`, `B 8` are deliberately unlike each other. |

Every entry shows its own name in its own face, above a one-line sample of real post text in the
reader's current theme colours. A list of unfamiliar names set in the font you are looking at is
the whole feature; a list set in the current font is a list of words.

The setting applies to the whole app: post text, names, headers, buttons, dialogs, the compose
box, the share card. Two things deliberately keep the system face: **code spans** (monospace must
stay monospace) and the **numeric tabular bits the app draws itself** if they exist.

## 2. Bundled assets

Eight model families would be 10 MB, so the set is four, all SIL OFL 1.1, all with a real italic:

| File | Family | Weight | Notes |
|---|---|---|---|
| `res/font/inter_regular.ttf` | Inter | 400 | instanced from `Inter[opsz,wght]` at `opsz=14` |
| `res/font/inter_medium.ttf` | Inter | 500 | `opsz=14` |
| `res/font/inter_bold.ttf` | Inter | 700 | `opsz=14` |
| `res/font/inter_italic.ttf` | Inter Italic | 400 | `opsz=14` |
| `res/font/source_sans_regular.ttf` | Source Sans 3 | 400 | |
| `res/font/source_sans_medium.ttf` | Source Sans 3 | 500 | |
| `res/font/source_sans_bold.ttf` | Source Sans 3 | 700 | |
| `res/font/source_sans_italic.ttf` | Source Sans 3 Italic | 400 | |
| `res/font/literata_regular.ttf` | Literata | 400 | `opsz=16` |
| `res/font/literata_medium.ttf` | Literata | 500 | `opsz=16` |
| `res/font/literata_bold.ttf` | Literata | 700 | `opsz=16` |
| `res/font/literata_italic.ttf` | Literata Italic | 400 | `opsz=16` |
| `res/font/atkinson_regular.ttf` | Atkinson Hyperlegible | 400 | static upstream |
| `res/font/atkinson_bold.ttf` | Atkinson Hyperlegible | 700 | no 500 exists; **500 maps to 700** |
| `res/font/atkinson_italic.ttf` | Atkinson Hyperlegible Italic | 400 | |

3.9 MB total. Licences ship inside the APK at `assets/font-licenses/OFL-*.txt`; OFL 1.1 requires
the licence text to travel with the font, so those files are not optional and not compressed away.

**Coverage:** Inter, Source Sans 3 and Literata carry Latin, Latin Extended, Greek and Cyrillic.
**Atkinson Hyperlegible is Latin-only** (342 glyphs, no Cyrillic). Text in a script it does not
cover falls back per glyph to the system font, which is normal Android behaviour and produces
mixed-family lines. That is acceptable and must not be "fixed" by subsetting or by refusing the
script; the spec says so here so nobody later treats it as a bug.

**Do not subset these files.** Posts are multilingual and subsetting produces tofu where the
fallback would have rendered the text.

## 3. State

`GlobalUserPreferences.fontFamily` — a `FontFamily` enum stored **by name** (the same rule the
theme preference follows: reordering the enum must never reassign anyone's choice).

```java
public enum FontFamily { SYSTEM, INTER, SOURCE_SANS, LITERATA, ATKINSON }
```

Default `SYSTEM`. `MammutApp` loads it at startup next to the other preferences.

## 4. Applying it

A single class, `ui/text/MammutTypeface`, owns everything:

- `Typeface get(FontFamily family, int weight, boolean italic)` — weight in {400, 500, 700}, cached
  in a `SparseArray`-keyed map; created once through `ResourcesCompat.getFont`, never per bind.
- `Typeface apply(Typeface base)` style mapping: read the base's `getStyle()` flags so bold and
  italic survive; when `family == SYSTEM`, return `base` untouched so the default path costs
  nothing.
- Loading failure: log at `W` with the resource name and return `base`. A missing font must not
  crash a timeline, and it must not silently render the wrong family without a log line.

### 4.1 XML

Install a `LayoutInflater.Factory2` once per `Activity` via
`Application.ActivityLifecycleCallbacks.onActivityCreated`, on `activity.getLayoutInflater()`. In
`onCreateView`, after `super.onCreateView` (so the style's own `android:fontFamily` has already
been applied by the constructor), do:

1. If the view is a `TextView` and its resolved family is not `monospace`, set the typeface from
   `MammutTypeface`.
2. Weight from the style's font-family name: `sans-serif-medium` → 500, everything else → 400,
   with bold taken from `getTypeface().getStyle()`.
3. Leave anything carrying `monospace` alone, and leave `Span`-level typefaces alone: this hook
   sets the view's base typeface, it does not rewrite spans.

If the factory cannot be installed (a context whose inflater was already frozen by a framework
call), log once at `W` and carry on with the system face. No reflection into
`Typeface.sDefaults`; no patching of appkit.

### 4.2 Text drawn outside the view tree

`ShareImageRenderer` paints with `Paint`, so a shared card would otherwise come out in the system
face while the app shows Literata. Every `Paint` in the renderer takes the active family through
`MammutTypeface`, and the shared image must use the same faces as the screen.

## 5. Money and time

The typefaces are built once at first use and cached; the hook adds one `setTypeface` call per
`TextView` created, which is the same call the framework already makes for the style. No
allocation on bind, no measurable scroll cost. Verify with a scroll test at `SYSTEM` and at
`LITERATA` and say in the handoff that you looked.

## 6. What this does not do

- No font size, line height or letter spacing change. The face changes; the metrics of the
  layout do not get second-guessed. Text may reflow inside a post, which is expected.
- No custom font import from storage. That is a later, separate feature with its own file access
  and its own licence questions.
- No per-screen font, no "heading font" separate from body font.

## 7. Tests

JVM-testable and required:

- `MammutTypefaceTest`: family→resource mapping for all four families, weight fallback
  (Atkinson 500 → 700 file), `SYSTEM` returns the base instance unchanged, style flags preserved
  (bold stays bold, italic stays italic), and the cache returns the same instance for a repeated
  request.
- A test that every `FontFamily` value other than `SYSTEM` resolves to four non-null resources
  (guards a rename in `res/font/` from silently shipping a family that renders as the system
  face).

Device check (Jason, not an emulator — emulators SIGSEGV on this host):

1. Each family applies to the timeline, a conversation, the compose box and the FAB menu.
2. Code spans stay monospace under every family.
3. The share card matches the on-screen face.
4. Switching back to System restores the previous look with no restart.

## 8. Out of scope, deliberately

Open Sans, Lato, Roboto and Noto Sans are not bundled. They occupy the same role as Inter or
Source Sans 3 and would add megabytes to say the same thing; a reader who wants them has the
system font, which on most devices is Roboto. If a family is added later it is four files, one
enum constant, one row, one licence — that is the point of the shape above.
