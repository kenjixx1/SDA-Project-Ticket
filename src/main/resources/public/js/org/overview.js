// SCR-34 Event overview: what the event is, and the few things its state still allows.
import { org } from './api.js';
import { html, num, baht, button } from '../parts.js';
import { chip, dateTime } from './parts.js';
import { bindDialogs, dialogSheet, dialogActions } from './dialogs.js';

const HEAD = ['TIER', 'PRICE', 'TOTAL', 'HELD', 'SOLD', 'REMAINING', ''];

/** What each state allows (docs/08 §8.8, the SCR-34 table). CLOSING, CLOSED and CANCELLED allow nothing. */
const ALLOWED = {
  DRAFT: { details: 1, tier: 1, add: 1, rate: 1 },
  SCHEDULED: { details: 1, tier: 1, add: 1, rate: 1 },
  ON_SALE: { details: 1, tier: 1, add: 1, rate: 1 },
};
const can = (state, what) => !!((ALLOWED[state] || {})[what]);

/** "148 seats are inside someone's 10 minutes." */
const inside = (n) => `${num(n)} ${n === 1 ? 'seat is' : 'seats are'} inside someone's 10 minutes.`;

const note = (lead, text = '', stop = false) =>
  html`<div class="ov-note ${stop ? 'stop' : ''}" role="status"><b>${lead}</b>${text ? html`<span>${text}</span>` : ''}</div>`;

const subtitle = (e) => `${e.venue} · ${dateTime(e.eventDateTime)} · Sale opens ${dateTime(e.saleOpensAt)}`;

function actionBar(e) {
  const id = e.eventId;
  const live = { label: 'Live dashboard', action: 'go', data: { to: `#/event/${id}/live` } };
  const bar = (inner) => html`<div class="ov-acts">${inner}</div>`;
  if (e.state === 'DRAFT') return bar(button({ label: 'Continue setup', action: 'go', data: { to: `#/event/${id}/tiers` } }));
  if (e.state === 'SCHEDULED') return bar(html`<div class="ov-hold">${button({ ...live, disabled: true })}<span>Opens when the sale starts</span></div>`);
  if (e.state === 'ON_SALE') return bar(html`${button(live)}${button({ label: 'Close the sale', action: 'closeSale', danger: true })}`);
  if (e.state === 'CLOSING') return bar(button(live));
  if (e.state === 'CLOSED') return bar(button({ label: 'Final figures', action: 'go', data: { to: `#/event/${id}/live` } }));
  return '';
}

const tierRow = (e, t) => html`<div class="ov-row">
  <span class="ov-tname">${t.name}</span>
  <span class="n">${baht(t.price)}</span>
  <span class="n">${num(t.total)}</span>
  <span class="n held">${num(t.held)}</span>
  <span class="n">${num(t.sold)}</span>
  <span class="n">${num(t.remaining)}</span>
  <span class="ov-act">${can(e.state, 'tier') ? html`<button class="ov-link" data-action="editTier" data-tier="${t.tierId}">Edit</button>` : ''}</span>
</div>`;

const tierTable = (e) => html`<div class="ov-tbl">
  <div class="ov-th">${HEAD.map((h, i) => html`<span class="${i > 0 && i < HEAD.length - 1 ? 'n' : ''}">${h}</span>`)}</div>
  ${e.tiers.map((t) => tierRow(e, t))}
  ${e.tiers.length === 0 ? html`<div class="ov-none">No tiers yet. Add one to publish.</div>` : ''}
  ${can(e.state, 'add') ? html`<div class="ov-add">${button({ label: '+ Add tier', action: 'addTier', quiet: true })}</div>` : ''}
</div>`;

const queueCard = (e) => html`<div class="ov-card">
  <span class="label">QUEUE</span>
  <span class="ov-rate-k">Admission rate</span>
  <div class="ov-rate"><span class="ov-rate-n">${num(e.admitPerSecond)}<span> per second</span></span>
    ${can(e.state, 'rate') ? html`<button class="ov-link" data-action="changeRate">Change</button>` : ''}</div>
</div>`;

function view(st) {
  const e = st.e;
  return html`<section class="org-page ov-page">
    ${st.banner ? note('Published.', `The sale opens ${dateTime(e.saleOpensAt)}. Customers can see the event now.`) : ''}
    ${e.state === 'CLOSING' ? note('Closing.', `${inside(e.heldNow)} The sale closes when they finish.`) : ''}
    ${e.state === 'CANCELLED' ? note('This event was cancelled.', '', true) : ''}
    <div class="ov-head">
      <div class="ov-name"><h1 class="title">${e.name}</h1>${chip(e.state)}</div>
      <div class="ov-meta"><span class="ov-sub">${subtitle(e)}</span>
        ${can(e.state, 'details') ? html`<button class="ov-link" data-action="editDetails">Edit details</button>` : ''}</div>
    </div>
    ${actionBar(e)}
    <div class="ov-grid">${tierTable(e)}${queueCard(e)}</div>
  </section>${dialogSheet(st)}`;
}

async function load(ctx, id) {
  ctx.state.e = await org.event(id);
}

export const screens = [
  {
    id: 'event',
    pattern: /^\/event\/([^/]+)$/,
    enter(ctx, id) {
      bindDialogs(ctx);
      ctx.state.banner = false;
      return load(ctx, id);
    },
    poll: { every: 5000, run: load },
    view,
    actions: { ...dialogActions },
  },
  {
    id: 'published',
    pattern: /^\/event\/([^/]+)\/published$/,
    enter(ctx, id) {
      bindDialogs(ctx);
      ctx.state.banner = true;
      return load(ctx, id);
    },
    poll: { every: 5000, run: load },
    view,
    actions: { ...dialogActions },
  },
];
