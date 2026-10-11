// SCR-05 Event. Variants: a before sale, b on sale, c already in line, d sold out, e sale closed, f cancelled.
import { api, serverNow } from '../api.js';
import { html, button, baht, num, time24, dateShort, dateLong, hms } from '../parts.js';

function variant(ev) {
  if (ev.state === 'CANCELLED') return 'f';
  if (ev.state === 'CLOSED') return 'e';
  if (ev.soldOut) return 'd';
  if (ev.myToken) return 'c';
  return ev.state === 'SCHEDULED' ? 'a' : 'b';
}

function saleBlock(ev, v) {
  if (v === 'a') {
    return html`<span class="label">Sale opens</span>
      <span class="big">${time24(ev.saleOpensAt)}</span>
      <p class="sub">${dateLong(ev.saleOpensAt)}</p>
      <span class="opens" data-countdown="${ev.saleOpensAt}" data-fmt="hms">Opens in <span data-cd-text>${hms(ev.saleOpensAt - serverNow())}</span></span>`;
  }
  if (v === 'b') return html`<span class="on"><span class="pulse"></span>ON SALE NOW</span>`;
  if (v === 'c') {
    return html`<span class="label go">You are in the queue</span>
      <span class="pos">${ev.myPosition ? `Position ${num(ev.myPosition)}` : 'It is your turn'}</span>`;
  }
  if (v === 'd') return html`<span class="stop">SOLD OUT</span><p class="sub">Every ticket has been sold.</p>`;
  if (v === 'e') return html`<span class="stop">SALE CLOSED</span><p class="sub">The organizer has closed this sale.</p>`;
  return html`<span class="stop">EVENT CANCELLED</span>`;
}

function footer(ev, v, st) {
  if (v === 'a') {
    return html`${button({ label: 'Join the queue', action: 'join', disabled: true })}
      <p class="hint">The button turns on at ${time24(ev.saleOpensAt)}</p>`;
  }
  if (v === 'b') {
    return html`${button({ label: 'Join the queue', action: 'join', working: st.joining ? 'Joining…' : '' })}
      <p class="hint">Maximum 4 tickets per person</p>`;
  }
  if (v === 'c') {
    return button({ label: 'Back to my place', action: 'go', data: { to: `#/queue/${ev.eventId}/${ev.myToken}/back` } });
  }
  return button({ label: 'Back to events', action: 'go', quiet: true, data: { to: '#/events' } });
}

const load = async (ctx, eventId) => { ctx.state.ev = await api.event(eventId); };

export const screens = [
  {
    id: 'event',
    pattern: /^\/event\/([^/]+)$/,
    enter: load,
    poll: { every: 5000, run: load },
    async onZero(ctx, eventId) {
      await load(ctx, eventId);
      if (ctx.state.ev.state === 'SCHEDULED') setTimeout(() => { ctx.run.zeroFired = false; }, 2000);
    },
    view(st) {
      const ev = st.ev;
      const v = variant(ev);
      return html`<section class="screen ev">
        <button class="allink" data-action="go" data-to="#/events">‹ All events</button>
        <div class="split">
          <div class="evleft">
            <div class="hero">
              <div class="hero-bg">${ev.artwork ? html`<img src="${ev.artwork}" alt="">` : html`<span class="noimg">${ev.name}</span>`}</div>
              <div class="hero-shade"></div>
              <div class="hero-txt"><h1>${ev.name}</h1><span class="where">${ev.venue} · ${dateShort(ev.eventDateTime)}</span></div>
            </div>
            <div class="tiers">
              <span class="sect">Tiers</span>
              <div class="tierlist">${ev.tiers.map((t) => html`<div><span>${t.name}</span><span>${baht(t.price)}</span></div>`)}</div>
            </div>
          </div>
          <div class="evright">
            <div class="sale">${saleBlock(ev, v)}</div>
            ${footer(ev, v, st)}
            <div class="note"><b>Everyone waits in the same line.</b><span>Joining early does not help.</span></div>
          </div>
        </div>
      </section>`;
    },
    actions: {
      async join(ctx, data, eventId) {
        ctx.state.joining = true;
        ctx.render();
        try {
          const r = await api.join(eventId);
          ctx.go(`#/queue/${eventId}/${r.token}${r.isExisting ? '/back' : ''}`);
        } catch (e) {
          ctx.state.joining = false;
          if (e.code !== 'NOT_ON_SALE') throw e;
          await load(ctx, eventId);
        }
      },
    },
  },
];
