// SCR-31 Create event · 1 Details — the same form edits the details when the organizer comes back from Tiers.
import { org } from './api.js';
import { ApiError, serverNow } from '../api.js';
import { html, button, num } from '../parts.js';
import { field, toEpoch, dateInput, timeInput, dateTime } from './parts.js';

const BLANK = { name: '', venue: '', evDate: '', evTime: '', saleDate: '', saleTime: '', rate: '12' };
const MAX = { name: 40, venue: 30 };
// One error line per group: the event and the sale each have a date picker and a time picker.
const GROUP = { name: 'name', venue: 'venue', evDate: 'ev', evTime: 'ev', saleDate: 'sale', saleTime: 'sale', rate: 'rate' };
// A server refusal names an EventDetails field; this is the error line that field belongs under.
const FIELD = { name: 'name', venue: 'venue', eventDateTime: 'ev', saleOpensAt: 'sale', admitPerSecond: 'rate' };
// The four pickers: which group each one's error line belongs to, and whether its popup opens upward.
const PICKS = { evDate: { group: 'ev', up: false }, evTime: { group: 'ev', up: false }, saleDate: { group: 'sale', up: true }, saleTime: { group: 'sale', up: true } };

const STEPS = ['Details', 'Tiers', 'Review'];
/** 1 Details — 2 Tiers — 3 Review. active is the step on screen now: 0 Details, 1 Tiers, 2 Review. */
const steps = (active) => html`<div class="wiz-steps">${STEPS.map((label, i) => {
  const done = i < active;
  return html`<div class="wiz-step">
    <span class="wiz-mark"><span class="wiz-dot ${done ? 'done' : i === active ? 'on' : ''}">${done ? '✓' : i + 1}</span>
      <span class="wiz-lbl ${i === active ? 'on' : ''}">${label}</span></span>
    ${i < STEPS.length - 1 ? html`<span class="wiz-bar"></span>` : ''}
  </div>`;
})}</div>`;

const at = (date, time) => { const ms = toEpoch(date, time); return ms !== null && Number.isFinite(ms) ? ms : null; };

// ----- Bangkok dates for the pickers. The clock is the server's, never the device's (Asia/Bangkok, UTC+7). -----
const BKK = 7 * 3600_000;
const pad = (n) => String(n).padStart(2, '0');
const MON = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MONL = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const DAYNAME = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const WDF = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

/** The Bangkok calendar day of a moment. */
const bkkDay = (ms) => { const x = new Date(ms + BKK); return { y: x.getUTCFullYear(), m: x.getUTCMonth(), d: x.getUTCDate() }; };
/** "2026-11-14" as three numbers. The string is already a Bangkok date, so no zone is added. */
const fromIso = (v) => { const [y, m, d] = v.split('-').map(Number); return { y, m: m - 1, d }; };
const isoOf = (y, m, d) => `${y}-${pad(m + 1)}-${pad(d)}`;
/** serverNow() as the refusal's time: "1 Oct 2026, 10:00", with ":07" only when the seconds are not zero. */
const nowText = () => { const ms = serverNow(); const s = new Date(ms + BKK).getUTCSeconds(); return dateTime(ms) + (s ? `:${pad(s)}` : ''); };

/** The same rules, in the same words, as the design (docs/08 §8.8). Sale first, then the event. */
function errors(f) {
  const e = {};
  if (!f.name.trim()) e.name = 'Give the event a name.';
  if (!f.venue.trim()) e.venue = 'Add the venue.';
  const sale = at(f.saleDate, f.saleTime);
  const ev = at(f.evDate, f.evTime);
  if (sale === null) e.sale = 'Pick the date and time the sale opens.';
  else if (sale <= serverNow()) e.sale = `That time has already passed. Pick a time after ${nowText()}.`;
  if (ev === null) e.ev = 'Pick the date and time of the event.';
  else if (sale !== null && ev <= sale) e.ev = 'The event has to be after the sale opens.';
  if (!/^\d+$/.test(String(f.rate).trim()) || Number(f.rate) < 1) e.rate = 'Let in at least 1 person per second.';
  return e;
}

