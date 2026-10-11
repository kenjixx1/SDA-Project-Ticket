// Organizer console: Chai creates and runs events. Screens: list (SCR-30), wizard (31–33), overview (34–39), dashboard (40).
import { startApp } from '../router.js';
import { orgTopBar } from './parts.js';
import { screens as list } from './list.js';
import { screens as wizard } from './wizard.js';
import { screens as overview } from './overview.js';
import { screens as dashboard } from './dashboard.js';

startApp({ screens: [...list, ...wizard, ...overview, ...dashboard], chrome: orgTopBar });
