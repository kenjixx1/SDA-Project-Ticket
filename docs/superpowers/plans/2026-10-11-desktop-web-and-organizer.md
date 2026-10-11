# Desktop Web + Organizer Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan runs through the `cc-bridge` skill with parallel lanes (see `## Lanes`).

**Goal:** Turn the customer app from a phone layout into the desktop web design (`design/Ticket App Web.dc.html`, SCR-01 to SCR-18), and build the organizer console (`design/Organizer Console.dc.html`, SCR-30 to SCR-40) with the server messages it needs.

**Architecture:** The page stays plain HTML, CSS and ES modules served by Javalin. The router in `js/app.js` moves into a shared `js/router.js` used by two pages: `index.html` (customer, Kenji) and `organizer.html` (organizer, Chai). The organizer talks to a new Java interface `OrganizerGateway` (`/api/org/...`), backed today by `FakeOrganizerGateway`, which edits the same in-memory events the customer fake sells from. So a tier Chai adds is a tier Kenji can buy.

**Tech Stack:** Java 17 (built on 21), Javalin 6.3.0, Jackson, JUnit 5, Maven. Browser side: no libraries, no build step. Playwright 1.64.0 (through `npx`) for screenshots.

## Global Constraints

- **Desktop only.** Target frame **1440 × 900**. The page must not scroll sideways at any width from **1024 px** up. There is no phone layout any more and no tab bar.
- **Look 1a tokens, exact values** (copy into CSS `:root`, do not invent others):
  `--bg:#000000; --s1:#111112; --s2:#1A1A1C; --s3:#252527; --l1:rgba(255,255,255,.08); --l2:rgba(255,255,255,.18); --tx:#F2F2F2; --mu:#8F8F94; --ac:#C5F25A; --a2:#5B8CFF; --ac2:#8FE3B0; --aci:#000000; --acw:rgba(197,242,90,.09); --st:#FF8B7B; --stw:rgba(255,139,123,.11); --qr:#F3F6FA; --r:22px; --rb:16px; --f:'Instrument Sans',system-ui,sans-serif; --fm:'JetBrains Mono',ui-monospace,monospace`
- Fonts: Instrument Sans 400/500/600/700 and JetBrains Mono 400/500/600 from Google Fonts (already linked in `index.html`).
- Where `docs/08-screen-requirements.md` says "cyan" use `--ac` (lime). Where it says "magenta" use `--st` (coral).
- **The design files are the source of exact sizes, spacing and copy.** Each UI task names line ranges in `design/Ticket App Web.dc.html` or `design/Organizer Console.dc.html`. Read those lines and copy font sizes, paddings, radii, gaps and text from the inline `style=` attributes. Design text wins over current text when they differ. `{{ name }}` in the design is a live value; take its meaning from the variable name and the design's `<script>` block (customer file from line 520, organizer file from line 305).
- Screens with no design (SCR-34c to f, SCR-35 to SCR-40) follow `docs/08-screen-requirements.md` §8.8 word for word, drawn with the organizer design's parts (table, card, chip, buttons, fields) and the customer design's dialog (lines 494–519).
- Story values only (`AGENTS.md` §2): The 1975 Bangkok, Impact Arena, 14 Nov 2026 19:00, sale 1 Oct 2026 10:00, VIP ฿6,500 × 500, Zone A ฿3,800 × 2,000, Zone B ฿1,900 × 5,000, Kenji, Chai, 10-minute hold, 4 per order, 12 per second.
- Bangkok time everywhere (`Asia/Bangkok`, UTC+7, no daylight saving). Money as `฿6,500`. Numbers with commas. Digits in tables: `--fm`, `font-variant-numeric: tabular-nums`, right aligned.
- No new libraries in the browser. No new Maven dependencies.
- Every value the page shows from the server goes through the `html` tagged template in `js/parts.js` (it escapes). Never build HTML by string concatenation of server data.
- Countdowns use `serverNow()` from `js/api.js`, never `Date.now()` alone.
- An action that can be refused (HTTP 400 or 409) catches `ApiError` itself and shows `e.message` next to the field named by `e.field`. Only 404 and 5xx are left to the router.
- Hover effects only inside `@media (hover:hover)`. Keep `prefers-reduced-motion` respected: inside `@media (prefers-reduced-motion: reduce)` set `animation:none; transition:none` for `*`.
- `core/` never imports `web/` and stays library-free. Only `web/` uses Javalin.
- Tests: `mvn test` must pass before every commit that touches `src/main/java/.../web/`, `src/test/java/.../web/` or `src/main/resources/public/`. `./run.sh test` must still pass (19 tests) — it ignores `web/`.
- Git: stage named paths only. **Never `git add -A` or `git add .`**. One commit per task minimum. Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Run the server with `mvn -q compile exec:java` (port 7070, or `PORT=7071 mvn -q compile exec:java` in a lane worktree to avoid clashes).

---

## Lanes

Task 1 runs first, alone. After it, lanes run in parallel, **at most 3 at once**. No file appears in two lanes.

| Lane | Tasks | Starts from | Files the lane owns (nobody else touches them) |
|---|---|---|---|
| **A · Organizer server** | 2, 3 | Task 1 commit | `src/main/java/.../web/OrganizerGateway.java`, `web/GatewayException.java`, `web/WebServer.java`, `web/dto/OrgViews.java`, `web/dto/OrgRequests.java`, `web/routes/OrgRoutes.java`, `web/routes/Reply.java`, `web/fake/FakeBookingGateway.java`, `web/fake/FakeOrganizerGateway.java`, `src/test/java/.../web/fake/FakeOrganizerTest.java`, `src/test/java/.../web/OrgApiContractTest.java`, `docs/09-web-api.md` |
| **B · Customer browse** | 4, 5 | Task 1 commit | `public/js/screens/shell.js`, `event.js`, `queue.js`, `select.js`, `public/css/browse.css` |
| **C · Customer purchase** | 6, 7 | Task 1 commit | `public/js/screens/hold.js`, `pay.js`, `tickets.js`, `public/css/purchase.css` |
| **D · Organizer create** | 8, 9 | **Lane A's last commit** (needs the API) | `public/js/org/list.js`, `public/js/org/wizard.js`, `public/css/org-wizard.css` |
| **E · Organizer manage** | 10, 11 | **Lane A's last commit** | `public/js/org/overview.js`, `public/js/org/dialogs.js`, `public/js/org/dashboard.js`, `public/css/org-manage.css` |

- Start A, B and C together. When A finishes, start D in its slot. When B or C finishes, start E.
- Everything a lane reads from another lane is written in this plan: the API shapes (Task 2 and Task 3), the shared helpers (Task 1). No lane waits on another lane's code except D and E, which branch from A.
- **Task 12 (join)** merges B, C, D and E (D and E already contain A), runs every suite, runs both screenshot walks, and updates the docs.

Shared, read-only for every lane (made in Task 1, changed by nobody after): `public/index.html`, `public/organizer.html`, `public/js/app.js`, `public/js/router.js`, `public/js/api.js`, `public/js/parts.js`, `public/js/dev.js`, `public/js/org/main.js`, `public/js/org/api.js`, `public/js/org/parts.js`, `public/css/app.css`, `public/css/org.css`, `src/test/e2e/walk.mjs`, `src/test/e2e/org-walk.mjs`. If a lane needs a change there, it puts an override in its own CSS file or a local helper in its own JS file, and reports it.

---

### Task 1: Desktop foundation (shared router, top bars, dialogs, CSS split, organizer page shell)

**Implementer:** claude

**Files:**
- Create: `src/main/resources/public/js/router.js`, `public/css/browse.css`, `public/css/purchase.css`, `public/organizer.html`, `public/js/org/main.js`, `public/js/org/api.js`, `public/js/org/parts.js`, `public/js/org/list.js`, `public/js/org/wizard.js`, `public/js/org/overview.js`, `public/js/org/dialogs.js`, `public/js/org/dashboard.js`, `public/css/org.css`, `public/css/org-wizard.css`, `public/css/org-manage.css`, `src/test/e2e/org-walk.mjs`
- Modify: `public/js/app.js`, `public/js/api.js`, `public/js/parts.js`, `public/css/app.css`, `public/index.html`, `src/test/e2e/walk.mjs`
- Add to git: `design/Ticket App Web.dc.html`, `design/Organizer Console.dc.html` (the two new design sources).

**Interfaces:**
- Produces `js/router.js`: `export function startApp({ screens, chrome })`. `screens` is the same list of screen definitions `app.js` uses today (`{ pattern, enter, poll, view, actions, onZero, loading, ownOffline }`). `chrome(path)` returns an `html` value drawn above every screen (the top bar). Home is always `#/events`.
- Produces `js/api.js`: `export async function call(method, path, body)` (was private). `ApiError` gains a 5th field: `constructor(status, code, message, remaining, field)` and `this.field`. The identity default becomes `'chai'` when `location.pathname` ends with `organizer.html`, else `'kenji'`; `?as=` still wins.
- Produces `js/parts.js`: `topNav(path)` (customer top bar), `sheet(...)` now draws a **centred dialog** with the same signature, `tabBar()` returns an empty `html` value (screens still calling it keep working), `statusScreen(...)` same signature, new desktop look. Every other export keeps its name and signature.
- Produces `js/org/api.js`: `export const org = { events, create, event, update, replaceTiers, addTier, updateTier, publish, setRate, close, dashboard }` (code below).
- Produces `js/org/parts.js`: `orgTopBar()`, `chip(state)`, `field({ label, name, value, type, error, counter, suffix, disabled })`, `toEpoch(dateStr, timeStr)`, `dateInput(ms)`, `timeInput(ms, seconds)`, `dateTime(ms)` (code below).
- Produces `js/org/main.js` that registers `screens` from `list.js`, `wizard.js`, `overview.js`, `dashboard.js`. Each of those five files starts as `export const screens = [];`. `dialogs.js` starts as `export {};`.
- CSS: `app.css` keeps only shared parts (tokens, reset, top nav, page container, buttons, banner, card, dialog, status screen, live dot, skeleton, dev panel, `.split` two-column grid). Every phone rule for SCR-01–08 moves **unchanged** into `browse.css`; for SCR-09–18 into `purchase.css`. `index.html` links `app.css`, `browse.css`, `purchase.css` in that order. `organizer.html` links `app.css`, `org.css`, `org-wizard.css`, `org-manage.css`.