const fromEvent = (e) => ({
  name: e.name, venue: e.venue,
  evDate: dateInput(e.eventDateTime), evTime: timeInput(e.eventDateTime),
  saleDate: dateInput(e.saleOpensAt), saleTime: timeInput(e.saleOpensAt, true),
  rate: String(e.admitPerSecond),
});

const toDetails = (f) => ({
  name: f.name.trim(), venue: f.venue.trim(),
  eventDateTime: at(f.evDate, f.evTime), saleOpensAt: at(f.saleDate, f.saleTime),
  admitPerSecond: Number(f.rate),
});

// ----- the date and time pickers (design lines 107–186: a trigger, a calendar, a column popup) -----

/** "Sat 14 Nov 2026", the way the calendar's trigger shows a chosen day. */
const dateLabel = (v) => { const { y, m, d } = fromIso(v); return `${DAYNAME[new Date(Date.UTC(y, m, d)).getUTCDay()]} ${d} ${MON[m]} ${y}`; };

/** The trigger: the value, or the empty text, and a chevron. Full width, 50 tall (design line 111). */
const trigger = (st, k, empty) => {
  const v = st.form[k];
  const open = st.pk === k;
  return html`<button type="button" class="pk${v ? ' set' : ''}${open ? ' on' : ''}" data-action="pk" data-pk="${k}" aria-expanded="${open ? 'true' : 'false'}">
    <span>${k.endsWith('Date') ? (v ? dateLabel(v) : empty) : v || empty}</span><span class="chev"></span></button>`;
};

/** The month grid. Days before today are unavailable; the sale calendar says so underneath (design line 158). */
function calendar(st, k) {
  const t = bkkDay(serverNow());
  const { y, m } = st.view || t;
  const lead = (new Date(Date.UTC(y, m, 1)).getUTCDay() + 6) % 7;
  const days = new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
  const v = st.form[k];
  const atMin = y === t.y && m === t.m;
  const cells = [];
  for (let i = 0; i < lead; i++) cells.push(html`<span></span>`);
  for (let i = 1; i <= days; i++) {
    const past = y < t.y || (y === t.y && (m < t.m || (m === t.m && i < t.d)));
    const sel = v === isoOf(y, m, i);
    const today = !sel && !past && y === t.y && m === t.m && i === t.d;
    cells.push(past
      ? html`<span class="pk-d past">${i}</span>`
      : html`<span class="pk-d${sel ? ' sel' : ''}${today ? ' today' : ''}" data-action="calDay" data-k="${k}" data-iso="${isoOf(y, m, i)}">${i}</span>`);
  }
  return html`<div class="pk-pop pk-date${PICKS[k].up ? ' up' : ''}">
    <div class="pk-head">
      ${atMin ? html`<span class="pk-nav off">‹</span>` : html`<span class="pk-nav" data-action="calPrev">‹</span>`}
      <span class="pk-title">${MONL[m]} ${y}</span>
      <span class="pk-nav" data-action="calNext">›</span>
    </div>
    <div class="pk-wd">${WDF.map((w) => html`<span>${w}</span>`)}</div>
    <div class="pk-days">${cells}</div>
    ${k === 'saleDate' ? html`<span class="pk-note">Days before today are unavailable.</span>` : ''}
  </div>`;
}

