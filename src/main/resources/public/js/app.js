// Customer app: Kenji buys tickets. Screens live in screens/; the router lives in router.js.
import { startApp } from './router.js';
import { topNav } from './parts.js';
import { screens as shell } from './screens/shell.js';
import { screens as event } from './screens/event.js';
import { screens as queue } from './screens/queue.js';
import { screens as select } from './screens/select.js';
import { screens as hold } from './screens/hold.js';
import { screens as pay } from './screens/pay.js';
import { screens as tickets } from './screens/tickets.js';

startApp({ screens: [...shell, ...event, ...queue, ...select, ...hold, ...pay, ...tickets], chrome: topNav });