- [ ] **Step 1: Move the router**

Move everything in `app.js` except the screen imports into `router.js`, unchanged in behaviour, wrapped as `startApp`. The two differences:

```js
// js/router.js — top of file
import { ApiError, serverNow, devMode } from './api.js';
import { html, skeleton, offlineBanner, notAvailable, mmss, hms, barPct, liveText } from './parts.js';
import { mountDev } from './dev.js';

export function startApp({ screens, chrome = () => '' }) {
  const SCREENS = screens;
  const appEl = document.getElementById('app');
  // ... the existing go, stop, route, start, load, guard, render, tick, click listener ...
}
```

In `render(me)` the drawn HTML becomes:

```js
appEl.innerHTML = String(html`${chrome(me.path)}${top}${body}`);
```

`app.js` becomes:

```js
// Customer app: Kenji buys tickets. Screens live in screens/; the router lives in router.js.
import { startApp } from './router.js';
import { topNav } from './parts.js';
import { screens as shell } from './screens/shell.js';
import { screens as event } from './screens/event.js';
import { screens as queue } from './screens/queue.js';
import { screens as select } from './screens/select.js';
import { screens as hold } from './screens/hold.js';
import { screens as pay } from './screens/pay.js';
import { screens as tickets } from './screens/tickets.js';

startApp({ screens: [...shell, ...event, ...queue, ...select, ...hold, ...pay, ...tickets], chrome: topNav });
```

- [ ] **Step 2: `api.js` changes**

```js
const params = new URLSearchParams(location.search);
const fallback = location.pathname.endsWith('organizer.html') ? 'chai' : 'kenji';
export const customer = (params.get('as') || fallback).toLowerCase().replace(/[^a-z0-9-]/g, '') || fallback;

export class ApiError extends Error {
  constructor(status, code, message, remaining, field) {
    super(message);
    this.status = status;
    this.code = code;
    this.remaining = remaining;
    this.field = field ?? null;
  }
}
// in call(): throw new ApiError(res.status, e.code || 'HTTP_' + res.status, e.message || 'Request failed', e.remaining ?? null, e.field ?? null);
export async function call(method, path, body) { /* unchanged body */ }
```

- [ ] **Step 3: Customer top bar and dialog in `parts.js`**

Read `design/Ticket App Web.dc.html` lines 35–46 (top bar) and 494–519 (dialogs). Top bar: height 68 px, padding `0 40px`, gap 40 px, bottom border `1px solid var(--l1)`. Left: 10 px lime dot with the `tq-dot` pulse, then `Ticket & Queue` (600 16px). Links `Events` (`#/events`) and `Tickets` (`#/tickets`): the active one is `--tx` with a 2 px lime underline, the other `--mu`. Right (margin-left auto): 34 px lime circle with the first letter of the customer in `--aci`, then the name, both linking to `#/account`.

```js
import { customer } from './api.js';
const NAME = customer.charAt(0).toUpperCase() + customer.slice(1);
const tabOf = (path) => (/^\/(tickets|ticket\/|confirmed\/)/.test(path) ? 'tickets' : /^\/account/.test(path) ? 'account' : 'events');
export const topNav = (path = '') => {
  const on = tabOf(path);
  return html`<header class="topnav"><a class="brand" href="#/events"><i></i>Ticket &amp; Queue</a>
    <nav aria-label="Main"><a href="#/events" class="${on === 'events' ? 'on' : ''}">Events</a><a href="#/tickets" class="${on === 'tickets' ? 'on' : ''}">Tickets</a></nav>
    <a class="who ${on === 'account' ? 'on' : ''}" href="#/account"><b>${NAME.charAt(0)}</b>${NAME}</a></header>`;
};
export const tabBar = () => html``;
```

`sheet()` keeps its signature and markup classes `sheet-back` and `sheet`; only CSS changes: `.sheet-back` covers the page with `rgba(0,0,0,.6)` and `backdrop-filter: blur(6px)`; `.sheet` is fixed at the centre, width 460 px, `background: var(--s2)`, `border:1px solid var(--l1)`, `border-radius: var(--r)`, padding 28 px, `animation: tq-modal .3s`. Title 600 24px; text `--mu` 15px/1.5; the buttons sit side by side in `.acts` (`display:grid; grid-template-columns:1fr 1fr; gap:10px`). Escape closes it: add to `router.js` a `keydown` listener that clicks `.sheet-back` when `Escape` is pressed.

- [ ] **Step 4: Status screens (SCR-07, 10, 11, 18 share this)**

Read lines 252–271. `statusScreen` keeps its signature. New CSS for `.screen.center`: max-width 560 px, centred, `padding: 90px 40px 70px`, ring 76 px, title 600 34px, text `--mu` 16px/1.5, buttons in a column under the text (`.foot` is no longer fixed: inside a `.screen.center` it is `position:static; width:100%; display:flex; flex-direction:column; gap:10px; margin-top:26px`).

- [ ] **Step 5: Split the CSS**

1. Back up: `mkdir -p backup-$(date +%Y%m%d-%H%M%S)-css && cp src/main/resources/public/css/app.css backup-*-css/`. (Untracked; do not commit it.)
2. Move rules for `.pick .stepper .qty .total .qhero .qbar .qmeta .poster .back .sale .prices .feature .carousel* .cdots .count .cnav .erow .events*` and every other SCR-01–08 rule into `browse.css`, unchanged.
3. Move rules for `.countdown .bar .rows .row .slim .stub .tear .ticket* .qrbox .method* .card-sim .pp*` and every other SCR-09–18 rule into `purchase.css`, unchanged.
4. In `app.css`: replace `#app{max-width:430px;...}` with `#app{min-height:100vh}`. Add `.page{max-width:1200px;margin:0 auto;padding:30px 40px 70px;display:flex;flex-direction:column;gap:22px}` and `.split{display:grid;grid-template-columns:minmax(0,1fr) 400px;gap:32px;align-items:start}` and `@media (max-width:1100px){.split{grid-template-columns:minmax(0,1fr) 340px}}`. Make `.foot` static (`position:static;transform:none;width:auto`) so no bar covers content while the lanes rebuild screens. `.btn` keeps height 54 px, `width:100%` stays (columns set the width).
5. Add the `.topnav` rules from Step 3 and the reduced-motion block from the Global Constraints.

- [ ] **Step 6: Organizer page shell**

`organizer.html`: same head as `index.html`, title `EventFlow · Organizer`, links `css/app.css`, `css/org.css`, `css/org-wizard.css`, `css/org-manage.css`, body `<main id="app" aria-live="polite"></main><div id="dev"></div><script type="module" src="js/org/main.js"></script>`.

`js/org/main.js`:

```js
// Organizer console: Chai creates and runs events. Screens: list (SCR-30), wizard (31–33), overview (34–39), dashboard (40).
import { startApp } from '../router.js';
import { orgTopBar } from './parts.js';
import { screens as list } from './list.js';
import { screens as wizard } from './wizard.js';
import { screens as overview } from './overview.js';
import { screens as dashboard } from './dashboard.js';

startApp({ screens: [...list, ...wizard, ...overview, ...dashboard], chrome: orgTopBar });
```

`js/org/api.js`:

```js
// The organizer's messages to Java (O-01 to O-11, docs/09-web-api.md). Same envelope as the customer's.
import { call } from '../api.js';

const enc = encodeURIComponent;
const B = '/api/org/events';

export const org = {
  events: () => call('GET', B),                                                         // O-01
  create: (details) => call('POST', B, details),                                        // O-02
  event: (id) => call('GET', `${B}/${enc(id)}`),                                        // O-03
  update: (id, details) => call('PATCH', `${B}/${enc(id)}`, details),                   // O-04
  replaceTiers: (id, tiers) => call('PUT', `${B}/${enc(id)}/tiers`, { tiers }),         // O-05
  addTier: (id, tier) => call('POST', `${B}/${enc(id)}/tiers`, tier),                   // O-06
  updateTier: (id, tierId, tier) => call('PATCH', `${B}/${enc(id)}/tiers/${enc(tierId)}`, tier), // O-07
  publish: (id) => call('POST', `${B}/${enc(id)}/publish`),                             // O-08
  setRate: (id, admitPerSecond) => call('PUT', `${B}/${enc(id)}/rate`, { admitPerSecond }), // O-09
  close: (id) => call('POST', `${B}/${enc(id)}/close`),                                 // O-10
  dashboard: (id) => call('GET', `${B}/${enc(id)}/dashboard`),                          // O-11
};
```

`js/org/parts.js` (read `design/Organizer Console.dc.html` lines 20–49 for the top bar, 93–202 for fields, 55–71 for chips):

```js
// Organizer shared parts: top bar, state chip, form field, Bangkok date helpers.
import { html, raw } from '../parts.js';

export const orgTopBar = () => html`<header class="topnav org"><a class="brand" href="#/events"><i></i>Ticket &amp; Queue · Organizer</a>
  <nav aria-label="Main"><a href="#/events" class="on">Events</a></nav><span class="who"><b>C</b>Chai</span></header>`;

const CHIP = { DRAFT: 'DRAFT', SCHEDULED: 'SCHEDULED', ON_SALE: 'ON SALE', CLOSING: 'CLOSING', CLOSED: 'CLOSED', CANCELLED: 'CANCELLED' };
/** DRAFT grey · SCHEDULED lime outline · ON_SALE lime fill · CLOSING lime outline · CLOSED grey · CANCELLED coral. */
export const chip = (state) => html`<span class="chip ${state.toLowerCase()}">${CHIP[state] || state}</span>`;

/** One labelled input. error: text shown in coral under it (and the input gets a coral border). */
export function field({ label, name, value = '', type = 'text', error = '', counter = '', suffix = '', disabled = false, extra = '' }) {
  return html`<label class="field ${error ? 'bad' : ''}"><span class="fl"><b>${label}</b>${counter ? html`<i>${counter}</i>` : ''}</span>
    <span class="fi"><input name="${name}" type="${type}" value="${value}" ${disabled ? raw('disabled') : ''} ${raw(extra)}>${suffix ? html`<em>${suffix}</em>` : ''}</span>
    ${error ? html`<span class="err" role="alert">${error}</span>` : ''}</label>`;
}

const pad = (n) => String(n).padStart(2, '0');
const BKK = 7 * 3600_000;
/** "2026-10-01" + "10:00" or "10:00:00" in Bangkok -> epoch ms. Empty input -> null. */
export const toEpoch = (d, t) => (d && t ? Date.parse(`${d}T${t.length === 5 ? t + ':00' : t}+07:00`) : null);
export const dateInput = (ms) => { if (ms == null) return ''; const x = new Date(ms + BKK); return `${x.getUTCFullYear()}-${pad(x.getUTCMonth() + 1)}-${pad(x.getUTCDate())}`; };
export const timeInput = (ms, seconds = false) => { if (ms == null) return ''; const x = new Date(ms + BKK); return `${pad(x.getUTCHours())}:${pad(x.getUTCMinutes())}${seconds ? ':' + pad(x.getUTCSeconds()) : ''}`; };
const MON = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
/** 14 Nov 2026, 19:00 */
export const dateTime = (ms) => { const x = new Date(ms + BKK); return `${x.getUTCDate()} ${MON[x.getUTCMonth()]} ${x.getUTCFullYear()}, ${pad(x.getUTCHours())}:${pad(x.getUTCMinutes())}`; };
```