/** HH (and MM, and SS for the sale) as scrolling columns. A click sets one part and leaves the popup open. */
function timePopup(st, k) {
  const secs = k === 'saleTime';
  const v = st.form[k];
  const parts = (v || (secs ? '10:00:00' : '19:00')).split(':').map(Number);
  while (parts.length < (secs ? 3 : 2)) parts.push(0);
  const col = (h, max, i) => html`<div class="pk-col"><span class="pk-h">${h}</span><div class="pk-list" data-tcol>
    ${Array.from({ length: max }, (_, n) => html`<span class="pk-it${v && parts[i] === n ? ' sel' : ''}" data-action="tpSet" data-k="${k}" data-i="${i}" data-v="${n}">${pad(n)}</span>`)}
  </div></div>`;
  return html`<div class="pk-pop pk-time${PICKS[k].up ? ' up' : ''}">
    <div class="pk-cols">${[col('HH', 24, 0), col('MM', 60, 1), secs ? col('SS', 60, 2) : ''].filter(Boolean)}</div>
    <button type="button" class="pk-done" data-action="pkClose">Done</button>
  </div>`;
}

const picker = (st, k, empty) => html`<span class="pk-wrap">${trigger(st, k, empty)}
  ${st.pk === k ? (k.endsWith('Date') ? calendar(st, k) : timePopup(st, k)) : ''}</span>`;

function detailsView(st) {
  const f = st.form;
  const err = errors(f);
  const off = Object.keys(err).length > 0;
  const shown = (k) => (st.failed && st.failed.key === k ? st.failed.message : st.touched[k] || st.asked ? err[k] || '' : '');
  const bad = (k) => (shown(k) ? 'bad' : '');
  const note = st.failed && !st.failed.key ? st.failed.message : off ? 'Fill in every field to continue' : 'Saves a draft. Nobody can see it yet.';
  return html`<section class="org-page ev-page">
    ${steps(0)}
    <h1 class="title">${st.edit ? 'Edit event' : 'Create event'}</h1>
    <div class="wiz-grid">
      <div class="wiz-card">
        ${field({ label: 'Event name', name: 'name', value: f.name, error: shown('name'), counter: `${f.name.length}/${MAX.name}`, extra: 'maxlength="40" placeholder="The 1975 Bangkok"' })}
        ${field({ label: 'Venue', name: 'venue', value: f.venue, error: shown('venue'), counter: `${f.venue.length}/${MAX.venue}`, extra: 'maxlength="30" placeholder="Impact Arena"' })}
        <div class="field ${bad('ev')}" data-grp="ev">
          <span class="fl"><b>Event date and time</b></span>
          <span class="wiz-two">${picker(st, 'evDate', 'Pick a date')}${picker(st, 'evTime', '--:--')}</span>
          <span class="err" role="alert">${shown('ev')}</span>
        </div>
        <div class="field ${bad('sale')}" data-grp="sale">
          <span class="fl"><b>Sale opens</b><i>to the second</i></span>
          <span class="wiz-two">${picker(st, 'saleDate', 'Pick a date')}${picker(st, 'saleTime', '--:--:--')}</span>
          <span class="err" role="alert">${shown('sale')}</span>
        </div>
        <div class="field ${bad('rate')}">
          <span class="fl"><b>Admission rate</b></span>
          <span class="wiz-rate"><input type="number" min="1" name="rate" value="${f.rate}"><span>per second</span></span>
          <span class="err" role="alert">${shown('rate')}</span>
        </div>
      </div>
      <aside class="wiz-help">
        <span class="label">ADMISSION RATE</span>
        <p>How many people the queue lets in each second. Work it out from your seats and the 10-minute hold: 7,500 seats ÷ 600 seconds of hold ≈ 12 per second. Faster than this and people get in only to find every seat already held.</p>
        <p>You can change it later, even while the sale is on.</p>
      </aside>
    </div>
    <div class="wiz-foot">
      ${button({ label: 'Cancel', action: 'go', quiet: true, data: { to: '#/events' } })}
      <div class="wiz-save">
        ${button({ label: 'Save and add tiers', action: 'save', disabled: off, working: st.working ? 'Saving' : '' })}
        <span class="wiz-note ${st.failed && !st.failed.key ? 'stop' : ''}" data-hint>${note}</span>
      </div>
    </div>
    ${st.pk ? html`<div class="pk-back" data-action="pkClose"></div>` : ''}
  </section>`;
}

