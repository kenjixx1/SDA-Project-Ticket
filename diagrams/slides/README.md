# Slide diagrams

Plain, static, white-background versions of the two diagrams — made for
**presenting**, not for reading on screen.

| File | What it is |
|---|---|
| `slide-ssd.png` / `.svg` | System Sequence Diagram |
| `slide-activity.png` / `.svg` | Activity Diagram |

## How to use them

- **PowerPoint / Keynote / Google Slides:** drag the `.png` straight in. They
  are 3200 × ~1800 (2× retina), so they stay sharp on a projector.
- **Want to edit or resize without blur:** use the `.svg`. It opens in
  Illustrator, Figma, Inkscape, or any browser.
- Both are **16:9**, the same shape as a slide. They already have a title, so
  you can drop one onto a blank slide with no title box.

## Rebuild after editing the SVG

```
python3 -c "import cairosvg; cairosvg.svg2png(url='slide-ssd.svg', \
  write_to='slide-ssd.png', scale=2, background_color='white')"
```

## How these differ from the interactive versions

The files one folder up (`../ssd-booking-payment.html`,
`../activity-booking-flow.html`) are the full, detailed diagrams. These slide
versions are **cut down on purpose**, because an audience reads a slide for
about ten seconds.

| Cut from the slide version | Still in the full version |
|---|---|
| Activity: the "payment declined → retry" branch | yes |
| Activity: separate "Request authorisation" step (folded into *Pay with PromptPay*) | yes |
| Activity: the queue "your turn?" decision (shown as a self-loop instead) | yes |
| SSD: `listTiers` as its own message | folded into *admitted + tiers* in both |

Nothing was changed, only left out. Every number still matches
`../../AGENTS.md` §2.

## What to say when the slide is up

**Sequence diagram** — "Two lifelines only. Ploy, and the system as one box.
The last line is the one that matters: if the bank answers after the ten
minutes, she loses the seats but she is never charged."

**Activity diagram** — "Follow the green line for the normal path. The two
orange diamonds are the whole design. The first one stops the same seat being
sold twice. The second one stops Ploy paying for a seat she no longer has."
