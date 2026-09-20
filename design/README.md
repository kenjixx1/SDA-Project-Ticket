# Design

Where the UI work lives: screen mockups and the clickable prototype.

```
design/
├── mockups/      screen images — PNG, one file per screen
└── prototype/    clickable HTML — the real thing you can tap through
```

**The brief for every screen is already written.** It is
[`../diagrams/ui-mockup-prompt.md`](../diagrams/ui-mockup-prompt.md): four
screens, every number pinned, plus an acceptance checklist. Do not invent
screen content here — take it from that file.

---

## Which screens

| # | Screen | Why it exists |
|---|---|---|
| 1 | Queue | Named in the brief as the UI mockup target |
| 2 | Selection + countdown | Two states: before the hold, after the hold |
| 3 | Confirmation | Two separate tickets, two QR codes |
| 4 | Hold expired while paying | "You have not been charged" |

Phone size: **390 × 844**, portrait.

---

## Two routes. You probably want both.

### Route A — Pictures (`design/mockups/`)

Still images of each screen. Good for the report and the slides.

### Route B — A real prototype (`design/prototype/`)

Actual HTML you can open on your phone and tap through. Better for the
demo, and it doubles as the start of the front end.

---

## The skills you just installed, and which one does what

13 were installed. Only a few matter for this project.

| Skill | Use it for | Works in Claude Code? |
|---|---|---|
| `design-taste-frontend` | Building the HTML prototype | **Yes** |
| `minimalist-ui` | Clean editorial style — calm, flat, no gradients | **Yes** |
| `high-end-visual-design` | Makes the result feel expensive rather than templated | **Yes** |
| `redesign-existing-projects` | Improving a screen that already exists | **Yes** |
| `imagegen-frontend-mobile` | Generating mobile screen **images** | No — see below |
| `image-to-code` | Generate an image, then build it | Partly — no image step |
| `brandkit`, `industrial-brutalist-ui`, `gpt-taste`, `stitch-design-taste` | Not this project | — |

### The honest catch about the image skills

`imagegen-frontend-mobile` is instructions for an art director. It assumes
the agent can **make images**. Claude Code here has no text-to-image tool, so
asking me to run it will not produce pictures.

Three ways around it:

1. **Use the prompt file in a tool that does make images.** Open
   `../diagrams/ui-mockup-prompt.md`, paste Block A, then one screen block,
   into ChatGPT, Gemini, or Midjourney. Save the result into `mockups/`.
2. **Use Codex or Cursor.** The same 13 skills were installed there. If that
   tool has image generation, `imagegen-frontend-mobile` works properly.
3. **Skip images. Build the HTML instead** (Route B). For a phone UI this is
   often better anyway — you can actually tap it, and it cannot drift from
   the requirements the way a generated picture can.

---

## How to run a skill

In Claude Code, type the slash command, then your request:

```
/design-taste-frontend  Build the Queue screen from diagrams/ui-mockup-prompt.md
                        Block B1, as a 390x844 phone screen. Save to
                        design/prototype/01-queue.html
```

Or just describe what you want and ask me to use the skill by name — I will
load it before writing anything.

**Stacking two skills works and is usually better:**

```
/minimalist-ui  then  /design-taste-frontend
```

The first sets the visual language, the second builds the page.

---

## Suggested order of work

1. **Queue screen first.** It is the one the brief names, and it is the
   hardest: nothing happens on it for six minutes, so it has to feel alive
   without a refresh button.
2. **Selection screen, both states.** Before the hold and after the hold. The
   difference between those two artboards is the whole architecture in one
   picture — seats are taken before money is discussed.
3. **Confirmation.**
4. **Expired screen.** Do not skip this one. It is where a customer decides
   whether the system cheated them.

---

## Before anything goes in the report

Run the checklist at the bottom of
[`../diagrams/ui-mockup-prompt.md`](../diagrams/ui-mockup-prompt.md). The
things that get missed most:

- The queue position must be the **largest** thing on that screen
- **No refresh button** — offering one invites the behaviour that breaks the system
- The countdown appears **only after** the hold exists
- **Two** tickets for two seats, not one card saying "×2"
- On the expired screen, **"You have not been charged"** comes first and biggest

---

## A note on the skills folder

`.agents/` and `skills-lock.json` are **not** in git. They are re-installable:

```
npx skills add Leonxlnx/taste-skill
```

Everything in `design/` **is** in git — the mockups and the prototype are
project deliverables.