async function save(ctx) {
  const st = ctx.state;
  st.asked = true;
  st.failed = null;
  if (Object.keys(errors(st.form)).length) return ctx.render();
  st.working = true;
  ctx.render();
  try {
    const e = st.edit ? await org.update(st.edit, toDetails(st.form)) : await org.create(toDetails(st.form));
    ctx.go(`#/event/${e.eventId}/tiers`);
  } catch (e) {
    st.working = false;
    if (!(e instanceof ApiError) || (e.status !== 400 && e.status !== 409)) throw e;
    st.failed = { key: FIELD[e.field] || null, message: e.message };
  }
}

// ----- the pickers' buttons: open, close, turn a page of the calendar, choose a day, choose a part -----

/** A pick marks its group seen, and clears a refusal the server sent for that group. */
function touch(st, group) {
  st.touched[group] = 1;
  if (st.failed && st.failed.key === group) st.failed = null;
}

/** Opening one picker closes the others. A date picker starts on the chosen month, else on this month. */
function openPk(ctx, k) {
  const st = ctx.state;
  if (st.pk === k) return void (st.pk = null);
  const v = st.form[k];
  const d = k.endsWith('Date') && v ? fromIso(v) : bkkDay(serverNow());
  st.pk = k;
  st.view = { y: d.y, m: d.m };
  // A time column scrolls to the chosen item after it is drawn (design line 390).
  if (k.endsWith('Time')) requestAnimationFrame(() => document.querySelectorAll('[data-tcol]').forEach((c) => {
    const s = c.querySelector('.pk-it.sel');
    if (s) c.scrollTop = s.offsetTop - c.offsetTop - 92;
  }));
}

const shiftMonth = (ctx, by) => {
  const { y, m } = ctx.state.view || bkkDay(serverNow());
  const d = new Date(Date.UTC(y, m + by, 1));
  ctx.state.view = { y: d.getUTCFullYear(), m: d.getUTCMonth() };
};

const pickDay = (ctx, k, day) => { ctx.state.form[k] = day; touch(ctx.state, PICKS[k].group); ctx.state.pk = null; };

const setPart = (ctx, k, i, n) => {
  const st = ctx.state;
  const secs = k === 'saleTime';
  const parts = (st.form[k] || (secs ? '10:00:00' : '19:00')).split(':').map(Number);
  while (parts.length < (secs ? 3 : 2)) parts.push(0);
  parts[i] = n;
  st.form[k] = parts.map(pad).join(':');
  touch(st, PICKS[k].group);
  // The chosen part stays put: the redraw builds new columns, so put each one back where it was.
  keepScroll();
};

/** Redrawing the columns throws their scroll away; this puts each one back after the draw. */
const keepScroll = () => {
  const was = [...document.querySelectorAll('[data-tcol]')].map((c) => c.scrollTop);
  if (!was.length) return;
  requestAnimationFrame(() => document.querySelectorAll('[data-tcol]').forEach((c, i) => { c.scrollTop = was[i] || 0; }));
};

const actions = {
  save,
  pk: (ctx, d) => openPk(ctx, d.pk),
  pkClose: (ctx) => { ctx.state.pk = null; },
  calPrev: (ctx) => shiftMonth(ctx, -1),
  calNext: (ctx) => shiftMonth(ctx, 1),
  calDay: (ctx, d) => pickDay(ctx, d.k, d.iso),
  tpSet: (ctx, d) => setPart(ctx, d.k, Number(d.i), Number(d.v)),
};

// ---- live checks: the typed value, the counter and the error lines change in place, so the caret stays ----

let live = null;

function begin(ctx, edit, form) {
  ctx.state.edit = edit;
  ctx.state.form = form;
  ctx.state.touched = {};
  ctx.state.asked = false;
  ctx.state.failed = null;
  ctx.state.working = false;
  ctx.state.pk = null;
  ctx.state.view = null;
  live = ctx;
}

