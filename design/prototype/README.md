# Prototype

Real HTML screens. Open a file in a browser, or on your phone.

| File | Screen | Traces to |
|---|---|---|
| `01-queue.html` | Customer Queue, two states | UC-01 · FR-02, FR-04 |

Built from `../../diagrams/ui-mockup-prompt.md` Block B1.
Visual language: `minimalist-ui`. Build: `design-taste-frontend`.

## How to open

Double-click the file. No build step, no npm, no server. It is one
self-contained HTML file with native CSS.

- **On a laptop** you get a phone frame so it reads as a mobile screen.
- **Under 430px wide** the frame disappears and the screen goes full bleed,
  which is what a real phone shows.

## What you can do on the queue screen

- **In the queue** / **Almost your turn** switch between the two states the
  brief asks for.
- **Watch it move** makes the position actually count down, the way it would
  during a real sale. This is the point of the screen: if it does not visibly
  move, Kenji refreshes, and every refresh makes the spike worse.

## Publishing a link for your phone

The published copy is generated from this file, so this file stays the single
source. To regenerate it:

```
python3 - <<'PY'
import re, pathlib
src = pathlib.Path('01-queue.html').read_text()
head = re.sub(r'<meta[^>]*>\s*', '', re.search(r'<head>(.*)</head>', src, re.S).group(1))
body = re.search(r'<body>(.*)</body>', src, re.S).group(1)
pathlib.Path('/tmp/queue-artifact.html').write_text(head.strip() + "\n\n" + body.strip())
PY
```

Then publish `/tmp/queue-artifact.html`. The platform supplies its own
doctype, charset and viewport, which is why they are stripped.

## Design decisions worth defending

- **The position is the largest thing on screen** and uses tabular figures, so
  the digits do not jump as it counts down.
- **There is no refresh button.** Offering one invites the exact behaviour
  that breaks the system under load.
- **No prices and no countdown timer.** Nothing is reserved yet. The countdown
  belongs on the next screen, after the hold exists.
- **The estimate says "about 6 min"** and is placed in quiet mono type. An
  estimate presented as a promise is worse than no estimate.
- **One live dot in the footer**, plus "Updated N seconds ago". That is the
  quiet proof the page is still connected. It is real state, not decoration.
- **No photography.** A queue screen with a stock concert photo behind the
  text would fight the one number that matters.

## Still to build

`02-selection.html` (two states, before and after the hold),
`03-confirmation.html`, `04-expired.html`. Briefs are in
`../../diagrams/ui-mockup-prompt.md`, blocks B2 to B4.
