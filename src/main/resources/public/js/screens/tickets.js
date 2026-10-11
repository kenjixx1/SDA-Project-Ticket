// SCR-16 Confirmed and SCR-17 Ticket (a valid, b event cancelled, c past event).
import { api } from '../api.js';
import { html, button, baht, time24, dateLong, stub, qr, slimHeader, WORDS } from '../parts.js';

async function loadOrder(ctx, orderId) {
  const order = (await api.orders()).find((o) => o.orderId === orderId);
  if (!order) ctx.state.error = 'missing';
  ctx.state.o = order;
}

export const screens = [
  {
    id: 'confirmed',
    pattern: /^\/confirmed\/([^/]+)$/,
    enter: loadOrder,
    view(st) {
      const o = st.o;
      const q = o.quantity;
      return html`<section class="screen" style="align-items:center;text-align:center">
        <div class="ring go">✓</div>
        <h1 class="status-title">You are going</h1>
        <p class="status-text">Paid ${baht(o.paidAmount)} by ${o.providerLabel}. ${q === 1 ? 'One ticket.' : `${WORDS[q]} tickets, one for each seat.`}</p>
        <div style="display:flex;flex-direction:column;gap:12px;width:100%">${o.tickets.map((t) => stub(o, t))}</div>
      </section>
      <div class="foot">
        ${button({ label: 'Open ticket 1', action: 'go', data: { to: `#/ticket/${o.orderId}/1` } })}
        <button class="textlink" data-action="go" data-to="#/tickets">Go to my tickets</button>
      </div>`;
    },
  },
  {
    id: 'ticket',
    pattern: /^\/ticket\/([^/]+)\/(\d+)$/,
    async enter(ctx, orderId) {
      await loadOrder(ctx, orderId);
      ctx.state.me = await api.me();
    },
    view(st, ctx) {
      const o = st.o;
      const n = Math.min(Math.max(1, Number(ctx.params[1])), o.tickets.length);
      const t = o.tickets[n - 1];
      const cancelled = t.state === 'CANCELLED';
      return html`<section class="screen ${o.past ? 'past' : ''}">
        ${slimHeader(o.event.shortName, `TICKET ${n} OF ${o.tickets.length} · ${o.tierName.toUpperCase()}`, o.event.artwork)}
        ${o.past ? html`<span class="label">Past event</span>` : ''}
        <div class="ticket-hero">
          ${o.event.artwork ? html`<img src="${o.event.artwork}" alt="">` : ''}
          <div class="qrwrap ${cancelled ? 'void' : ''}"><div class="qr">${qr(t.payload, 220)}</div>
            ${cancelled ? html`<span class="stamp">CANCELLED</span>` : ''}</div>
          <p class="mono" style="color:var(--ac);text-align:center;margin:0">${t.ticketId}</p>
        </div>
        <div class="rows">
          <div class="row"><span class="k">Holder</span><span class="v txt">${st.me.name}</span></div>
          <div class="row"><span class="k">Event</span><span class="v txt">${o.event.name}</span></div>
          <div class="row"><span class="k">Venue</span><span class="v txt">${o.event.venue}</span></div>
          <div class="row"><span class="k">Date</span><span class="v txt">${dateLong(o.event.eventDateTime)}, ${time24(o.event.eventDateTime)}</span></div>
          <div class="row"><span class="k">Tier</span><span class="v txt">${o.tierName}</span></div>
        </div>
        <div class="card">${cancelled
          ? html`<b>This event was cancelled.</b><p>This ticket will not get you in.</p>`
          : html`<b>Single use.</b><p>This code works once. Do not share it.</p>`}</div>
        ${o.tickets.length > 1 ? html`<div class="dots">${o.tickets.map((x) => html`<button class="${x.n === n ? 'on' : ''}"
          data-action="go" data-to="#/ticket/${o.orderId}/${x.n}" aria-label="Ticket ${x.n}"></button>`)}</div>` : ''}
      </section>
      <div class="foot">${button({ label: 'Back to my tickets', action: 'go', quiet: true, data: { to: '#/tickets' } })}</div>`;
    },
  },
];