`org.css`: the organizer shared parts, values from the organizer design: `.topnav.org` (height 64 px), `.org-page{max-width:1120px;margin:0 auto;padding:44px 40px 80px;display:flex;flex-direction:column;gap:26px}`, `.chip` (600 11px/1 `--fm`, letter-spacing .1em, padding 5px 9px, radius 999px; colours per the `chip` comment), `.tbl` (card table: `--s1` background, `1px solid var(--l1)` border, radius `var(--r)`, header cells 600 11px `--fm` letter-spacing .12em `--mu`, rows 64 px with `1px solid var(--l1)` top border, numeric cells right aligned in `--fm`), `.field` (label 500 15px, counter 400 12px `--fm` `--mu`, input height 52 px, background `--bg`, border `1px solid var(--l2)`, radius 14px, padding `0 16px`, `.bad` input border `--st`, `.err` 500 13px `--st`), `.btn.sm` (height 46 px, width auto, padding `0 22px`). Native `<input type="date">` and `<input type="time" step="1">` are used for dates (styled like the other inputs, `color-scheme: dark`).

- [ ] **Step 7: Screenshot walks at desktop size**

In `walk.mjs`: viewport `1440,900`, output folder `target/shots-web`, and the header comment says to compare with `design/Ticket App Web.dc.html`. Create `src/test/e2e/org-walk.mjs` in the same style (same `call`, `shoot`, `dev` helpers, viewport 1440,900, page `organizer.html`, folder `target/shots-org`, identity `chai`) with these shots:

| Shot | Setup | Address |
|---|---|---|
| `30a` | reset | `/events` |
| `31a` | reset | `/new` |
| `32a` | reset; create `{name:'Test Night Bangkok', venue:'Impact Arena', eventDateTime: 14 Nov 2026 19:00, saleOpensAt: 1 Nov 2026 10:00, admitPerSecond:12}` | `/event/<id>/tiers` |
| `33a` | as 32a, then PUT tiers VIP 6500×500, Zone A 3800×2000, Zone B 1900×5000 | `/event/<id>/review` |
| `33b` | as 32a | `/event/<id>/review` |
| `34a` | as 32a | `/event/<id>` |
| `34b` | as 33a, then publish | `/event/<id>/published` |
| `34c` | reset, advance 60 s | `/event/the-1975` |
| `34d` | reset, advance 60, a customer joins and holds 2 VIP (advance 350 to admit), then close | `/event/the-1975` |
| `34e` | reset, advance 60, scenario `SALE_CLOSED` | `/event/the-1975` |
| `34f` | reset, scenario `EVENT_CANCELLED` | `/event/the-1975` |
| `40a` | reset, advance 380 | `/event/the-1975/live` |
| `40c` | reset, advance 60, scenario `SALE_CLOSED` | `/event/the-1975/live` |

Dates in the setup are built with `Date.parse('2026-11-14T19:00:00+07:00')`. These shots will only show real screens once Lanes A, D and E are done; that is expected.

- [ ] **Step 8: Check nothing broke**

Run: `mvn test` → expected `Tests run: 42, Failures: 0, Errors: 0`.
Run: `./run.sh test` → 19 passed.
Start the server, then `node src/test/e2e/walk.mjs 01a 05b 08a 09a 13a 17a` → six PNGs in `target/shots-web`, no errors. Open `http://localhost:7070/` and `http://localhost:7070/organizer.html` in Playwright at 1440×900: the customer page shows the top bar and every screen still works (layouts are still phone-shaped, that is fine); the organizer page shows the organizer top bar and "This page is not available." (no screens yet). No console errors on either.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/public/js/router.js src/main/resources/public/js/app.js src/main/resources/public/js/api.js src/main/resources/public/js/parts.js src/main/resources/public/css/app.css src/main/resources/public/css/browse.css src/main/resources/public/css/purchase.css src/main/resources/public/index.html src/main/resources/public/organizer.html src/main/resources/public/js/org src/main/resources/public/css/org.css src/main/resources/public/css/org-wizard.css src/main/resources/public/css/org-manage.css src/test/e2e/walk.mjs src/test/e2e/org-walk.mjs "design/Ticket App Web.dc.html" "design/Organizer Console.dc.html"
git commit -m "Desktop foundation: shared router, top bars, centred dialogs, CSS split, organizer page shell"
```

---

### Task 2: Organizer rules in the fake (Lane A)

**Implementer:** deepseek

**Files:**
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/OrganizerGateway.java`, `web/dto/OrgViews.java`, `web/dto/OrgRequests.java`, `web/fake/FakeOrganizerGateway.java`, `src/test/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeOrganizerTest.java`
- Modify: `web/GatewayException.java`, `web/fake/FakeBookingGateway.java`

**Interfaces:**
- Produces the Java types below, exactly. Task 3 wires them to HTTP; Lanes D and E read their JSON.

- [ ] **Step 1: Types**

`dto/OrgViews.java`:

```java
package th.ac.kmitl.sda.ticketqueue.web.dto;

import java.util.List;

/** Every reply shape the organizer page receives. Field names are the JSON names. */
public final class OrgViews {

    private OrgViews() {
    }

    /** O-01 row. state: DRAFT, SCHEDULED, ON_SALE, CLOSING, CLOSED, CANCELLED. */
    public record OrgEventRow(String eventId, String name, String venue, long eventDateTime, long saleOpensAt,
                              String state, int sold, int total) {
    }

    /** held + sold + remaining == total, always. */
    public record OrgTier(String tierId, String name, long price, int total, int held, int sold, int remaining) {
    }

    /** O-02 to O-10. waiting: people in the queue now (0 unless ON_SALE). closedAt: null until CLOSED. */
    public record OrgEvent(String eventId, String name, String venue, long eventDateTime, long saleOpensAt,
                           String state, int admitPerSecond, List<OrgTier> tiers, int heldNow, int waiting,
                           Long closedAt) {
    }

    /** O-11. all: the sum row, tierId "all", name "All", price 0. revenue: confirmed orders only, in baht. */
    public record Dashboard(String eventId, String name, String state, int waiting, int admitPerSecond,
                            List<OrgTier> tiers, OrgTier all, long revenue, Long closedAt) {
    }
}
```

`dto/OrgRequests.java`:

```java
package th.ac.kmitl.sda.ticketqueue.web.dto;

import java.util.List;

/** Every request body the organizer page sends. A null field means "not given". */
public final class OrgRequests {

    private OrgRequests() {
    }

    public record EventDetails(String name, String venue, Long eventDateTime, Long saleOpensAt, Integer admitPerSecond) {
    }

    public record TierInput(String name, Long price, Integer total) {
    }

    public record TierList(List<TierInput> tiers) {
    }

    public record RateInput(Integer admitPerSecond) {
    }
}
```

`OrganizerGateway.java`:

```java
package th.ac.kmitl.sda.ticketqueue.web;

import th.ac.kmitl.sda.ticketqueue.web.dto.OrgRequests.EventDetails;
import th.ac.kmitl.sda.ticketqueue.web.dto.OrgRequests.TierInput;
import th.ac.kmitl.sda.ticketqueue.web.dto.OrgViews.Dashboard;
import th.ac.kmitl.sda.ticketqueue.web.dto.OrgViews.OrgEvent;
import th.ac.kmitl.sda.ticketqueue.web.dto.OrgViews.OrgEventRow;

import java.util.List;

/** Everything the organizer console asks of the system (UC-05, UC-06, UC-07). Refusals are GatewayExceptions. */
public interface OrganizerGateway {

    List<OrgEventRow> listEvents(String organizerId);                                            // O-01

    OrgEvent createEvent(String organizerId, EventDetails details);                              // O-02

    OrgEvent getEvent(String organizerId, String eventId);                                       // O-03

    OrgEvent updateDetails(String organizerId, String eventId, EventDetails details);            // O-04

    OrgEvent replaceTiers(String organizerId, String eventId, List<TierInput> tiers);            // O-05

    OrgEvent addTier(String organizerId, String eventId, TierInput tier);                        // O-06

    OrgEvent updateTier(String organizerId, String eventId, String tierId, TierInput tier);      // O-07

    OrgEvent publish(String organizerId, String eventId);                                        // O-08

    OrgEvent setRate(String organizerId, String eventId, Integer admitPerSecond);                // O-09

    OrgEvent closeSale(String organizerId, String eventId);                                      // O-10

    Dashboard dashboard(String organizerId, String eventId);                                     // O-11
}
```

`GatewayException`: add `public final String field;` (nullable). The existing 4-argument constructor sets `field = null`. Add a 5-argument constructor `(int status, String code, String message, Integer remaining, String field)` and two factories:

```java
public static GatewayException invalid(String code, String field, String message) {
    return new GatewayException(400, code, message, null, field);
}

public static GatewayException conflict(String code, String field, String message, Integer remaining) {
    return new GatewayException(409, code, message, remaining, field);
}
```

- [ ] **Step 2: Changes inside `FakeBookingGateway`**

