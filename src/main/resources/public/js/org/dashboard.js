// SCR-40 Live dashboard: is the sale working, right now? Queue, tiers, revenue (UC-07, FR-31 – FR-34).
import { org } from './api.js';
import { contactAge } from '../api.js';
import { html, raw, num, baht, liveIndicator } from '../parts.js';

const HEAD = ['TIER', 'TOTAL', 'HELD', 'SOLD', 'REMAINING', ''];
/** No answer for this long reads as stale (SCR-40b), the same rule as the C-01 live dot. */
const STALE_MS = 15000;

/** Bangkok clock time with seconds: "10:31:05". */
const clockSeconds = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Bangkok', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' });
const closedLine = (ms) => `Sale closed at ${clockSeconds.format(ms)}.`;

/** The tier rows, then the All sum row the server sends with them. */
const tableRows = (d) => [...d.tiers, d.all];

/** Every live figure by key, so two loads can be compared and only what changed can ease in. */
function figures(d) {
  const m = { waiting: d.waiting, rate: d.admitPerSecond, revenue: d.revenue };
  for (const t of tableRows(d)) {
    m[`${t.tierId}:total`] = t.total;
    m[`${t.tierId}:held`] = t.held;
    m[`${t.tierId}:sold`] = t.sold;
    m[`${t.tierId}:remaining`] = t.remaining;
  }
  return m;
}

/** A live figure. `bump` eases a changed number from lime to its own colour; `dim` is the stale look. */
const fig = (st, key, cls, inner) =>
  html`<span class="${cls}${st.stale ? ' dim' : st.changed.has(key) ? ' bump' : ''}" data-fig>${inner}</span>`;

const share = (n, total) => (total ? (n / total) * 100 : 0);
/** Sold, held, remaining — the last part is the bar's own dark background. */
const bar = (t) => html`<span class="ov-bar" aria-hidden="true"><i style="width:${share(t.sold, t.total)}%"></i><i style="width:${share(t.held, t.total)}%"></i></span>`;

const row = (st, t, all) => html`<div class="ov-row${all ? ' all' : ''}">
  <span class="ov-tname">${t.name}</span>
  ${fig(st, `${t.tierId}:total`, 'n', num(t.total))}
  ${fig(st, `${t.tierId}:held`, 'n held', num(t.held))}
  ${fig(st, `${t.tierId}:sold`, 'n', num(t.sold))}
  ${fig(st, `${t.tierId}:remaining`, `n${t.remaining === 0 ? ' ov-sold' : ''}`, t.remaining === 0 ? 'Sold out' : num(t.remaining))}
  ${bar(t)}
</div>`;

function view(st) {
  const d = st.d;
  const final = d.state === 'CLOSED';
  return html`<section class="org-page ov-page">
    <div class="ov-note stop" role="status" data-stale ${st.stale ? '' : raw('hidden')}>
      <b data-age>These figures are ${st.age} seconds old.</b><span>We are trying to reconnect.</span>
    </div>
    <div class="ov-head">
      <div class="ov-name live-head"><h1 class="title">${d.name} · ${final ? 'Final' : 'Live'}</h1>${final ? '' : liveIndicator()}</div>
      ${final && d.closedAt ? html`<div class="ov-meta"><span class="ov-sub">${closedLine(d.closedAt)}</span></div>` : ''}
    </div>
    <div class="ov-queue">
      <div class="ov-card"><span class="ov-rate-k">Waiting</span>${fig(st, 'waiting', 'ov-fig-n', num(d.waiting))}</div>
      <div class="ov-card"><span class="ov-rate-k">Admitting</span>${fig(st, 'rate', 'ov-fig-n', html`${num(d.admitPerSecond)}<span class="ov-fig-u"> / second</span>`)}</div>
    </div>
    <div class="ov-live">
      <div class="ov-tbl ov-live-tbl">
        <div class="ov-th">${HEAD.map((h, i) => html`<span class="${i > 0 && i < HEAD.length - 1 ? 'n' : ''}">${h}</span>`)}</div>
        ${tableRows(d).map((t) => row(st, t, t.tierId === 'all'))}
      </div>
      <p class="ov-hint">Held = inside a customer's 10 minutes. Not sold, not available.</p>
    </div>
    <div class="ov-card ov-rev"><span class="ov-rate-k">Revenue, confirmed orders only</span>${fig(st, 'revenue', 'ov-rate-n', baht(d.revenue))}</div>
    <div class="ov-foot"><button class="ov-link" data-action="go" data-to="#/event/${d.eventId}">Back to event</button></div>
  </section>`;
}

async function load(ctx, id) {
  const d = await org.dashboard(id);
  const st = ctx.state;
  const now = figures(d);
  st.changed = st.figs ? new Set(Object.keys(now).filter((k) => now[k] !== st.figs[k])) : new Set();
  st.figs = now;
  st.d = d;
}

/** SCR-40b: the age of the last answer, counted up each second, and the dimming it brings. */
function watch(ctx) {
  const st = ctx.state;
  clearInterval(st.timer);
  st.timer = setInterval(() => {
    if (!ctx.alive()) return clearInterval(st.timer);
    const age = contactAge();
    st.age = Math.floor(age / 1000);
    st.stale = age > STALE_MS;
    paint(st);
  }, 1000);
}

/** Dims every figure and moves the banner, without redrawing the page the poll has just drawn. */
function paint(st) {
  const root = document.getElementById('app');
  root.querySelectorAll('[data-fig]').forEach((el) => el.classList.toggle('dim', st.stale));
  const note = root.querySelector('[data-stale]');
  if (!note) return;
  note.hidden = !st.stale;
  note.querySelector('[data-age]').textContent = `These figures are ${st.age} seconds old.`;
}

export const screens = [
  {
    id: 'live',
    pattern: /^\/event\/([^/]+)\/live$/,
    // The stale banner below is this screen's own, so the router's "No connection" is not drawn over it.
    ownOffline: true,
    enter(ctx, id) {
      ctx.state.age = 0;
      ctx.state.stale = false;
      watch(ctx);
      return load(ctx, id);
    },
    poll: { every: 2000, run: load },
    view,
  },
];