/** What the error line of each group says right now. */
const lineOf = (st, err, k) =>
  st.failed && st.failed.key === k ? st.failed.message : st.touched[k] || st.asked ? err[k] || '' : '';

function paint(ctx) {
  const root = document.getElementById('app');
  const st = ctx.state;
  const err = errors(st.form);
  const off = Object.keys(err).length > 0;
  for (const group of ['name', 'venue', 'ev', 'sale', 'rate']) {
    const box = group === 'ev' || group === 'sale'
      ? root.querySelector(`.wiz-card [data-grp="${group}"]`)
      : root.querySelector(`.wiz-card [name="${group}"]`)?.closest('.field');
    if (!box) continue;
    const msg = lineOf(st, err, group);
    let line = box.querySelector('.err');
    if (!line) {
      line = document.createElement('span');
      line.className = 'err';
      line.setAttribute('role', 'alert');
      box.append(line);
    }
    line.textContent = msg;
    box.classList.toggle('bad', !!msg);
    const count = MAX[group] ? box.querySelector('.fl i') : null;
    if (count) count.textContent = `${st.form[group].length}/${MAX[group]}`;
  }
  const save = root.querySelector('.wiz-save .btn');
  if (save) save.disabled = off || st.working;
  const hint = root.querySelector('[data-hint]');
  if (hint && !hint.classList.contains('stop')) {
    hint.textContent = off ? 'Fill in every field to continue' : 'Saves a draft. Nobody can see it yet.';
  }
}

document.addEventListener('input', (e) => {
  const ctx = live && live.alive() ? live : null;
  const el = e.target;
  if (!ctx || !el.closest('#app')) return;
  if (el.dataset.k) return void tierInput(ctx, el); // SCR-32's tier rows
  const group = el.name ? GROUP[el.name] : null;
  if (!group) return;
  ctx.state.form[el.name] = el.value;
  touch(ctx.state, group);
  paint(ctx);
});

// A tier box left empty is only a problem once the organizer has been in it (design line 425).
document.addEventListener('focusout', (e) => {
  const ctx = live && live.alive() ? live : null;
  if (!ctx || !e.target.closest('#app') || !e.target.dataset.k) return;
  seeTier(ctx, e.target);
});

// ----- SCR-32 Tiers: the rows Chai types, checked as he types (design lines 203–233) -----

const MAX_TIERS = 8;
const KEYS = ['name', 'price', 'seats'];
// The server calls the seats box `total`; the same box under its other name (docs/09 §9.4).
const TOT = { name: 'name', price: 'price', total: 'seats' };
const PUB_HINT = 'An event needs at least one tier before it can be published.';

const blankRow = () => ({ name: '', price: '', seats: '', t: {} });

/** The same rules, in the same words, as the server: a row needs a name, a whole price and at least 1 seat. */
function rowErrs(r) {
  const e = {};
  if (!r.name.trim()) e.name = 'Name is required.';
  if (!/^\d+$/.test(r.price.trim())) e.price = 'Price cannot be below ฿0.';
  if (!/^\d+$/.test(r.seats.trim()) || Number(r.seats) < 1) e.seats = 'Seats must be more than 0.';
  return e;
}

/** A tier's own name, or "Tier 2" when it has none — the way the server names a nameless row. */
const tierLabel = (name, i) => name.trim() || `Tier ${i + 1}`;

/** The message for one box: the server's refusal first, else what this row says now. Empty when it is fine. */
function cellMsg(st, i, k) {
  const r = st.rows[i];
  if (!r) return '';
  if (st.failed && st.failed.i === i && st.failed.k === k) return st.failed.message;
  const e = rowErrs(r)[k];
  return e && r.t[k] ? `${tierLabel(r.name, i)} · ${e}` : '';
}