1. `Event`: drop `final` from `name, shortName, venue, dateTime, saleOpensAt`. Add `String owner = "chai"; boolean draft; boolean closing; Long closedAt; int admitPerSecond = ADMIT_PER_SECOND;`. `artwork` stays final and may be `null` for events Chai creates.
2. `Tier`: drop `final` from `name, price, total`. Add `int soldByOrders;`. In `settle(...)`, after `t.sold += r.quantity;` add `t.soldByOrders += r.quantity;`.
3. Add the organizer state and keep the customer state as a mapping of it:

```java
/** The organizer's state. CLOSING turns into CLOSED (and stamps closedAt) once no seat is held. */
String orgState(Event e) {
    if (e.draft) {
        return "DRAFT";
    }
    if (e.cancelled) {
        return "CANCELLED";
    }
    if (e.closed) {
        return "CLOSED";
    }
    if (e.closing) {
        if (e.tiers.stream().anyMatch(t -> t.held > 0)) {
            return "CLOSING";
        }
        e.closed = true;
        e.closedAt = clock.now();
        return "CLOSED";
    }
    return clock.now() < e.saleOpensAt ? "SCHEDULED" : "ON_SALE";
}

/** What a customer sees: a closing sale already reads as closed (SCR-05e). */
String state(Event e) {
    String s = orgState(e);
    return s.equals("CLOSING") ? "CLOSED" : s;
}
```

4. `listEvents`: filter out `e.draft` as well as `e.cancelled`. `getEvent`, `joinQueue`, `availability`: a draft event answers `GatewayException.notFound("Event")` (use a helper `Event customerEvent(String id)` that calls `event(id)` and throws when `draft`).
5. Queue speed per event. Replace uses of `ADMIT_AFTER_MS` and `ADMIT_PER_SECOND` in `refresh(Token)`, `admittedSoFar` and `queueStatus` with the event's own rate. Keep the constants (tests use them):

```java
long admitAfterMs(Event e) {
    return (long) Math.ceil(START_AHEAD * 1000.0 / e.admitPerSecond);
}

void refresh(Token t) {
    long now = clock.now();
    Event e = events.get(t.eventId);
    long after = admitAfterMs(e);
    // FR-29: once the sale is closing or closed, nobody new is let in.
    if (t.state.equals("WAITING") && now >= t.issuedAt + after && orgState(e).equals("ON_SALE")) {
        t.state = "ADMITTED";
        t.admittedAt = t.issuedAt + after;
    }
    if (t.state.equals("ADMITTED") && now - t.admittedAt > TURN_LIMIT_MS) {
        t.state = "EXPIRED";
    }
}

int admittedSoFar(Token t) {
    long elapsed = Math.max(0, clock.now() - t.issuedAt);
    return (int) Math.min(START_AHEAD, elapsed * events.get(t.eventId).admitPerSecond / 1000);
}
```

`queueStatus` passes `event(t.eventId).admitPerSecond` instead of `ADMIT_PER_SECOND`.
6. `scenario(SALE_CLOSED)` also sets `e.closedAt = clock.now()`.
7. Two read helpers the organizer side uses:

```java
static final int CROWD_1975 = 35_768;

/** People waiting now. The 1975 also has the crowd from the story: 31,208 at 10:06:20 at 12 per second. */
int waiting(Event e) {
    if (!orgState(e).equals("ON_SALE")) {
        return 0;
    }
    long real = tokens.values().stream().filter(t -> t.eventId.equals(e.id))
            .peek(this::refresh).filter(t -> t.state.equals("WAITING")).count();
    long crowd = e.id.equals("the-1975")
            ? Math.max(0, CROWD_1975 - (clock.now() - e.saleOpensAt) * e.admitPerSecond / 1000)
            : 0;
    return (int) (crowd + real);
}

/** Confirmed money for this event: seats sold before the demo at list price, plus what each order paid. */
long revenue(Event e) {
    long seeded = e.tiers.stream().mapToLong(t -> (long) (t.sold - t.soldByOrders) * t.price).sum();
    long paid = orders.values().stream().filter(o -> o.event.eventId().equals(e.id)).mapToLong(o -> o.paid).sum();
    return seeded + paid;
}
```

- [ ] **Step 3: Write the failing tests**

`FakeOrganizerTest.java` — same setup style as `FakeBookingTest` (manual clock at `FakeBookingGateway.START`, real time frozen). Use these values: `long EVENT = FakeBookingGateway.at(2026, 11, 14, 19, 0); long SALE = FakeBookingGateway.at(2026, 11, 1, 10, 0);` and a helper `EventDetails details(String name)` → `new EventDetails(name, "Impact Arena", EVENT, SALE, 12)`; `TierInput tier(String n, long p, int q)`.

```java
@Test void listsChaisFourEventsNewestDateFirst()        // ids: marshmello, alan-walker, bruno-mars, the-1975; the-1975 SCHEDULED at 09:59:30
@Test void otherOrganizerSeesNothing()                  // listEvents("someone") is empty; getEvent("someone","the-1975") -> 404
@Test void createMakesADraftHiddenFromCustomers()       // state DRAFT, eventId "test-night-bangkok", admitPerSecond 12; gw.listEvents("kenji") has no such id; gw.getEvent("kenji", id) -> 404
@Test void createRefusesASaleInThePast()                // saleOpensAt = START - 1 -> 400 SALE_IN_PAST, field "saleOpensAt", message "The sale must open in the future."
@Test void createRefusesASaleAfterTheEvent()            // saleOpensAt = EVENT + 1 -> SALE_AFTER_EVENT, "The sale must open before the event."
@Test void createRefusesEmptyAndTooLong()               // name "" -> REQUIRED field "name" message "Required"; name of 41 chars -> TOO_LONG "Up to 40 characters"; venue 31 chars -> TOO_LONG "Up to 30 characters"
@Test void createRefusesRateBelowOne()                  // admitPerSecond 0 -> RATE_TOO_LOW field "admitPerSecond" "Must be at least 1."
@Test void sameNameGetsANewId()                         // two creates "Test Night Bangkok" -> "test-night-bangkok", "test-night-bangkok-2"
@Test void replaceTiersValidatesEachRow()               // [VIP 6500 500, Zone B 1900 0] -> SEATS_ZERO field "tiers[1].total" message "Zone B · Seats must be more than 0."
                                                        // [VIP -1 500] -> PRICE_NEGATIVE "tiers[0].price" "VIP · Price cannot be below ฿0."
                                                        // ["" 100 5] -> TIER_NAME_REQUIRED "tiers[0].name" "Tier 1 · Name is required."
                                                        // 9 rows -> TOO_MANY_TIERS field "tiers" "Up to 8 tiers."
@Test void replaceTiersSetsIdsFromNames()               // VIP, Zone A, Zone B -> ids vip, zone-a, zone-b; held 0 sold 0 remaining = total
@Test void publishNeedsATier()                          // publish with no tiers -> 409 NO_TIERS "An event needs at least one tier before it can be published."
@Test void publishMakesItScheduledAndVisible()          // after tiers + publish: state SCHEDULED; customers now see it in listEvents with state SCHEDULED
@Test void publishTwiceIsRefused()                      // second publish -> 409 NOT_DRAFT "Only a draft can do this."
@Test void replaceTiersOnlyOnADraft()                   // replaceTiers on "the-1975" -> 409 NOT_DRAFT
@Test void priceIsFrozenWhileOnSale()                   // advance 60 s; updateTier(the-1975, vip, new TierInput("VIP", 7200L, 500)) -> 409 PRICE_FROZEN field "price"
                                                        //   message "Prices are frozen while the sale is on, so no customer sees a price change mid-purchase."
@Test void samePriceWhileOnSaleIsFine()                 // updateTier(vip, ("VIP", 6500L, 600)) -> total 600, remaining grows by 100
@Test void cannotReduceBelowHeldPlusSold()              // advance 60; a customer holds 2 VIP (join, advance 350, hold); VIP sold 484, held 2;
                                                        //   updateTier total 485 -> 409 BELOW_HELD_SOLD field "total" remaining 486
                                                        //   message "VIP: 484 sold and 2 held. Cannot reduce below 486."
@Test void priceCanChangeBeforeTheSale()                // at START (09:59:30) updateTier vip price 7000 -> price 7000
@Test void addTierWhileOnSaleIsBuyable()                // advance 60; addTier("Zone C", 990, 100) -> tier "zone-c"; gw.availability("the-1975") contains zone-c remaining 100
@Test void rateChangesTheQueue()                        // setRate(the-1975, 24) -> admitPerSecond 24; customer queueStatus shows admitPerSecond 24; setRate 0 -> RATE_TOO_LOW
@Test void closeSaleKeepsLiveHolds()                    // advance 60; kenji holds 2 VIP; closeSale -> state CLOSING, heldNow 2; customer getEvent state "CLOSED";
                                                        //   kenji's hold still HELD; advance 601 s -> getEvent(chai) state CLOSED and closedAt not null
@Test void closeWithNoHoldsIsClosedAtOnce()             // advance 60; closeSale -> state CLOSED
@Test void closeOnlyWhileOnSale()                       // at START closeSale -> 409 NOT_ON_SALE "Only a sale that is on can be closed."
@Test void closedEventIsLocked()                        // after close: updateDetails / addTier / updateTier / setRate -> 409 EVENT_LOCKED "This event can no longer be changed."
@Test void saleTimeLockedOnceOnSale()                   // advance 60; updateDetails with a new saleOpensAt -> 409 EVENT_LOCKED field "saleOpensAt" "The sale time cannot change once the sale is on."
                                                        //   updateDetails with the same saleOpensAt and venue "UOB Live" -> venue changes
@Test void closingStopsNewAdmissions()                  // advance 60; "mint" joins; kenji holds (so it stays CLOSING); close; advance 600; mint's queueStatus state still WAITING
@Test void dashboardAddsUp()                            // advance 380 (10:06:20); dashboard: waiting 31208, admitPerSecond 12; every tier held+sold+remaining == total;
                                                        //   all.total 7500; revenue == 484*6500 + 1650*3800 + 2980*1900
@Test void dashboardRevenueCountsPaidOrders()           // kenji buys 1 VIP by PromptPay (as in FakeBookingTest's paid flow); revenue grows by the order's paidAmount, not 6500
```

