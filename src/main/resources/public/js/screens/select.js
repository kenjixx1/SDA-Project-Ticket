// SCR-08 Select tickets. Variants: a default, b holding, c at the limit of 4, d tier sold out,
// e more than remain, f seats just went, g everything sold out.
import { api } from '../api.js';
import { html, raw, baht, num, button, banner } from '../parts.js';

const LIMIT = 4;
const low = (t) => t.remaining > 0 && t.remaining <= t.total * 0.05; // OQ-04: 5% or less
const maxFor = (t) => Math.min(LIMIT, t.remaining);

/** Keeps the selection valid after the counts change. sel becomes -1 when nothing is left. */
function fit(st) {
  if (!st.tiers[st.sel] || st.tiers[st.sel].remaining === 0) st.sel = st.tiers.findIndex((t) => t.remaining > 0);
  if (st.sel >= 0) st.qty = Math.max(1, Math.min(st.qty, maxFor(st.tiers[st.sel])));
}

async function refresh(ctx, eventId) {
  ctx.state.tiers = await api.availability(eventId);
  fit(ctx.state);
}

function tierRow(t, i, st) {
  const out = t.remaining === 0;
  const sel = i === st.sel;
  const left = out ? 'Sold out' : low(t) ? `only ${num(t.remaining)} left` : `${num(t.remaining)} left`;
  return html`<button class="pick ${sel ? 'sel' : ''}" data-action="pick" data-i="${i}" ${out || st.working ? raw('disabled') : ''}>
    <span class="name">${t.name}</span><span class="price">${baht(t.price)}</span>
    <span class="left ${out ? 'out' : low(t) ? 'low' : ''}">${left}</span>
    ${sel ? html`<span class="check" aria-label="Selected">✓</span>` : html`<span></span>`}
  </button>`;
}

function soldOutView(st) {
  return html`<section class="screen">
    <div><h1 class="title sm">Sold out</h1><p class="sub">Every ticket was sold while you were in line. Nothing was charged.</p></div>
    <hr class="hair">
    ${st.tiers.map((t, i) => tierRow(t, i, st))}
  </section>
  <div class="foot">${button({ label: 'Back to events', action: 'go', quiet: true, data: { to: '#/events' } })}</div>`;
}

export const screens = [
  {
    id: 'select',
    pattern: /^\/select\/([^/]+)\/([^/]+)$/,
    async enter(ctx, eventId) {
      ctx.state.sel = 0;
      ctx.state.qty = 1;
      await refresh(ctx, eventId);
    },
    poll: {
      every: 3000,
      async run(ctx, eventId, token) {
        if (ctx.state.working) return;
        const q = await api.queue(token);
        if (q.state === 'EXPIRED') ctx.go(`#/passed/${eventId}`, { replace: true }); // Q-3: the turn ends after 5 minutes
      },
    },
    view(st) {
      if (st.sel < 0) return soldOutView(st);
      const tier = st.tiers[st.sel];
      const atLimit = st.qty >= LIMIT;
      const busy = st.working ? raw('disabled') : '';
      return html`<section class="screen">
        <div><h1 class="title sm">You are in</h1><p class="sub">Choose your tickets. Nothing is reserved yet.</p></div>
        <hr class="hair">
        ${st.banner ? banner(st.banner[0], st.banner[1]) : ''}
        ${st.tiers.map((t, i) => tierRow(t, i, st))}
        <div class="card">
          <div class="qty"><b>How many</b>
            <div class="stepper">
              <button data-action="minus" aria-label="One fewer" ${st.qty <= 1 ? raw('disabled') : busy}>−</button>
              <span class="n">${st.qty}</span>
              <button data-action="plus" aria-label="One more" ${st.qty >= maxFor(tier) ? raw('disabled') : busy}>+</button>
            </div>
          </div>
          <p class="hint ${atLimit ? 'stop' : ''}" style="text-align:left">${atLimit ? '4 is the most one person can buy.' : 'Maximum 4 per person'}</p>
        </div>
      </section>
      <div class="foot">
        <div class="total"><span class="what">${st.qty} × ${tier.name}</span><span class="sum">${baht(tier.price * st.qty)}</span></div>
        ${button({ label: 'Hold these seats', action: 'hold', working: st.working ? 'Holding…' : '' })}
        <p class="hint">Holding takes them out of the sale for 10 minutes</p>
      </div>`;
    },
    actions: {
      pick(ctx, data) {
        ctx.state.sel = Number(data.i);
        ctx.state.banner = null;
        fit(ctx.state);
      },
      minus(ctx) { ctx.state.qty = Math.max(1, ctx.state.qty - 1); },
      plus(ctx) {
        const st = ctx.state;
        st.qty = Math.min(maxFor(st.tiers[st.sel]), st.qty + 1);
      },
      async hold(ctx, data, eventId, token) {
        const st = ctx.state;
        const tier = st.tiers[st.sel];
        st.working = true;
        st.banner = null;
        ctx.render();
        try {
          const h = await api.hold(token, tier.tierId, st.qty);
          ctx.go(`#/hold/${h.reservationId}`);
        } catch (e) {
          st.working = false;
          if (e.code === 'NOT_ADMITTED') return ctx.go(`#/passed/${eventId}`);
          if (e.code === 'NOT_ON_SALE') return ctx.go(`#/event/${eventId}`);
          if (e.status !== 409) throw e;
          await refresh(ctx, eventId);
          if (e.code === 'NOT_ENOUGH' && e.remaining > 0) {
            st.banner = [`Only ${num(e.remaining)} ${tier.name} ticket${e.remaining === 1 ? ' is' : 's are'} left.`, 'Choose fewer tickets or another tier.'];
          } else if (e.code === 'OVER_LIMIT') {
            st.banner = ['4 is the most one person can buy.', 'Choose 4 tickets or fewer.'];
          } else if (e.code !== 'SOLD_OUT') {
            st.banner = ['Those seats just went to someone else.', 'The numbers below are up to date. Nothing was reserved.'];
          }
        }
      },
    },
  },
];