/** The one coral line under a row (design line 217). */
const rowMsg = (st, i) => KEYS.map((k) => cellMsg(st, i, k)).filter(Boolean).join(' ');
const rowsBad = (rows) => rows.some((r) => Object.keys(rowErrs(r)).length > 0);
const seatsOf = (rows) => rows.reduce((a, r) => a + (/^\d+$/.test(r.seats.trim()) ? Number(r.seats) : 0), 0);

/** The line under Review (design line 229). */
function tiersHint(st) {
  if (st.failed && st.failed.i < 0) return st.failed.message;
  if (!st.rows.length) return 'Add at least one tier.';
  if (rowsBad(st.rows)) return 'Fix the tier marked in red';
  return st.rows.length >= MAX_TIERS ? '8 of 8 tiers' : '';
}

/** One tier row: the name, the price, the seats, and Remove (design lines 211-215). */
const cells = (st, r, i) => html`<div class="wiz-tg">
  <input class="wiz-ti${cellMsg(st, i, 'name') ? ' bad' : ''}" data-i="${i}" data-k="name" value="${r.name}" maxlength="20" placeholder="Tier name">
  <input class="wiz-ti mono${cellMsg(st, i, 'price') ? ' bad' : ''}" data-i="${i}" data-k="price" value="${r.price}" inputmode="numeric" placeholder="0">
  <input class="wiz-ti mono${cellMsg(st, i, 'seats') ? ' bad' : ''}" data-i="${i}" data-k="seats" value="${r.seats}" inputmode="numeric" placeholder="0">
  <span class="wiz-rm" data-action="rmRow" data-i="${i}">Remove</span>
</div>`;