Write each test fully with `assertEquals` / `assertThrows` and check `code`, `field`, `getMessage()` and `remaining` where the comment names them.

- [ ] **Step 4: Run them, see them fail**

Run: `mvn -q test -Dtest=FakeOrganizerTest` → compile errors / failures (class missing).

- [ ] **Step 5: Implement `FakeOrganizerGateway`**

`public class FakeOrganizerGateway implements OrganizerGateway`, constructor `FakeOrganizerGateway(FakeBookingGateway fake)`. Every method body runs inside `synchronized (fake) { fake.sweep(); ... }`.

Rules (these are the whole of UC-05 and UC-06):

| Method | Allowed in | Checks, in this order (first failure is thrown) |
|---|---|---|
| `listEvents` | any | events with `owner.equals(organizerId)`, sorted by `dateTime` newest first; `sold` and `total` summed over tiers |
| `createEvent` | — | `name` trimmed: null/empty → `invalid("REQUIRED","name","Required")`, > 40 → `invalid("TOO_LONG","name","Up to 40 characters")`; `venue` same with 30; `eventDateTime` null → REQUIRED; `saleOpensAt` null → REQUIRED; `saleOpensAt <= now` → `SALE_IN_PAST`; `saleOpensAt >= eventDateTime` → `SALE_AFTER_EVENT`; `admitPerSecond` null → 12, `< 1` → `RATE_TOO_LOW`. New `Event(id, name, shortName, venue, null, eventDateTime, saleOpensAt)` with `draft = true`, `owner = organizerId`. id = slug of name (lower case, runs of non `[a-z0-9]` become `-`, trim `-`; empty → `event`), then `-2`, `-3`... until free. `shortName` = name upper case with a trailing ` BANGKOK` removed. |
| `getEvent` | any | not found or other owner → `GatewayException.notFound("Event")` |
| `updateDetails` | DRAFT, SCHEDULED, ON_SALE | else `conflict("EVENT_LOCKED", null, "This event can no longer be changed.", null)`. Null fields keep their value. Same name/venue checks. If `saleOpensAt` differs from the current one: in ON_SALE → `conflict("EVENT_LOCKED","saleOpensAt","The sale time cannot change once the sale is on.",null)`; otherwise SALE_IN_PAST check. Always: sale before event → else SALE_AFTER_EVENT. |
| `replaceTiers` | DRAFT | else `conflict("NOT_DRAFT", null, "Only a draft can do this.", null)`. More than 8 → `invalid("TOO_MANY_TIERS","tiers","Up to 8 tiers.")`. Each row `i` with label = trimmed name, or `"Tier " + (i+1)` when blank: blank name → `TIER_NAME_REQUIRED` field `tiers[i].name` `"<label> · Name is required."`; price null or < 0 → `PRICE_NEGATIVE` `tiers[i].price` `"<label> · Price cannot be below ฿0."`; total null or <= 0 → `SEATS_ZERO` `tiers[i].total` `"<label> · Seats must be more than 0."`. Then replace all tiers; tier id = slug of name, unique within the event. |
| `addTier` | DRAFT, SCHEDULED, ON_SALE | else EVENT_LOCKED. At 8 tiers → TOO_MANY_TIERS. Same row checks with fields `name`, `price`, `total` (no `tiers[i].` prefix). |
| `updateTier` | DRAFT, SCHEDULED, ON_SALE | else EVENT_LOCKED. Unknown tier → notFound("Tier"). Null fields keep their value. Row checks as `addTier`. In ON_SALE a price different from the current one → `conflict("PRICE_FROZEN","price","Prices are frozen while the sale is on, so no customer sees a price change mid-purchase.",null)`. New total < held + sold → `conflict("BELOW_HELD_SOLD","total", String.format("%s: %,d sold and %,d held. Cannot reduce below %,d.", name, sold, held, held + sold), held + sold)` (use `Locale.US`). |
| `publish` | DRAFT | else NOT_DRAFT. No tiers → `conflict("NO_TIERS", null, "An event needs at least one tier before it can be published.", null)`. Sets `draft = false`. |
| `setRate` | DRAFT, SCHEDULED, ON_SALE | else EVENT_LOCKED. null or < 1 → RATE_TOO_LOW. |
| `closeSale` | ON_SALE | else `conflict("NOT_ON_SALE", null, "Only a sale that is on can be closed.", null)`. Sets `closing = true`, then returns the view (CLOSING, or CLOSED when nothing is held). |
| `dashboard` | any | rows per tier, plus `all` = sums; `waiting(e)`, `revenue(e)`, `closedAt`. |

`OrgEvent` is built by one private method `view(Event e)`: `heldNow` = sum of `held`, `waiting` = `fake.waiting(e)`.

- [ ] **Step 6: Run the tests**

Run: `mvn test` → all pass (42 old + the new ones). Fix any old test the change broke **without changing what it asserts**; if one must change, stop and report why.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/th/ac/kmitl/sda/ticketqueue/web/OrganizerGateway.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/GatewayException.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/dto/OrgViews.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/dto/OrgRequests.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeBookingGateway.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeOrganizerGateway.java src/test/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeOrganizerTest.java
git commit -m "Organizer rules in the fake: drafts, tiers, publish, frozen prices, close, rate, dashboard"
```

---

### Task 3: Organizer messages over HTTP (Lane A)

**Implementer:** deepseek

**Files:**
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/OrgRoutes.java`, `src/test/java/th/ac/kmitl/sda/ticketqueue/web/OrgApiContractTest.java`
- Modify: `web/routes/Reply.java`, `web/WebServer.java`, `docs/09-web-api.md`

**Interfaces:**
- Consumes: `OrganizerGateway`, `OrgRequests`, `GatewayException.field` (Task 2).
- Produces the HTTP contract Lanes D and E call through `js/org/api.js`:

| ID | Method and path | Body | Answer `data` | Status |
|---|---|---|---|---|
| O-01 | `GET /api/org/events` | — | `OrgEventRow[]` | 200 |
| O-02 | `POST /api/org/events` | `EventDetails` | `OrgEvent` | 201 |
| O-03 | `GET /api/org/events/{eventId}` | — | `OrgEvent` | 200 |
| O-04 | `PATCH /api/org/events/{eventId}` | `EventDetails` | `OrgEvent` | 200 |
| O-05 | `PUT /api/org/events/{eventId}/tiers` | `TierList` | `OrgEvent` | 200 |
| O-06 | `POST /api/org/events/{eventId}/tiers` | `TierInput` | `OrgEvent` | 201 |
| O-07 | `PATCH /api/org/events/{eventId}/tiers/{tierId}` | `TierInput` | `OrgEvent` | 200 |
| O-08 | `POST /api/org/events/{eventId}/publish` | — | `OrgEvent` | 200 |
| O-09 | `PUT /api/org/events/{eventId}/rate` | `RateInput` | `OrgEvent` | 200 |
| O-10 | `POST /api/org/events/{eventId}/close` | — | `OrgEvent` | 200 |
| O-11 | `GET /api/org/events/{eventId}/dashboard` | — | `Dashboard` | 200 |

The organizer is named by the same `X-Customer` header (`Reply.customer(ctx)`); the page sends `chai`. Every error body gains `"field"`: `{serverNow, clockRate, error: {code, message, remaining, field}}`.

- [ ] **Step 1: Failing contract test**

`OrgApiContractTest` in the style of `ApiContractTest` (`JavalinTest.test(WebServer.create(fake, org, null), ...)`, header `X-Customer: chai`, parse with Jackson). Tests:

```java
@Test void listAnswersFourRows()            // GET /api/org/events -> 200, data.length 4, data[0].eventId "marshmello", keys exactly eventId,name,venue,eventDateTime,saleOpensAt,state,sold,total
@Test void createAnswers201Draft()          // POST body {"name":"Test Night Bangkok","venue":"Impact Arena","eventDateTime":<EVENT>,"saleOpensAt":<SALE>,"admitPerSecond":12} -> 201, data.state "DRAFT", data.tiers []
@Test void refusalCarriesTheField()         // POST with saleOpensAt START-1 -> 400, error.code "SALE_IN_PAST", error.field "saleOpensAt", error.message "The sale must open in the future."
@Test void fullWizardRoundTrip()            // create -> PUT tiers (3 rows) -> POST publish -> GET event: state "SCHEDULED", tiers.length 3, tiers[0] keys exactly tierId,name,price,total,held,sold,remaining
@Test void belowHeldSoldCarriesTheMinimum() // after /dev advance 60 and a held 2 VIP by kenji through /api: PATCH /api/org/events/the-1975/tiers/vip {"total":485} -> 409, code BELOW_HELD_SOLD, remaining 486, field "total"
@Test void dashboardShape()                 // GET .../the-1975/dashboard -> keys exactly eventId,name,state,waiting,admitPerSecond,tiers,all,revenue,closedAt; all.tierId "all"
@Test void otherOrganizerGets404()          // X-Customer: someone, GET /api/org/events/the-1975 -> 404 NOT_FOUND
@Test void customerApiStillHidesDrafts()    // after create, GET /api/events (X-Customer kenji) has no "test-night-bangkok"
```

Run: `mvn -q test -Dtest=OrgApiContractTest` → fails (no `create(…, …, …)`, no routes).

- [ ] **Step 2: Routes and wiring**

`Reply.error`: add `err.put("field", e.field);` after `remaining`.

`OrgRoutes.register(Javalin app, BookingGateway clock, OrganizerGateway org)` — `clock` is only used for `Reply.ok` (server time and clock rate). One line per row of the table above, in the style of `HoldRoutes`, with the O-number comment. Bodies: `ctx.bodyAsClass(EventDetails.class)`, `TierList.class` (pass `req.tiers()`, null → empty list), `TierInput.class`, `RateInput.class`.

`WebServer`:

```java
public static Javalin create(BookingGateway gateway, DevControls dev) {
    return create(gateway, null, dev);
}

public static Javalin create(BookingGateway gateway, OrganizerGateway organizer, DevControls dev) {
    // ... existing body ...
    if (organizer != null) {
        OrgRoutes.register(app, gateway, organizer);
    }
    // ... dev routes, return app
}
```

