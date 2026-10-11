// The design's date and time pickers (Organizer Console design lines 105–190), shared by SCR-31 Details and the SCR-35 dialog.
// Values are Bangkok strings: dates "2026-11-14", times "19:00" or "10:00:00". The clock is the server's, never the device's.
import { html } from '../parts.js';
import { serverNow } from '../api.js';

export const BKK = 7 * 3600_000;
export const pad = (n) => String(n).padStart(2, '0');
const MON = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MONL = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const DAYNAME = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const WDF = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

/** The Bangkok calendar day of a moment. */
const bkkDay = (ms) => { const x = new Date(ms + BKK); return { y: x.getUTCFullYear(), m: x.getUTCMonth(), d: x.getUTCDate() }; };
/** "2026-11-14" as three numbers. The string is already a Bangkok date, so no zone is added. */
const fromIso = (v) => { const [y, m, d] = v.split('-').map(Number); return { y, m: m - 1, d }; };
const isoOf = (y, m, d) => `${y}-${pad(m + 1)}-${pad(d)}`;
/** "Sat 14 Nov 2026", the way the calendar's trigger shows a chosen day. */
const dateLabel = (v) => { const { y, m, d } = fromIso(v); return `${DAYNAME[new Date(Date.UTC(y, m, d)).getUTCDay()]} ${d} ${MON[m]} ${y}`; };
/** The time parts of a value, or the default the first click starts from. */
const partsOf = (v, secs) => {
  const parts = (v || (secs ? '10:00:00' : '19:00')).split(':').map(Number);
  while (parts.length < (secs ? 3 : 2)) parts.push(0);
  return parts;
};

/** Redrawing the columns throws their scroll away; this puts each one back after the draw. */
const keepScroll = () => {
  const was = [...document.querySelectorAll('[data-tcol]')].map((c) => c.scrollTop);
  if (!was.length) return;
  requestAnimationFrame(() => document.querySelectorAll('[data-tcol]').forEach((c, i) => { c.scrollTop = was[i] || 0; }));
};

/**
 * picks: { evDate: { group, up }, ... } — which form keys are pickers, their error group, and whether the popup opens upward.
 * box(ctx): the object that holds `form` (the values), `pk` (the open picker) and `view` (the calendar month).
 * touch(ctx, group): marks a group seen after a pick.
 * Keys ending in Date are calendars; keys ending in Time are time lists; 'saleTime' has seconds and 'saleDate' a note.
 */
export function pickerKit({ picks, box, touch }) {
  /** The trigger: the value, or the empty text, and a chevron. Full width, 50 tall (design line 111). */
  const trigger = (b, k, empty) => {
    const v = b.form[k];
    const open = b.pk === k;
    return html`<button type="button" class="pk${v ? ' set' : ''}${open ? ' on' : ''}" data-action="pk" data-pk="${k}" aria-expanded="${open ? 'true' : 'false'}">
      <span>${k.endsWith('Date') ? (v ? dateLabel(v) : empty) : v || empty}</span><span class="chev"></span></button>`;
  };

  /** The month grid. Days before today are unavailable; the sale calendar says so underneath (design line 158). */
  function calendar(b, k) {
    const t = bkkDay(serverNow());
    const { y, m } = b.view || t;
    const lead = (new Date(Date.UTC(y, m, 1)).getUTCDay() + 6) % 7;
    const days = new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
    const v = b.form[k];
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
    return html`<div class="pk-pop pk-date${picks[k].up ? ' up' : ''}">
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
  function timePopup(b, k) {
    const secs = k === 'saleTime';
    const v = b.form[k];
    const parts = partsOf(v, secs);
    const col = (h, max, i) => html`<div class="pk-col"><span class="pk-h">${h}</span><div class="pk-list" data-tcol>
      ${Array.from({ length: max }, (_, n) => html`<span class="pk-it${v && parts[i] === n ? ' sel' : ''}" data-action="tpSet" data-k="${k}" data-i="${i}" data-v="${n}">${pad(n)}</span>`)}
    </div></div>`;
    return html`<div class="pk-pop pk-time${picks[k].up ? ' up' : ''}">
      <div class="pk-cols">${[col('HH', 24, 0), col('MM', 60, 1), secs ? col('SS', 60, 2) : ''].filter(Boolean)}</div>
      <button type="button" class="pk-done" data-action="pkClose">Done</button>
    </div>`;
  }

  const picker = (b, k, empty) => html`<span class="pk-wrap">${trigger(b, k, empty)}
    ${b.pk === k ? (k.endsWith('Date') ? calendar(b, k) : timePopup(b, k)) : ''}</span>`;

  /** The same trigger, locked: it shows the value and cannot open (SCR-35 sale time once the sale is on). */
  const locked = (b, k, empty) => {
    const v = b.form[k];
    return html`<span class="pk-wrap"><button type="button" class="pk set" disabled>
      <span>${k.endsWith('Date') ? (v ? dateLabel(v) : empty) : v || empty}</span><span class="chev"></span></button></span>`;
  };

  /** Opening one picker closes the others. A date picker starts on the chosen month, else on this month. */
  function openPk(ctx, k) {
    const b = box(ctx);
    if (b.pk === k) return void (b.pk = null);
    const v = b.form[k];
    const d = k.endsWith('Date') && v ? fromIso(v) : bkkDay(serverNow());
    b.pk = k;
    b.view = { y: d.y, m: d.m };
    // A time column scrolls to the chosen item after it is drawn (design line 390).
    if (k.endsWith('Time')) requestAnimationFrame(() => document.querySelectorAll('[data-tcol]').forEach((c) => {
      const s = c.querySelector('.pk-it.sel');
      if (s) c.scrollTop = s.offsetTop - c.offsetTop - 92;
    }));
  }

  const shiftMonth = (ctx, by) => {
    const b = box(ctx);
    const { y, m } = b.view || bkkDay(serverNow());
    const d = new Date(Date.UTC(y, m + by, 1));
    b.view = { y: d.getUTCFullYear(), m: d.getUTCMonth() };
  };

  const pickDay = (ctx, k, day) => { const b = box(ctx); b.form[k] = day; touch(ctx, picks[k].group); b.pk = null; };

  const setPart = (ctx, k, i, n) => {
    const b = box(ctx);
    const parts = partsOf(b.form[k], k === 'saleTime');
    parts[i] = n;
    b.form[k] = parts.map(pad).join(':');
    touch(ctx, picks[k].group);
    // The chosen part stays put: the redraw builds new columns, so put each one back where it was.
    keepScroll();
  };

  return {
    picker,
    locked,
    actions: {
      pk: (ctx, d) => openPk(ctx, d.pk),
      pkClose: (ctx) => { box(ctx).pk = null; },
      calPrev: (ctx) => shiftMonth(ctx, -1),
      calNext: (ctx) => shiftMonth(ctx, 1),
      calDay: (ctx, d) => pickDay(ctx, d.k, d.iso),
      tpSet: (ctx, d) => setPart(ctx, d.k, Number(d.i), Number(d.v)),
    },
  };
}
