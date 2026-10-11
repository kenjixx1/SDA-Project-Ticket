// SCR-08 Select tickets. Variants: a default, b holding, c at the limit of 4, d tier sold out,
// e more than remain, f seats just went, g everything sold out.
import { api } from '../api.js';
import { html, raw, baht, num, button } from '../parts.js';

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
  return html`<button class="tierrow ${sel ? 'sel' : ''}" data-action="pick" data-i="${i}" ${out || st.working ? raw('disabled') : ''}>
    <span class="radio">${sel ? html`<span class="tick"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="5 12.5 10 17 19 7"></polyline></svg></span>` : ''}</span>
    <span class="name">${t.name}</span>
    <span class="left ${out ? 'out' : low(t) ? 'low' : ''}">${left}</span>
    <span class="price">${baht(t.price)}</span>
  </button>`;
}

/** Moves away when this place in line can no longer hold seats. Returns true when it moved. */
async function placeEnded(ctx, eventId, token) {
  const q = await api.queue(token);
  if (q.state === 'SPENT') {
    ctx.go(q.reservationId ? `#/hold/${q.reservationId}` : `#/event/${eventId}`, { replace: true });
    return true;
  }
  if (q.state === 'EXPIRED') {
    ctx.go(`#/passed/${eventId}`, { replace: true }); // Q-3: the turn ends after 5 minutes
    return true;
  }
  return false;
}

export const screens = [
  {
    id: 'select',
    pattern: /^\/select\/([^/]+)\/([^/]+)$/,
    async enter(ctx, eventId, token) {
      if (await placeEnded(ctx, eventId, token)) return;
      ctx.state.sel = 0;
      ctx.state.qty = 1;
      await refresh(ctx, eventId);
    },
    poll: {
      every: 3000,
      async run(ctx, eventId, token) {
        if (ctx.state.working) return;
        await placeEnded(ctx, eventId, token);
      },
    },
    view(st) {
      const allSold = st.sel < 0;
      const tier = allSold ? null : st.tiers[st.sel];
      const atLimit = !allSold && st.qty >= LIMIT;
      const busy = st.working ? raw('disabled') : '';
      const off = st.working ? ' off' : '';
      return html`<section class="screen sel">
        <div class="selleft">
          <div class="selhead">
            <h1>${allSold ? 'Sold out' : 'You are in'}</h1>
            <p>${allSold ? 'Every ticket was sold while you were in line. Nothing was charged.' : 'Choose your tickets. Nothing is reserved yet.'}</p>
          </div>
          ${st.banner ? html`<div class="alert" role="status"><b>${st.banner[0]}</b><p>${st.banner[1]}</p></div>` : ''}
          <div class="tierrows${off}">${st.tiers.map((t, i) => tierRow(t, i, st))}</div>
        </div>
        <div class="selcard">
          ${allSold
            ? button({ label: 'Back to events', action: 'go', quiet: true, data: { to: '#/events' } })
            : html`<div class="qtyblock${off}">
                <div class="steprow"><b>How many</b>
                  <div class="step">
                    <button data-action="minus" aria-label="One fewer" ${st.qty <= 1 ? raw('disabled') : busy}>−</button>
                    <span class="n">${st.qty}</span>
                    <button data-action="plus" aria-label="One more" ${st.qty >= maxFor(tier) ? raw('disabled') : busy}>+</button>
                  </div>
                </div>
                <p class="shint ${atLimit ? 'stop' : ''}">${atLimit ? '4 is the most one person can buy.' : 'Maximum 4 per person'}</p>
              </div>
              <div class="totrow"><span class="what">${st.qty} × ${tier.name}</span><span class="sum">${baht(tier.price * st.qty)}</span></div>
              ${button({ label: 'Hold these seats', action: 'hold', working: st.working ? 'Holding…' : '' })}
              <p class="holdhint">Holding takes them out of the sale for 10 minutes</p>`}
        </div>
      </section>`;
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
          ctx.go(`#/hold/${h.reservationId}`, { replace: true });
        } catch (e) {
          st.working = false;
          if (e.code === 'NOT_ADMITTED') {
            if (!(await placeEnded(ctx, eventId, token))) ctx.go(`#/passed/${eventId}`, { replace: true });
            return;
          }
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