In `main`: `OrganizerGateway organizer = new FakeOrganizerGateway(fake);` next to the THE SWITCH lines (comment: `// THE SWITCH, organizer side: organizer = new CoreOrganizerGateway(...)`), and `create(gateway, organizer, dev)`.

- [ ] **Step 3: Run all tests**

Run: `mvn test` → all pass. Run: `./run.sh test` → 19 passed.

- [ ] **Step 4: Document the messages**

`docs/09-web-api.md`: add a section `## Organizer messages (O-01 to O-11)` after the customer messages: the table above, the four request shapes, the three reply shapes (copy the record fields), the state list `DRAFT → SCHEDULED → ON_SALE → CLOSING → CLOSED` (plus `CANCELLED`), and a refusal table with every code, its field and its exact message from Task 2 Step 5. Add `field` to the error envelope description. Each row names its use case (O-01 UC-05/06 trigger, O-02 UC-05 steps 1–2, O-05 UC-05 steps 3–4, O-08 UC-05 steps 5–6, O-04/06/07/09/10 UC-06, O-11 UC-07) and FR (FR-23 to FR-34).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/OrgRoutes.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/Reply.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/WebServer.java src/test/java/th/ac/kmitl/sda/ticketqueue/web/OrgApiContractTest.java docs/09-web-api.md
git commit -m "Organizer messages O-01 to O-11 over HTTP, with the field that caused a refusal"
```

---

### Task 4: Customer shell at desktop size — SCR-01 to SCR-04 (Lane B)

**Implementer:** deepseek

**Files:**
- Modify: `src/main/resources/public/js/screens/shell.js`, `src/main/resources/public/css/browse.css`

**Interfaces:**
- Consumes: `topNav` is drawn by the router already (do not draw a nav in screens). `sheet()` already draws a centred dialog. Data shapes unchanged: `api.events()`, `api.orders()`, `api.me()`.

Design: `design/Ticket App Web.dc.html` lines 47–108 (SCR-01), 109–145 (SCR-02/03), 146–160 (SCR-04), 506–519 (sign-out dialog). Script block from line 520 for copy such as result labels.

- [ ] **Step 1: SCR-01 Events**

Rebuild the `/events` screen as in the design. Remove the carousel, its dots, arrows, counter and the two module-level carousel listeners.
1. Container `max-width:1200px; margin:0 auto; padding` as in line ~49. Title `Events` 600 44px, sub `Bangkok`. On the right a search box (`Search events or venues`, magnifier icon, clear button `×` shown only when there is text).
2. Featured hero: the first event in the list (the API sorts by sale time, so The 1975) as one wide card, height and radius from the design, artwork as background, dark gradient from the left, lime pill (`ON SALE TODAY` when the sale opens today in Bangkok or is on; keep the existing pill rules for other states), big upper-case name in two lines (`THE 1975` / `BANGKOK` — `shortName` then `Bangkok` upper case), `Impact Arena · 14 Nov 2026`, lime line `Sale opens 10:00, from ฿1,900` (once on sale: `On sale now, from ฿1,900`; sold out / closed / cancelled: keep today's wording). On hover: image scales to 1.07, a `View event →` call to action fades in (design `data-feat*` hover rules, lines 9–10 of the `<style>`).
3. `ALSO ON SALE` label, then a 3-column grid of the other events: image (aspect from design), name, `Thunder Dome · 22 Nov`, `From ฿1,200`. Hover: image scale 1.08, price turns lime.
4. Search filters by event name or venue, case-insensitive, **without re-rendering** (re-rendering would lose focus): a module-level `input` listener on `[data-search]` hides non-matching cards with `el.hidden`, hides the hero when it does not match, and shows the result label and the no-results block exactly as the design (lines 84–91). The clear button empties the box and shows all again.
5. Click on any card → `#/event/<id>`. Events whose `artwork` is `null` (created by the organizer) show a `--s2` panel with the name in place of the image.
6. SCR-01b (no events): the design block at lines 61–67.

- [ ] **Step 2: SCR-02/03 Tickets, SCR-04 Account**

Copy the design layouts (lines 109–160): Tickets page with `UPCOMING` and `PAST` sections of order cards (keep current data and click targets), empty state for SCR-03. Account: narrow column 640 px, avatar 64 px, name, masked email, rows `Help` and `Sign out` (coral), sign-out dialog via `sheet()` with the design copy (lines 506–519).

- [ ] **Step 3: Styles**

All new rules go in `browse.css`. Delete the phone rules in `browse.css` that no screen in this file uses any more (carousel, `.feature` phone sizes). Do not touch `app.css`.

- [ ] **Step 4: Check**

Start the server (`PORT=7071 mvn -q compile exec:java` in the worktree). Run `BASE=http://localhost:7071 node src/test/e2e/walk.mjs 01a 02 03 04` → four PNGs in `target/shots-web`, no errors. In Playwright at 1440×900 open `/#/events`: typing `uob` leaves only Alan Walker and the label from the design; clearing shows all four; no console errors. At 1024 px wide `document.documentElement.scrollWidth <= 1024`.
Run: `mvn test` → all pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/public/js/screens/shell.js src/main/resources/public/css/browse.css
git commit -m "SCR-01 to SCR-04 at desktop size: hero, events grid, search, tickets, account"
```

---

### Task 5: Event, queue and select at desktop size — SCR-05 to SCR-08 (Lane B)

**Implementer:** deepseek

**Files:**
- Modify: `public/js/screens/event.js`, `public/js/screens/queue.js`, `public/js/screens/select.js`, `public/css/browse.css`

Design lines: SCR-05 161–215, SCR-06 216–251, SCR-07 252–271 (results block, already styled by `statusScreen`), SCR-08 272–318.

Keep every `enter`, `poll`, `actions` and `onZero` exactly as it is — only `view` markup and CSS change. Keep every variant (05a–f, 06a–e, 07, 08a–g) and every existing `data-action` name.

- [ ] **Step 1: SCR-05 Event** — `‹ All events` link top left. Two columns (`.split`): left the poster card (artwork, upper-case name 600 ~64px, `IMPACT ARENA · 14 NOV 2026` in `--fm`) and under it `TIERS` with a list card of tier name and price; right a status card with the variant content (05a countdown to sale, 05b `ON SALE NOW` with the pulsing dot, 05c `YOU ARE IN THE QUEUE` + position, 05d `SOLD OUT`, 05e `SALE CLOSED`, 05f `EVENT CANCELLED` — copy from lines 185–195), then the main button (`Join the queue`, `Back to my place`, or the quiet `Back to events`), `Maximum 4 tickets per person`, a hairline, and `Everyone waits in the same line.` / `Joining early does not help.`.
- [ ] **Step 2: SCR-06 Queue** — centred column from lines 216–251: welcome-back banner (06c), joining state (06e), big position number, progress bar, ETA line, reconnecting state (06d dims the number). Keep the existing 15-second stale rule.
- [ ] **Step 3: SCR-08 Select** — lines 272–318: left `You are in` / `Choose your tickets. Nothing is reserved yet.`, the coral banner for 08e/08f, tier rows full width (radio circle, name, `only 16 left` coral when low, `350 left`, price; selected row lime border and lime price); right a summary card: `How many` with the − / + stepper, `Maximum 4 per person`, `1 × VIP` and the total, `Hold these seats` (spinner while holding), `Holding takes them out of the sale for 10 minutes`. 08g shows the quiet `Back to events` instead.
- [ ] **Step 4: Check** — `BASE=http://localhost:7071 node src/test/e2e/walk.mjs 05a 05b 05c 05d 05e 05f 06a 06b 06c 07 08a 08d 08g` → PNGs, no errors. Click through by hand in Playwright: `/?dev#/event/the-1975` → `+1 min` → Join → `+6 min` → select VIP, `+` once → `Hold these seats` lands on `#/hold/...`. `mvn test` passes.
- [ ] **Step 5: Commit**

```bash
git add src/main/resources/public/js/screens/event.js src/main/resources/public/js/screens/queue.js src/main/resources/public/js/screens/select.js src/main/resources/public/css/browse.css
git commit -m "SCR-05 to SCR-08 at desktop size: two-column event and select, centred queue"
```

---

### Task 6: Hold and checkout at desktop size — SCR-09 to SCR-12 (Lane C)

**Implementer:** deepseek

**Files:**
- Modify: `public/js/screens/hold.js`, `public/js/screens/pay.js` (the SCR-12 checkout screen only), `public/css/purchase.css`

Design lines: the "Timed" block 319–414 (SCR-09 when `s.held`, SCR-12 when `showMethods`), results 252–271 (SCR-10, SCR-11 use `statusScreen`), release dialog 494–505.

Keep every `enter`, `poll`, `actions`, `onZero` and `data-action` name; change `view` markup and CSS only.

- [ ] **Step 1: SCR-09 Seats held** — two columns: left `Seats held` / sub line, the order summary card (`orderSummary`), the lime info card (line 345); right the countdown card (big `09:50` in `--fm` lime, label `Time to pay`, bar), `Continue to payment`, then the quiet `Release these seats`. SCR-09b: the release dialog via `sheet()` with the copy at lines 494–505 (`Release these seats?`, `The 2 VIP seats go back on sale straight away. To buy again you join the back of the line.`, `Time to pay` + live countdown, coral outline `Release seats`, lime `Keep my seats`). The countdown in the dialog is a `[data-countdown]` element so the router ticks it.
- [ ] **Step 2: SCR-12 Checkout** — left: title, coral banners for 12c / 12d (line 334), summary card with the promotion row (12b), payment methods list (lines 346–357: tile, label, line, selected check, unavailable dimmed); right: countdown card and the `Pay ฿…` button.
- [ ] **Step 3: SCR-10, SCR-11** — confirm they render with the new `statusScreen` look and the design copy (results block lines 252–271).
- [ ] **Step 4: Check** — `BASE=http://localhost:7071 node src/test/e2e/walk.mjs 09a 10 11 12a 12b 12c 12d` → PNGs, no errors. In Playwright, on `#/hold/<id>` click `Release these seats`: the centred dialog shows, its countdown moves, `Keep my seats` closes it, `Escape` closes it. `mvn test` passes.
- [ ] **Step 5: Commit**

