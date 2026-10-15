// SCR-16 Confirmed and SCR-17 Ticket (a valid, b event cancelled, c past event).
import { api } from '../api.js';
import { html, raw, button, baht, time24, dateLong, stub, qr, WORDS } from '../parts.js';

async function loadOrder(ctx, orderId) {
  const order = (await api.orders()).find((o) => o.orderId === orderId);
  if (!order) ctx.state.error = 'missing';
  ctx.state.o = order;
}

// SCR-17: ← and → move between the tickets of one order, the same as the round buttons.
document.addEventListener('keydown', (e) => {
  if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
  if (e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return; // Alt+← is the browser's Back
  if (e.target instanceof HTMLElement && e.target.closest('input,textarea,select,[contenteditable]')) return;
  const b = document.querySelector(`.tknav .${e.key === 'ArrowLeft' ? 'prev' : 'next'}`);
  if (b) b.click();
});

export const screens = [
  {
    id: 'confirmed',
    pattern: /^\/confirmed\/([^/]+)$/,
    enter: loadOrder,
    view(st) {
      const o = st.o;
      const q = o.quantity;
      return html`<section class="screen confirmed">
        <div class="gohead">
          <span class="checkring" role="img" aria-label="Paid">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="5 12.5 10 17 19 7"></polyline></svg>
          </span>
          <h1 class="status-title">You are going</h1>
          <p class="status-text">Paid ${baht(o.paidAmount)} by ${o.providerLabel}. ${q === 1 ? 'One ticket.' : `${WORDS[q]} tickets, one for each seat.`}</p>
        </div>
        <div class="stubs">
          ${o.tickets.map((t) => html`<div class="stubwrap">${o.event.artwork ? html`<img src="${o.event.artwork}" alt="">` : ''}${stub(o, t)}</div>`)}
        </div>
        <div class="acts">
          ${button({ label: 'Go to my tickets', action: 'go', quiet: true, data: { to: '#/tickets' } })}
          ${button({ label: 'Open ticket 1', action: 'go', data: { to: `#/ticket/${o.orderId}/1` } })}
        </div>
      </section>`;
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
      const past = o.past && !cancelled;
      const multi = o.tickets.length > 1;
      return html`<section class="screen ticket ${past ? 'past' : ''}">
        <button class="backlink" data-action="go" data-to="#/tickets">‹ My tickets</button>
        <div class="tkhead">
          <div><span class="tkname">${o.event.shortName}</span>
            <span class="tksub">TICKET ${n} OF ${o.tickets.length} · ${o.tierName.toUpperCase()}</span></div>
          ${past ? html`<span class="pastchip">PAST EVENT</span>` : ''}
        </div>
        <div class="tkbody">
          <div class="tkcard">
            ${o.event.artwork ? html`<img src="${o.event.artwork}" alt="">` : ''}
            <span class="sheen"></span>
            <div class="qrwrap ${cancelled ? 'void' : past ? 'used' : ''}"><div class="qr">${qr(t.payload, 264)}</div>
              ${cancelled ? html`<span class="cut a"></span><span class="cut b"></span>
                <span class="stamp">CANCELLED</span>` : ''}
              ${past ? html`<span class="stamp used">USED</span>` : ''}</div>
            <span class="tkid">${t.ticketId}</span>
            ${multi ? html`<div class="tknav">
              <button class="prev" data-action="go" data-to="#/ticket/${o.orderId}/${n - 1}" title="Previous ticket (←)" aria-label="Previous ticket" ${n <= 1 ? raw('disabled') : ''}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg></button>
              <div class="tkwhere">
                <div class="dots">${o.tickets.map((x) => html`<button class="${x.n === n ? 'on' : ''}"
                  data-action="go" data-to="#/ticket/${o.orderId}/${x.n}" aria-label="Ticket ${x.n}"></button>`)}</div>
                <span class="tklabel">Ticket ${n} of ${o.tickets.length} · use ← →</span>
              </div>
              <button class="next" data-action="go" data-to="#/ticket/${o.orderId}/${n + 1}" title="Next ticket (→)" aria-label="Next ticket" ${n >= o.tickets.length ? raw('disabled') : ''}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg></button>
            </div>` : ''}
          </div>
          <div class="detail">
            <div class="rows">
              <div class="row"><span class="k">Holder</span><span class="v txt">${st.me.name}</span></div>
              <div class="row"><span class="k">Event</span><span class="v txt">${o.event.name}</span></div>
              <div class="row"><span class="k">Venue</span><span class="v txt">${o.event.venue}</span></div>
              <div class="row"><span class="k">Date</span><span class="v txt">${dateLong(o.event.eventDateTime)}, ${time24(o.event.eventDateTime)}</span></div>
              <div class="row"><span class="k">Tier</span><span class="v txt">${o.tierName}</span></div>
            </div>
            <div class="card ${cancelled ? 'stop' : ''}">${cancelled
              ? html`<b>This event was cancelled.</b><p>This ticket will not get you in.</p>`
              : past
                ? html`<b>Used.</b><p>This event has happened. The code no longer gets anyone in.</p>`
                : html`<b>Single use.</b><p>This code works once. Do not share it.</p>`}</div>
          </div>
        </div>
      </section>`;
    },
  },
];
