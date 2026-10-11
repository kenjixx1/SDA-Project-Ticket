// Picks the screen from the address, loads it, polls it, draws it, and runs its buttons. Shared by the customer app and the organizer console.
import { ApiError, serverNow, devMode } from './api.js';
import { html, skeleton, offlineBanner, notAvailable, mmss, hms, barPct, liveText } from './parts.js';
import { mountDev } from './dev.js';

export function startApp({ screens, chrome = () => '' }) {
  const SCREENS = screens;
  const appEl = document.getElementById('app');
  let run = null;

  function go(hash, { replace = false } = {}) {
    if (location.hash !== hash) history[replace ? 'replaceState' : 'pushState'](null, '', hash);
    route(true);
  }

  function stop() {
    if (!run) return;
    run.alive = false;
    clearInterval(run.timer);
    clearTimeout(run.retry);
    run = null;
  }

  function route(force = false) {
    const path = location.hash.replace(/^#/, '') || '/events';
    if (!force && run && run.path === path) return;
    stop();
    const def = SCREENS.find((d) => d.pattern.test(path));
    if (!def) {
      if (path !== '/events') return go('#/events', { replace: true });
      appEl.innerHTML = String(html`${chrome(path)}${notAvailable('This page is not available.')}`);
      return;
    }
    start(def, path.match(def.pattern).slice(1), path);
  }

  function start(def, params, path) {
    const me = { def, params, path, state: {}, alive: true, loaded: false, animated: false, zeroFired: false };
    run = me;
    // A screen that has been left can never navigate: late answers are ignored.
    me.ctx = { state: me.state, params, run: me, go: (hash, opts) => { if (me.alive) go(hash, opts); }, render: () => render(me), alive: () => me.alive };
    render(me);
    load(me);
    if (def.poll) {
      me.timer = setInterval(async () => {
        if (!me.loaded || me.busy || me.state.error) return;
        if (def.poll.paused && def.poll.paused(me.state)) return; // e.g. while a dialog is open, so typing keeps focus
        me.busy = true;
        await guard(me, () => def.poll.run(me.ctx, ...params));
        me.busy = false;
        render(me);
      }, def.poll.every);
    }
  }

  async function load(me) {
    const ok = await guard(me, () => (me.def.enter ? me.def.enter(me.ctx, ...me.params) : null));
    if (!me.alive) return;
    if (ok) {
      me.loaded = true;
      render(me);
    } else if (me.state.offline) {
      me.retry = setTimeout(() => me.alive && load(me), 3000);
    }
  }

  /** Runs fn. Offline or 5xx marks the screen offline. 404 shows "not available". Returns true when fn worked. */
  async function guard(me, fn) {
    try {
      await fn();
      me.state.offline = false;
      return true;
    } catch (e) {
      if (!me.alive) return false;
      if (e instanceof ApiError && (e.code === 'OFFLINE' || e.status >= 500)) {
        me.state.offline = true;
        render(me);
        return false;
      }
      console.error(e);
      me.state.error = e instanceof ApiError && e.status === 404 ? 'missing' : 'failed';
      me.loaded = true;
      render(me);
      return false;
    }
  }

  function render(me) {
    if (!me.alive) return;
    let body;
    if (me.state.error === 'missing') body = notAvailable('This page is not available.');
    else if (me.state.error === 'failed') body = notAvailable('Something went wrong. Please try again.');
    else if (!me.loaded) body = me.def.loading ? me.def.loading() : skeleton();
    else body = me.def.view(me.state, me.ctx);
    const top = me.state.offline && !me.def.ownOffline ? offlineBanner(!me.def.poll) : '';
    appEl.innerHTML = String(html`${chrome(me.path)}${top}${body}`);
    if (me.loaded && !me.animated) {
      me.animated = true;
      appEl.querySelector('.screen')?.classList.add('enter');
    }
    tick();
  }

  /** Updates countdowns and live dots without redrawing. Fires the screen's onZero once. */
  function tick() {
    const now = serverNow();
    let hitZero = false;
    document.querySelectorAll('[data-countdown]').forEach((el) => {
      const end = Number(el.dataset.countdown);
      const left = end - now;
      const f = el.dataset.fmt === 'hms' ? hms : mmss;
      el.querySelectorAll('[data-cd-text]').forEach((t) => { t.textContent = f(left); });
      el.querySelectorAll('[data-cd-bar]').forEach((b) => { b.style.width = barPct(end) + '%'; });
      if (left <= 0) hitZero = true;
    });
    document.querySelectorAll('[data-live]').forEach((el) => {
      const l = liveText();
      el.querySelector('.dot').classList.toggle('off', l.off);
      el.querySelector('[data-live-text]').textContent = l.text;
    });
    const r = run;
    if (hitZero && r && r.loaded && !r.zeroFired && r.def.onZero) {
      r.zeroFired = true;
      guard(r, () => r.def.onZero(r.ctx, ...r.params)).then(() => render(r));
    }
  }

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') document.querySelector('.sheet-back')?.click();
  });

  document.addEventListener('click', (e) => {
    const el = e.target.closest('[data-action]');
    if (!el || el.disabled || !run || !el.closest('#app')) return;
    const name = el.dataset.action;
    if (name === 'retry') return route(true);
    if (name === 'go') return go(el.dataset.to);
    const fn = run.def.actions && run.def.actions[name];
    if (!fn) return;
    const r = run;
    guard(r, () => fn(r.ctx, el.dataset, ...r.params)).then(() => render(r));
  });

  window.addEventListener('hashchange', () => route());
  setInterval(tick, 500);
  if (devMode) mountDev(() => route(true));
  route(true);
}