```bash
git add src/main/resources/public/js/screens/hold.js src/main/resources/public/js/screens/pay.js src/main/resources/public/css/purchase.css
git commit -m "SCR-09 to SCR-12 at desktop size: held seats, release dialog, checkout"
```

---

### Task 7: Paying, confirmed and tickets at desktop size — SCR-13 to SCR-18 (Lane C)

**Implementer:** deepseek

**Files:**
- Modify: `public/js/screens/pay.js` (SCR-13, 14, 15, 18), `public/js/screens/tickets.js`, `public/css/purchase.css`

Design lines: Timed block 319–414 (`showQr` 358 for SCR-13, `showCardPanel` 364 for SCR-14, `s.confirming` 324 for SCR-15, footers 396–412), SCR-16 415–443, SCR-17 444–489, results 252–271 (SCR-18).

- [ ] **Step 1: SCR-13 PromptPay** — left: QR panel (white `--qr` card, the `qr()` SVG, amount, merchant line), declined banner (13b); right: countdown and `I have paid` (spinner while working).
- [ ] **Step 2: SCR-14 Card** — the simulated provider panel (lines 364–391), declined banner (14b), `Pay ฿…`.
- [ ] **Step 3: SCR-15 Confirming** — lines 324–330 and 407–412 (`Do not close this page` note); 15b bank-slow copy.
- [ ] **Step 4: SCR-16 Confirmed** — lines 415–443: check ring, title, order summary, ticket stubs side by side, `Open ticket 1` button.
- [ ] **Step 5: SCR-17 Ticket** — lines 444–489: `‹ My tickets`, `THE 1975`, `TICKET 1 OF 2 · VIP`; left the ticket card (event artwork behind, QR on white, lime ticket id, prev/next round buttons and dots, `Ticket 1 of 2 · use ← →`; left/right arrow keys switch tickets through a module-level `keydown` listener that clicks the prev/next button); right the details card (Holder, Event, Venue, Date `Saturday 14 November, 19:00`, Tier) and the `Single use.` card. 17b cancelled and 17c past variants from lines 451–462. `artwork` may be `null`: then no backdrop image.
- [ ] **Step 6: SCR-18** — renders with `statusScreen` and the design copy.
- [ ] **Step 7: Check** — `BASE=http://localhost:7071 node src/test/e2e/walk.mjs 13a 13b 14a 14b 15a 15b 16 17a 17b 17c 18` → PNGs, no errors. Full purchase by clicking in Playwright from `/?dev#/events` to an open ticket works. `mvn test` passes.
- [ ] **Step 8: Commit**

```bash
git add src/main/resources/public/js/screens/pay.js src/main/resources/public/js/screens/tickets.js src/main/resources/public/css/purchase.css
git commit -m "SCR-13 to SCR-18 at desktop size: PromptPay, card, confirming, confirmed, ticket"
```

---

### Task 8: My events and Details — SCR-30, SCR-31 (Lane D)

**Implementer:** deepseek

**Files:**
- Modify: `public/js/org/list.js`, `public/js/org/wizard.js`, `public/css/org-wizard.css`

**Interfaces:**
- Consumes: `org` from `js/org/api.js`, `chip`, `field`, `toEpoch`, `dateInput`, `timeInput`, `dateTime` from `js/org/parts.js`, `html`, `num`, `button`, `banner` from `js/parts.js`, `ApiError`, `serverNow` from `js/api.js`. Server shapes: `OrgEventRow`, `OrgEvent` and the refusal codes in Task 3 / `docs/09-web-api.md`.
- Produces routes: `#/events` (list.js), `#/new` and `#/event/<id>/details` (wizard.js). Task 9 adds `#/event/<id>/tiers` and `#/event/<id>/review` to `wizard.js`.

Design: `design/Organizer Console.dc.html` lines 50–81 (SCR-30a, 30b), 82–92 (step indicator), 93–202 (SCR-31). Copy rules: `docs/08-screen-requirements.md` SCR-30 and SCR-31.

- [ ] **Step 1: SCR-30** — `list.js`, route `/^\/events$/`. `enter` loads `org.events()`. Title `My events`, sub `3 events` (`1 event` when one), lime `Create event` → `#/new`. Table columns `EVENT · VENUE · EVENT DATE · SALE OPENS · STATE · SOLD` (dates with `dateTime`, state with `chip`, sold `4,810 / 6,200`, `—` for DRAFT). Row click (`data-action="go"` with `data-to`) → `#/event/<id>`. Rows hover `--s2`. 30b: `No events yet` / `Create your first event to start selling.` + `Create event`. Poll every 5000 ms.
- [ ] **Step 2: SCR-31** — `wizard.js`. A shared `steps(n)` helper draws `1 Details — 2 Tiers — 3 Review` (done steps get a lime check, the active one a lime ring; lines 82–92). Two columns: the form card (left, 740 px) and the `ADMISSION RATE` helper text (right) exactly as the design: `How many people the queue lets in each second. Work it out from your seats and the 10-minute hold: 7,500 seats ÷ 600 seconds of hold ≈ 12 per second. Faster than this and people get in only to find every seat already held.` / `You can change it later, even while the sale is on.` Fields: `Event name` (counter `0/40`, maxlength 40, placeholder `The 1975 Bangkok`), `Venue` (`0/30`, placeholder `Impact Arena`), `Event date and time` (date + time), `Sale opens` (date + time with seconds, label note `to the second`), `Admission rate` (number, default 12, suffix `per second`). Footer: quiet `Cancel` → `#/events`, lime `Save and add tiers`.
- [ ] **Step 3: Live checks without re-render** — keep the typed values in `ctx.state.form` through a module-level `input` listener on `#app [name]` (update the counter text and the error lines in place, never call `render` on input, so focus stays). Live client rules, same text as the server: empty → `Required`; sale time `<= serverNow()` → `The sale must open in the future.` (31b); sale `>=` event time → `The sale must open before the event.`; rate `< 1` → `Must be at least 1.`. Any error disables `Save and add tiers`. Show `Required` only after the field was touched or after a save attempt.
- [ ] **Step 4: Save** — `#/new`: `org.create(details)` then `ctx.go('#/event/<id>/tiers')`. `#/event/<id>/details` (reached by `Back` from Tiers): loads `org.event(id)`, fills the form, saves with `org.update`. On `ApiError` with status 400/409: put `e.message` under the field named `e.field` and re-enable the button.
- [ ] **Step 5: Check** — server from this lane's worktree (`PORT=7072`). `BASE=http://localhost:7072 node src/test/e2e/org-walk.mjs 30a 31a` → PNGs, no errors. By hand in Playwright at `/organizer.html#/new`: type a sale date of 2026-09-30 → the coral `The sale must open in the future.` shows and the button is disabled; fix it, fill the rest, save → lands on `#/event/<id>/tiers`; back on `#/events` the new row shows `DRAFT` and `—`. `mvn test` passes.
- [ ] **Step 6: Commit**

```bash
git add src/main/resources/public/js/org/list.js src/main/resources/public/js/org/wizard.js src/main/resources/public/css/org-wizard.css
git commit -m "SCR-30 My events and SCR-31 event details with live checks"
```

---

### Task 9: Tiers and Review & publish — SCR-32, SCR-33 (Lane D)

**Implementer:** deepseek

**Files:**
- Modify: `public/js/org/wizard.js`, `public/css/org-wizard.css`

Design: lines 203–233 (SCR-32), 234–266 (SCR-33). Copy: `docs/08` SCR-32, SCR-33.

- [ ] **Step 1: SCR-32** — route `/^\/event\/([^/]+)\/tiers$/`. `enter`: `org.event(id)`; if not `DRAFT` → `ctx.go('#/event/<id>', {replace:true})`. Title `Tiers · <name>`, sub `Each tier is a pool of seats at one price. Up to 8 tiers.` Editable table `NAME · PRICE (฿) · SEATS` with `Remove` per row; with 0 tiers one empty row and the line `Add at least one tier.`; `+ Add tier` (disabled at 8); total `7,500 seats in all`. Live checks per row (same messages as the server: `Zone B · Seats must be more than 0.`, `VIP · Price cannot be below ฿0.`, `Tier 2 · Name is required.`): coral border on the bad cell, message under the row (32b), `Review` disabled. Edits live in `ctx.state.rows` through a module-level `input` listener (no re-render on typing); add/remove rows do re-render. Footer `Back` → `#/event/<id>/details`, lime `Review` → `org.replaceTiers(id, rows)` then `#/event/<id>/review`; server refusals land under the row from `e.field` (`tiers[1].total`).
- [ ] **Step 2: SCR-33** — route `/^\/event\/([^/]+)\/review$/`. Summary card (name, venue, event date, sale opens, admission rate), read-only tier table with the total, the line `After publishing, customers see this event. Prices are frozen once the sale opens.`, `Back` → tiers, lime `Publish` (spinner `Publishing`) → `org.publish(id)` then `#/event/<id>/published`. 33b: no tiers → `Publish` disabled and `An event needs at least one tier before it can be published.`
- [ ] **Step 3: Check** — `BASE=http://localhost:7072 node src/test/e2e/org-walk.mjs 32a 33a 33b` → PNGs. By hand: create → tiers VIP/Zone A/Zone B → set Zone B seats to 0 → message and disabled `Review` → fix → Review → Publish → lands on `#/event/<id>/published` (SCR-34 comes from Lane E; until it is merged the page may say "not available", which is fine here). Then `/#/events` as Kenji (`index.html`) does **not** show it before publish and does after. `mvn test` passes.
- [ ] **Step 4: Commit**

```bash
git add src/main/resources/public/js/org/wizard.js src/main/resources/public/css/org-wizard.css
git commit -m "SCR-32 tiers with live checks and SCR-33 review and publish"
```

---

### Task 10: Event overview and its dialogs — SCR-34 to SCR-39 (Lane E)

**Implementer:** deepseek

**Files:**
- Modify: `public/js/org/overview.js`, `public/js/org/dialogs.js`, `public/css/org-manage.css`

**Interfaces:**
- Consumes the same helpers as Task 8, plus `sheet` from `js/parts.js` for dialogs.
- Produces routes `#/event/<id>` and `#/event/<id>/published` (same screen with the `Published.` banner). Links to `#/event/<id>/live` (Task 11) and `#/event/<id>/tiers` (Lane D).

