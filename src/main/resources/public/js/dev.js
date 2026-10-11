// Demo controls, shown only when the address has ?dev. Calls the /dev messages of the fake.
import { api } from './api.js';

const SCENARIOS = ['RACE_LOST', 'SOLD_OUT_ALL', 'PROVIDER_DOWN', 'ALL_PROVIDERS_DOWN', 'BANK_SLOW', 'TURN_PASSED', 'EVENT_CANCELLED', 'SALE_CLOSED'];

export function mountDev(refresh) {
  const el = document.getElementById('dev');
  el.innerHTML = `<div class="panel"><b>Demo controls</b>
    <div class="row2">${[0, 1, 10, 30, 100].map((n) => `<button data-dev="speed" data-n="${n}">${n === 0 ? 'Pause' : `×${n}`}</button>`).join('')}</div>
    <div class="row2"><button data-dev="advance" data-n="60">+1 min</button><button data-dev="advance" data-n="350">+6 min</button><button data-dev="advance" data-n="600">+10 min</button></div>
    <select id="dev-scenario" aria-label="Scenario">${SCENARIOS.map((s) => `<option>${s}</option>`).join('')}</select>
    <div class="row2"><button data-dev="scenario">Set scenario</button><button data-dev="reset">Reset</button></div>
    <span id="dev-msg"></span></div>`;
  el.addEventListener('click', async (e) => {
    const b = e.target.closest('[data-dev]');
    if (!b) return;
    const msg = el.querySelector('#dev-msg');
    try {
      const kind = b.dataset.dev;
      if (kind === 'speed') await api.dev.speed(Number(b.dataset.n));
      if (kind === 'advance') await api.dev.advance(Number(b.dataset.n));
      if (kind === 'scenario') await api.dev.scenario(el.querySelector('#dev-scenario').value);
      if (kind === 'reset') {
        await api.dev.reset();
        history.replaceState(null, '', '#/events');
      }
      msg.textContent = 'Done';
      refresh();
    } catch (err) {
      msg.textContent = err.message;
    }
  });
}