function tiersView(st) {
  const rows = st.rows.length ? st.rows : [blankRow()];
  const total = seatsOf(st.rows);
  return html`<section class="org-page ev-page wiz-tiers">
    ${steps(1)}
    <div class="wiz-head"><h1 class="title">Tiers · ${st.e.name}</h1>
      <span class="sub">Each tier is a pool of seats at one price. Up to 8 tiers.</span></div>
    <div class="wiz-tbl">
      <div class="wiz-th"><span>NAME</span><span class="r">PRICE (฿)</span><span class="r">SEATS</span><span></span></div>
      ${rows.map((r, i) => html`<div class="wiz-tr" data-i="${i}">${cells(st, r, i)}
        <span class="wiz-msg" data-msg="${i}">${rowMsg(st, i)}</span></div>`)}
      <div class="wiz-tfoot">
        <button class="wiz-add" data-action="addRow" ${st.rows.length >= MAX_TIERS ? raw('disabled') : ''}>+ Add tier</button>
        <span class="wiz-total${total ? '' : ' zero'}" data-total>${num(total)} seats in all</span>
      </div>
    </div>
    <div class="wiz-foot">
      ${button({ label: 'Back', action: 'go', quiet: true, data: { to: `#/event/${st.e.eventId}/details` } })}
      <div class="wiz-save">
        ${button({ label: 'Review', action: 'review', disabled: !st.rows.length || rowsBad(st.rows), working: st.working ? 'Saving' : '' })}
        <span class="wiz-note${st.failed && st.failed.i < 0 ? ' stop' : ''}" data-hint>${tiersHint(st)}</span>
      </div>
    </div>
  </section>`;
}

/** Opens the tiers screen on the event: its rows as they stand, and the checks quiet until a box is typed in. */
function beginTiers(ctx, e) {
  ctx.state.e = e;
  ctx.state.rows = e.tiers.map((t) => ({ name: t.name, price: String(t.price), seats: String(t.total), t: {} }));
  ctx.state.failed = null;
  ctx.state.working = false;
  live = ctx;
}

/** A box that has been typed in or left behind is seen, so its message may show (design line 425). */
function seeTier(ctx, el) {
  const st = ctx.state;
  const i = Number(el.dataset.i);
  if (!st.rows[i]) return; // the one empty row is only a row once it holds something
  st.rows[i].t[el.dataset.k] = 1;
  if (st.failed && st.failed.i === i) st.failed = null;
  paintTiers(ctx);
}

/** One keystroke in a tier row: whole baht and whole seats, so only digits stay in those two. */
function tierInput(ctx, el) {
  const st = ctx.state;
  const i = Number(el.dataset.i);
  const k = el.dataset.k;
  if (k === 'price' || k === 'seats') el.value = el.value.replace(/[^\d]/g, '');
  if (!st.rows[i]) st.rows[i] = blankRow();
  st.rows[i][k] = el.value;
  seeTier(ctx, el);
}

/** Redraws the borders, the row lines, the total and the buttons in place, so typing keeps the caret. */
function paintTiers(ctx) {
  const root = document.getElementById('app');
  const st = ctx.state;
  const rows = st.rows.length ? st.rows : [null];
  for (let i = 0; i < rows.length; i++) {
    const tr = root.querySelector(`.wiz-tr[data-i="${i}"]`);
    if (!tr) continue;
    for (const k of KEYS) {
      const box = tr.querySelector(`.wiz-ti[data-k="${k}"]`);
      if (box) box.classList.toggle('bad', !!cellMsg(st, i, k));
    }
    tr.querySelector('.wiz-msg').textContent = rowMsg(st, i);
  }
  const total = seatsOf(st.rows);
  const line = root.querySelector('[data-total]');
  if (line) {
    line.textContent = `${num(total)} seats in all`;
    line.classList.toggle('zero', !total);
  }
  const add = root.querySelector('.wiz-add');
  if (add) add.disabled = st.rows.length >= MAX_TIERS;
  const next = root.querySelector('.wiz-save .btn');
  if (next) next.disabled = !st.rows.length || rowsBad(st.rows) || st.working;
  const hint = root.querySelector('[data-hint]');
  if (hint) {
    hint.textContent = tiersHint(st);
    hint.classList.toggle('stop', !!(st.failed && st.failed.i < 0));
  }
}

/** + Add tier (design line 221). */
const addRow = (ctx) => { if (ctx.state.rows.length < MAX_TIERS) ctx.state.rows.push(blankRow()); };

/** Remove takes the row out and the table is redrawn; the rows below shift up (design line 215). */
const rmRow = (ctx, d) => { ctx.state.rows.splice(Number(d.i), 1); ctx.state.failed = null; };

/** Review saves every row at once (O-05), then shows the summary. A refusal names the row it came from. */
async function review(ctx) {
  const st = ctx.state;
  if (!st.rows.length || rowsBad(st.rows)) return;
  st.failed = null;
  st.working = true;
  ctx.render();
  try {
    await org.replaceTiers(st.e.eventId, st.rows.map((r) => ({ name: r.name.trim(), price: Number(r.price), total: Number(r.seats) })));
    ctx.go(`#/event/${st.e.eventId}/review`);
  } catch (e) {
    st.working = false;
    if (!(e instanceof ApiError) || (e.status !== 400 && e.status !== 409)) throw e;
    const m = /^tiers\[(\d+)\]\.(\w+)$/.exec(e.field || '');
    const k = m && TOT[m[2]];
    st.failed = k ? { i: Number(m[1]), k, message: e.message } : { i: -1, k: null, message: e.message };
  }
}

// ----- SCR-33 Review and publish: the whole event once more, then Publish (design lines 234–266) -----

const sumRow = (k, v, cls = '') =>
  html`<div class="row"><span class="k">${k}</span><span class="v ${cls}">${v}</span></div>`;