Design: lines 267–304 for SCR-34a and 34b. **No design** for 34c–f and SCR-35 to 39: build them from `docs/08` §8.8 (SCR-34 state table, SCR-35 to SCR-39) word for word, using the same table/card/button/field styles and the customer dialog look (`design/Ticket App Web.dc.html` lines 494–519).

- [ ] **Step 1: SCR-34** — `enter`: `org.event(id)`; poll every 5000 ms. Banner on `/published`: `Published.` + `The sale opens 1 Oct 2026, 10:00. Customers can see the event now.` (date from `saleOpensAt`). Title + `chip`; sub-line in `--fm`: `Impact Arena · 14 Nov 2026, 19:00 · Sale opens 1 Oct 2026, 10:00` and lime `Edit details` (hidden in CLOSING, CLOSED, CANCELLED). Action bar per state: DRAFT lime `Continue setup` → tiers; SCHEDULED disabled `Live dashboard` + `Opens when the sale starts`; ON_SALE lime `Live dashboard` → live and coral outline `Close the sale`; CLOSING lime `Live dashboard` + banner `Closing.` `<heldNow> seats are still inside someone's 10 minutes. The sale closes when they finish.`; CLOSED `Final figures` → live; CANCELLED coral banner `This event was cancelled.` and nothing else. Tier table `TIER · PRICE · TOTAL · HELD · SOLD · REMAINING ·` with `Edit` (lime link) where allowed; `No tiers yet. Add one to publish.` when empty; `+ Add tier` where allowed. Queue card: `QUEUE`, `Admission rate`, big rate + `per second`, `Change` where allowed. **Not allowed means hidden**, per the docs/08 table (a to f).
- [ ] **Step 2: Dialogs in `dialogs.js`** — export one function per dialog that returns `html` for `sheet()` plus the matching action handlers. Each dialog's open/closed state and typed values live in `ctx.state.dialog`; typing uses a module-level `input` listener (no re-render on typing); refusals from the server show under the field from `e.field`.
  - SCR-35 `Edit details`: name, venue, event date and time, sale opens (disabled unless DRAFT or SCHEDULED), `Cancel` · `Save` → `org.update`.
  - SCR-36 `Edit VIP`: Name, Price (฿), Total seats with `476 sold · 12 held · 12 remaining` under it. 36b ON_SALE: price read-only with a lock icon and `Prices are frozen while the sale is on, so no customer sees a price change mid-purchase.` 36c: total below held + sold → coral `VIP: 476 sold and 12 held. Cannot reduce below 488.` live, Save disabled. Save → `org.updateTier`.
  - SCR-37 `Add tier`: Name, Price (฿), Seats, same row checks as SCR-32; in ON_SALE the line `The new tier goes on sale straight away.`; button `Add tier` → `org.addTier`.
  - SCR-38 `Admission rate`: number + `per second`; helper worked from the event's own numbers: `<total seats> seats ÷ 600 seconds of hold ≈ <round(total/600)> per second. Faster than this and people get in only to find every seat already held.`; ON_SALE only: `At <rate> per second, the <waiting> people now waiting are through in about <ceil(waiting/rate/60)> minutes.`; `< 1` → `Must be at least 1.`; Save → `org.setRate`.
  - SCR-39 `Close the sale?`: `No one new will be let in from the queue.` / `<heldNow> seats are inside someone's 10 minutes. Those customers keep their time and can still pay.` / `This cannot be undone.`; lime `Keep selling` · coral outline `Close the sale` → `org.close` → stays on SCR-34 (now CLOSING or CLOSED).
- [ ] **Step 3: Check** — server on `PORT=7073`. `BASE=http://localhost:7073 node src/test/e2e/org-walk.mjs 34a 34b 34c 34d 34e 34f` → PNGs, no errors. By hand at `/organizer.html?dev#/event/the-1975` after `+1 min`: Edit VIP price → refused with the frozen line; seats 400 → refused with the numbers; Admission rate 24 → card shows 24; Close the sale → CLOSING or CLOSED, and Edit links are gone. `mvn test` passes.
- [ ] **Step 4: Commit**

```bash
git add src/main/resources/public/js/org/overview.js src/main/resources/public/js/org/dialogs.js src/main/resources/public/css/org-manage.css
git commit -m "SCR-34 event overview by state, SCR-35 to SCR-39 dialogs"
```

---

### Task 11: Live dashboard — SCR-40 (Lane E)

**Implementer:** deepseek

**Files:**
- Modify: `public/js/org/dashboard.js`, `public/css/org-manage.css`

No design. Build from `docs/08` SCR-40 word for word, in the organizer look.

- [ ] **Step 1: Screen** — route `/^\/event\/([^/]+)\/live$/`. `enter` and `poll` (every 2000 ms, which meets FR-34's 5 s): `org.dashboard(id)`. Title `The 1975 Bangkok · Live` with `liveIndicator()` on the right (40c: `· Final`, no dot, line `Sale closed at 10:31:05.` from `closedAt` in Bangkok time with seconds). Queue row with two big figures: `Waiting` **31,208** and `Admitting` **12 / second**. Tier table `TIER · TOTAL · HELD · SOLD · REMAINING` with an `All` row in bold; each tier row has a thin 3-part bar (sold `--tx`, held `--ac`, remaining `--s3`, widths in %); remaining 0 shows coral `Sold out`. Note `Held = inside a customer's 10 minutes. Not sold, not available.` Revenue `Revenue, confirmed orders only` **฿20,350,000** style (`baht`). Footer link `Back to event` → `#/event/<id>`.
- [ ] **Step 2: Numbers ease, no flashing** — when a figure changes, add a class that fades it from `--ac` to `--tx` over 600 ms. No layout jump: figures use `tabular-nums`.
- [ ] **Step 3: 40b stale** — this screen sets `ownOffline: true` and, on every tick of its own 1-second interval started in `enter` (cleared when `ctx.alive()` turns false), reads `contactAge()`: over 15000 ms → every figure gets class `dim` (opacity .45) and a coral banner `These figures are <n> seconds old.` `We are trying to reconnect.` with the age counting up.
- [ ] **Step 4: Check** — `BASE=http://localhost:7073 node src/test/e2e/org-walk.mjs 40a 40c` → PNGs. In Playwright at `?dev#/event/the-1975/live` after `+6 min`: Waiting reads 31,208 (at 10:06:20) and moves down by about 12 each second at ×1; stop the server for 20 s → stale banner with a growing age; start it again → banner goes. `mvn test` passes.
- [ ] **Step 5: Commit**

```bash
git add src/main/resources/public/js/org/dashboard.js src/main/resources/public/css/org-manage.css
git commit -m "SCR-40 live dashboard: queue, held/sold/remaining, revenue, stale and final variants"
```

---

### Task 12: Join, check everything, update the docs

**Implementer:** claude

**Files:**
- Modify: `docs/08-screen-requirements.md`, `docs/02-requirements.md`, `src/README.md`, `AGENTS.md` (only §1 table status line for deliverable 4 if needed)

- [ ] **Step 1: Merge** lanes B, C, D, E into `feat/desktop-web` (D and E already contain A). Resolve nothing by hand in owned files; a conflict means a lane broke the ownership rule — stop and report.
- [ ] **Step 2: All suites** — `mvn test` (all pass, count recorded) and `./run.sh test` (19 passed).
- [ ] **Step 3: Screenshot walks** — `node src/test/e2e/walk.mjs` (every customer shot) and `node src/test/e2e/org-walk.mjs` (every organizer shot). Compare each with the design at the same variant (`design/Ticket App Web.dc.html` menu, `design/Organizer Console.dc.html` menu). List every visible difference; fix small ones here, send larger ones back to the owning lane's files.
- [ ] **Step 4: Cross-side story check** — Chai creates "Test Night Bangkok", publishes, `+1 min` past its sale time is not possible in the story, so instead on The 1975: Chai adds `Zone C ฿990 × 100` while on sale → Kenji's SCR-08 shows Zone C → Kenji holds 2 → Chai's dashboard shows Zone C held 2 → Chai closes the sale → Kenji's hold still pays → event becomes CLOSED, dashboard `· Final`.
- [ ] **Step 5: Docs**
  - `docs/08` §8.2: heading `Customer app — desktop web`; §8.3 Devices: customer `Desktop browser`, **1440 × 900**, "must work from 1024 px wide with no sideways scrolling", why: `Kenji has the page open on a laptop at 09:59.`; drop the 360 px rule; design file pointers `design/Ticket App Web.dc.html` and `design/Organizer Console.dc.html`.
  - `docs/08` SCR-01: replace the carousel item with the hero + grid + search (mark *changed 11 Oct 2026*).
  - `docs/08` §8.8: a note under SCR-34 c–f, SCR-35 to SCR-40: *No visual design yet. Built from this text in the organizer design's look (11 Oct 2026).*
  - `docs/02` Assumptions: `ASM-10` *Customers buy from a desktop browser. There is no phone layout (11 Oct 2026).* with its traceability line.
  - `src/README.md`: the organizer page is at `http://localhost:7070/organizer.html` (`?dev` for demo controls).
- [ ] **Step 6: Commit**

```bash
git add docs/08-screen-requirements.md docs/02-requirements.md src/README.md
git commit -m "Desktop web and organizer console: docs follow the new design"
```

---

## Self-review notes

- **Coverage:** UC-01 to UC-04 (SCR-01–18) → Tasks 4–7. UC-05 (FR-23–25) → Tasks 2, 3, 8, 9. UC-06 (FR-26–30) → Tasks 2, 3, 10. UC-07 (FR-31–34) → Tasks 2, 3, 11. Devices change → Tasks 1, 12.
- **Known limits, on purpose:** the fake's crowd number exists only for The 1975; other events count real tokens. A token whose admit time passed while the sale was closing stays WAITING (FR-29 wins). No organizer sign-in (ASM-01 covers accounts).
- **Names used across tasks:** `startApp`, `topNav`, `orgTopBar`, `chip`, `field`, `toEpoch`, `dateInput`, `timeInput`, `dateTime`, `org.*` (11 calls), `OrgEventRow`, `OrgTier`, `OrgEvent`, `Dashboard`, `EventDetails`, `TierInput`, `TierList`, `RateInput`, `GatewayException.field` / `ApiError.field`.
