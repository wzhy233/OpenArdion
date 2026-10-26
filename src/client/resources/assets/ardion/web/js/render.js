import { appState, categories } from './state.js';
import { renderNav } from './nav.js';
import { renderCards } from './cards.js';
import { renderInspector } from './inspector.js';

export function render(forceInspector = true) {
  if (!appState.data) return;
  if (!categories().includes(appState.category)) {
    appState.category = 'ALL';
  }
  const page = appState.modelsOpen ? 'models' : appState.settingsOpen ? 'settings' : 'modules';
  if (appState.page !== page) {
    appState.page = page;
    const grid = document.getElementById('grid');
    grid.replaceChildren();
    delete grid.dataset.modelsSignature;
  }
  document.querySelector('.shell').classList.toggle('detailsOpen', appState.detailsOpen);
  document.querySelector('.shell').classList.toggle('modelsOpen', page === 'models');
  document.getElementById('pageTitle').textContent = page === 'models' ? 'Models' : page === 'settings' ? 'Settings' : 'Modules';
  document.getElementById('pageDescription').textContent = page === 'models'
    ? 'Providers and the default model.'
    : page === 'settings'
      ? 'Client preferences. Changes are saved automatically.'
      : 'Toggle a module or open its settings to configure it.';
  const search = document.getElementById('search');
  search.placeholder = page === 'models' ? 'Search providers…' : page === 'settings' ? 'Search settings…' : 'Search modules…';
  search.setAttribute('aria-label', page === 'models' ? 'Search providers' : page === 'settings' ? 'Search settings' : 'Search modules');
  const status = document.getElementById('modelsStatus');
  status.hidden = page !== 'models' || !appState.data.modelsError;
  status.textContent = appState.data.modelsError || '';
  document.querySelectorAll('[data-route]').forEach(link => {
    const active = link.dataset.route === (page === 'models' ? '/models' : page === 'settings' ? '/settings' : '/modules');
    link.classList.toggle('active', active);
    if (active) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
  document.getElementById('pname').textContent = appState.data.player.name;
  document.getElementById('head').src = appState.data.player.head;
  const sessionState = document.getElementById('sessionState');
  sessionState.textContent = appState.data.player.sessionLabel || 'checking session';
  sessionState.dataset.status = appState.data.player.sessionStatus || 'checking';
  document.getElementById('version').textContent = 'OpenArdion';
  renderNav();
  renderGrid();
  const activeTag = document.activeElement?.tagName;
  if (appState.detailsOpen && (forceInspector || !['INPUT', 'SELECT'].includes(activeTag))) {
    renderInspector();
  }
}

export function renderGrid() {
  document.getElementById('grid').classList.toggle('settingsGrid', appState.settingsOpen || appState.modelsOpen);
  renderCards();
}

export function renderDetails() {
  document.querySelector('.shell').classList.toggle('detailsOpen', appState.detailsOpen);
  renderInspector();
}
