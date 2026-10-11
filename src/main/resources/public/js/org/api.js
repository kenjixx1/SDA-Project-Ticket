// The organizer's messages to Java (O-01 to O-11, docs/09-web-api.md). Same envelope as the customer's.
import { call } from '../api.js';

const enc = encodeURIComponent;
const B = '/api/org/events';

export const org = {
  events: () => call('GET', B),                                                         // O-01
  create: (details) => call('POST', B, details),                                        // O-02
  event: (id) => call('GET', `${B}/${enc(id)}`),                                        // O-03
  update: (id, details) => call('PATCH', `${B}/${enc(id)}`, details),                   // O-04
  replaceTiers: (id, tiers) => call('PUT', `${B}/${enc(id)}/tiers`, { tiers }),         // O-05
  addTier: (id, tier) => call('POST', `${B}/${enc(id)}/tiers`, tier),                   // O-06
  updateTier: (id, tierId, tier) => call('PATCH', `${B}/${enc(id)}/tiers/${enc(tierId)}`, tier), // O-07
  publish: (id) => call('POST', `${B}/${enc(id)}/publish`),                             // O-08
  setRate: (id, admitPerSecond) => call('PUT', `${B}/${enc(id)}/rate`, { admitPerSecond }), // O-09
  close: (id) => call('POST', `${B}/${enc(id)}/close`),                                 // O-10
  dashboard: (id) => call('GET', `${B}/${enc(id)}/dashboard`),                          // O-11
};
