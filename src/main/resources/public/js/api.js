// The ONLY file that talks to Java. Every answer is {serverNow, clockRate, data} or {serverNow, clockRate, error}.
const params = new URLSearchParams(location.search);
export const customer = (params.get('as') || 'kenji').toLowerCase().replace(/[^a-z0-9-]/g, '') || 'kenji';
export const devMode = params.has('dev');

let lastServer = 0;
let lastContact = 0;
let rate = 1;

/** The server's clock, run forward from its last answer at its own rate. Countdowns use this, never the phone's clock. */
export const serverNow = () => (lastContact ? lastServer + (Date.now() - lastContact) * rate : Date.now());
/** Milliseconds since the server last answered. */
export const contactAge = () => (lastContact ? Date.now() - lastContact : 0);

export class ApiError extends Error {
  constructor(status, code, message, remaining) {
    super(message);
    this.status = status;
    this.code = code;
    this.remaining = remaining;
  }
}

async function call(method, path, body) {
  let res;
  try {
    res = await fetch(path, {
      method,
      headers: { 'X-Customer': customer, ...(body ? { 'Content-Type': 'application/json' } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'OFFLINE', 'No connection', null);
  }
  let json = null;
  try {
    json = await res.json();
  } catch {
    json = null;
  }
  if (json && typeof json.serverNow === 'number') {
    lastServer = json.serverNow;
    lastContact = Date.now();
    rate = typeof json.clockRate === 'number' ? json.clockRate : 1;
  }
  if (!res.ok) {
    const e = (json && json.error) || {};
    throw new ApiError(res.status, e.code || 'HTTP_' + res.status, e.message || 'Request failed', e.remaining ?? null);
  }
  return json.data;
}

const enc = encodeURIComponent;

export const api = {
  events: () => call('GET', '/api/events'),                                              // M-01
  event: (id) => call('GET', `/api/events/${enc(id)}`),                                  // M-02
  join: (id) => call('POST', `/api/events/${enc(id)}/queue`),                            // M-03
  queue: (token) => call('GET', `/api/queue/${enc(token)}`),                             // M-04
  availability: (id) => call('GET', `/api/events/${enc(id)}/availability`),              // M-05
  hold: (token, tierId, quantity) => call('POST', '/api/holds', { token, tierId, quantity }), // M-06
  getHold: (rid) => call('GET', `/api/holds/${enc(rid)}`),                               // M-07
  release: (rid) => call('DELETE', `/api/holds/${enc(rid)}`),                            // M-08
  startPayment: (rid, providerKey) => call('POST', `/api/holds/${enc(rid)}/payments`, { providerKey }), // M-09
  answer: (pid, approve) => call('POST', `/api/payments/${enc(pid)}/answer`, { approve }), // M-10
  payment: (pid) => call('GET', `/api/payments/${enc(pid)}`),                            // M-11
  orders: () => call('GET', '/api/me/orders'),                                           // M-12
  me: () => call('GET', '/api/me'),                                                      // M-13
  dev: {
    scenario: (scenario) => call('POST', '/dev/scenario', { scenario }),
    speed: (times) => call('POST', '/dev/speed', { times }),
    advance: (seconds) => call('POST', '/dev/advance', { seconds }),
    reset: () => call('POST', '/dev/reset'),
  },
};
