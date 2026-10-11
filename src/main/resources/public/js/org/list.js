// SCR-30 My events: Chai's events, and the way to a new one. Variant b: no events yet.
import { org } from './api.js';
import { chip, dateTime } from './parts.js';
import { html, num, button } from '../parts.js';

const HEAD = ['EVENT', 'VENUE', 'EVENT DATE', 'SALE OPENS', 'STATE', 'SOLD'];

/** A draft has nothing sold yet, so the figure is a dash. */
const soldText = (r) => (r.state === 'DRAFT' ? '—' : `${num(r.sold)} / ${num(r.total)}`);

const row = (r) => html`<div class="ev-row" data-action="go" data-to="#/event/${r.eventId}">
  <span class="ev-name">${r.name}</span>
  <span class="ev-venue">${r.venue}</span>
  <span class="ev-when">${dateTime(r.eventDateTime)}</span>
  <span class="ev-sale">${dateTime(r.saleOpensAt)}</span>
  <span class="ev-state">${chip(r.state)}</span>
  <span class="ev-sold">${soldText(r)}</span>
</div>`;

const table = (rows) => html`<div class="ev-tbl">
  <div class="ev-head">${HEAD.map((h) => html`<span class="${h === 'SOLD' ? 'r' : ''}">${h}</span>`)}</div>
  ${rows.map(row)}
</div>`;

const none = () => html`<div class="ev-empty">
  <span class="ring"></span>
  <b>No events yet</b>
  <p>Create your first event to start selling.</p>
  ${button({ label: 'Create event', action: 'go', data: { to: '#/new' } })}
</div>`;

const load = async (ctx) => { ctx.state.rows = await org.events(); };

export const screens = [
  {
    id: 'events',
    pattern: /^\/events$/,
    enter: load,
    poll: { every: 5000, run: load },
    view(st) {
      const rows = st.rows;
      return html`<section class="org-page ev-page">
        <div class="ev-top">
          <div class="ev-titles"><h1 class="title">My events</h1><span class="sub">${rows.length} ${rows.length === 1 ? 'event' : 'events'}</span></div>
          ${rows.length ? button({ label: 'Create event', action: 'go', data: { to: '#/new' } }) : ''}
        </div>
        ${rows.length ? table(rows) : none()}
      </section>`;
    },
  },
];
