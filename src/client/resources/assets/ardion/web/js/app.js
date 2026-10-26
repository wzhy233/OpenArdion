import { connect } from './socket.js';
import { bindGlobalEvents } from './events.js';
import { render } from './render.js';
import { syncRouteFromLocation } from './router.js';

syncRouteFromLocation();
bindGlobalEvents();
connect();

window.addEventListener('popstate', () => {
  syncRouteFromLocation();
  render(true);
});
