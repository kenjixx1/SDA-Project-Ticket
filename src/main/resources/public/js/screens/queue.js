// SCR-06 Queue (a waiting, b almost your turn, c welcome back, d reconnecting, e joining) and SCR-07 Your turn passed.
import { api, contactAge } from '../api.js';
import { html, num, dateShort, banner, liveIndicator, slimHeader, statusScreen } from '../parts.js';

/** Joins the back of the line again. Used by SCR-07, 10, 11 and 18. */
export async function joinAgain(ctx, eventId) {
  ctx.state.joining = true;
  ctx.render();
  const r = await api.join(eventId);
  ctx.go(`#/queue/${eventId}/${r.token}`);
}

const estimate = (q) =>
  q.position <= 50 ? 'less than a minute' : `about ${Math.max(1, Math.round(q.position / q.admitPerSecond / 60))} min`;

async function check(ctx, eventId, token) {
  const q = await api.queue(token);
  ctx.state.q = q;
  if (q.state === 'ADMITTED' && !ctx.state.flash) {
    ctx.state.flash = true;
    ctx.render();
    setTimeout(() => ctx.alive() && ctx.go(`#/select/${eventId}/${token}`, { replace: true }), 1200);
  } else if (q.state === 'EXPIRED') {
    ctx.go(`#/passed/${eventId}`, { replace: true });
  } else if (q.state === 'SPENT') {
    ctx.go(`#/event/${eventId}`, { replace: true });
  }
}

export const screens = [
  {
    id: 'queue',
    pattern: /^\/queue\/([^/]+)\/([^/]+?)(\/back)?$/,
    ownOffline: true,
    loading: () => html`<section class="screen">
      <div class="qhero"><div class="skel" style="width:220px;height:104px"></div><p class="sub">Getting your place in line…</p></div>
    </section>`,
    async enter(ctx, eventId, token, back) {
      ctx.state.ev = await api.event(eventId);
      if (back) {
        ctx.state.welcome = true;
        setTimeout(() => { ctx.state.welcome = false; ctx.render(); }, 5000);
      }
      await check(ctx, eventId, token);
    },
    poll: { every: 2000, run: (ctx, eventId, token) => check(ctx, eventId, token) },
    view(st) {
      const { ev, q } = st;
      const stale = st.offline || contactAge() > 15000;
      const almost = q.position <= 50;
      const pct = almost ? 99 : Math.round((q.moved / (q.moved + q.position)) * 100);
      const ahead = q.position - 1;
      const card = stale
        ? ['Still holding your place.', 'The server keeps it even if your connection drops.']
        : almost
          ? ['Almost your turn.', 'Get ready to choose your tickets.']
          : ['Your place is saved.', 'Keep this page open. When it is your turn you get 10 minutes to choose and pay.'];
      return html`<section class="screen">
        ${slimHeader(ev.shortName, `${ev.venue} · ${dateShort(ev.eventDateTime)}`, ev.artwork)}
        ${st.welcome ? banner('Welcome back.', 'Your place was kept.', true) : ''}
        <div class="qhero ${stale ? 'dim' : ''}">
          <span class="label">Your position</span>
          <span class="pos">${num(q.position)}</span>
          <span class="sub ahead">${ahead === 1 ? '1 person is ahead of you' : `${num(ahead)} people are ahead of you`}</span>
        </div>
        <div class="qbar"><i style="width:${pct}%"></i></div>
        <div class="qmeta"><span>Moved ${num(q.moved)} places</span><span class="eta">${estimate(q)}</span></div>
        <div class="card"><b>${card[0]}</b><p>${card[1]}</p></div>
      </section>
      <div class="foot bare">${liveIndicator()}</div>
      ${st.flash ? html`<div class="flash" role="status">It is your turn</div>` : ''}`;
    },
  },
  {
    id: 'passed',
    pattern: /^\/passed\/([^/]+)$/,
    enter(ctx, eventId) { ctx.state.eventId = eventId; },
    view: (st) => statusScreen({
      tone: 'calm',
      icon: '',
      title: 'Your turn has passed',
      text: 'You were let in, but no seats were chosen in time, so the next person in line went ahead. Nothing was reserved and nothing was charged.',
      card: ['You can try again.', 'Joining again puts you at the back of the line.'],
      primary: { label: 'Join the queue again', action: 'again', working: st.joining ? 'Joining…' : '' },
      secondary: { label: 'Back to the event', action: 'go', data: { to: `#/event/${st.eventId}` } },
    }),
    actions: { again: (ctx, data, eventId) => joinAgain(ctx, eventId) },
  },
];
