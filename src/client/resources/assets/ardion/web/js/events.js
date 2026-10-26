import { appState } from './state.js';
import { render, renderDetails } from './render.js';
import { send } from './socket.js';
import { keyName } from './utils.js';
import { navigateTo } from './router.js';

export function bindGlobalEvents() {
  const search = document.getElementById('search');
  search.addEventListener('input', event => {
    appState.query = event.target.value;
    render(true);
  });

  document.querySelectorAll('[data-route]').forEach(link => link.addEventListener('click', event => {
    if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    appState.detailsOpen = false;
    appState.listening = false;
    appState.category = 'ALL';
    appState.query = '';
    search.value = '';
    navigateTo(link.dataset.route);
    render(true);
  }));

  document.getElementById('clearSearch').addEventListener('click', () => {
    appState.query = '';
    search.value = '';
    render(true);
    search.focus();
  });

  window.addEventListener('keydown', event => {
    if (!appState.listening) return;
    event.preventDefault();
    send({ type: 'bind', module: appState.selected, key: keyName(event) });
    appState.listening = false;
    renderDetails();
  });
}