function reviewView(st) {
  const e = st.e;
  const none = e.tiers.length === 0;
  const total = e.tiers.reduce((a, t) => a + t.total, 0);
  const hint = st.failed || (none ? PUB_HINT : 'Publishing cannot be undone.');
  return html`<section class="org-page ev-page wiz-review">
    ${steps(2)}
    <div class="wiz-head"><h1 class="title">Review and publish</h1>
      <span class="sub">Check everything once. Customers see the event as soon as you publish.</span></div>
    <div class="wiz-rv">
      <div class="wiz-sum">
        <span class="label">EVENT</span>
        <b class="nm">${e.name}</b>
        ${sumRow('Venue', e.venue)}
        ${sumRow('Event date', dateTime(e.eventDateTime), 'mono')}
        ${sumRow('Sale opens', dateTime(e.saleOpensAt), 'mono now')}
        ${sumRow('Admission rate', `${e.admitPerSecond} per second`, 'mono')}
      </div>
      <div class="wiz-tbl">
        <div class="wiz-th rv"><span>TIER</span><span class="r">PRICE</span><span class="r">SEATS</span></div>
        ${e.tiers.map((t) => html`<div class="wiz-vr"><span class="tn">${t.name}</span><span class="mono">${'฿' + num(t.price)}</span><span class="mono">${num(t.total)}</span></div>`)}
        ${none ? html`<div class="wiz-none">No tiers yet.</div>` : ''}
        <div class="wiz-all"><span class="tn">All</span><span></span><span class="mono">${num(total)}</span></div>
      </div>
    </div>
    <span class="wiz-line">After publishing, customers see this event. Prices are frozen once the sale opens.</span>
    <div class="wiz-foot">
      ${button({ label: 'Back', action: 'go', quiet: true, data: { to: `#/event/${e.eventId}/tiers` } })}
      <div class="wiz-save">
        ${button({ label: 'Publish', action: 'publish', disabled: none, working: st.working ? 'Publishing' : '' })}
        <span class="wiz-note${st.failed || none ? ' stop' : ''}" data-hint>${hint}</span>
      </div>
    </div>
  </section>`;
}

/** Publish (O-08) turns the draft into a scheduled event and opens the published screen. */
async function publish(ctx) {
  const st = ctx.state;
  st.failed = null;
  st.working = true;
  ctx.render();
  try {
    await org.publish(st.e.eventId);
    ctx.go(`#/event/${st.e.eventId}/published`);
  } catch (e) {
    st.working = false;
    if (!(e instanceof ApiError) || (e.status !== 400 && e.status !== 409)) throw e;
    st.failed = e.message;
  }
}

const tierActions = { addRow, rmRow, review };
const reviewActions = { publish };

export const screens = [
  {
    id: 'new',
    pattern: /^\/new$/,
    // The sale-time check reads the server's clock, so ask the server for one before the first keystroke (O-01).
    async enter(ctx) {
      await org.events();
      begin(ctx, null, { ...BLANK });
    },
    view: detailsView,
    actions,
  },
  {
    id: 'details',
    pattern: /^\/event\/([^/]+)\/details$/,
    async enter(ctx, eventId) {
      begin(ctx, eventId, fromEvent(await org.event(eventId)));
    },
    view: detailsView,
    actions,
  },
  {
    id: 'tiers',
    pattern: /^\/event\/([^/]+)\/tiers$/,
    // Tiers belong to a draft. An event that has moved on belongs to its own screen (SCR-34).
    async enter(ctx, eventId) {
      const e = await org.event(eventId);
      if (e.state !== 'DRAFT') return void ctx.go(`#/event/${e.eventId}`, { replace: true });
      beginTiers(ctx, e);
    },
    view: tiersView,
    actions: tierActions,
  },
  {
    id: 'review',
    pattern: /^\/event\/([^/]+)\/review$/,
    async enter(ctx, eventId) {
      const e = await org.event(eventId);
      if (e.state !== 'DRAFT') return void ctx.go(`#/event/${e.eventId}`, { replace: true });
      ctx.state.e = e;
      ctx.state.failed = null;
      ctx.state.working = false;
    },
    view: reviewView,
    actions: reviewActions,
  },
];
