import { appState, categories } from './state.js';
import { render } from './render.js';
import { navigateTo } from './router.js';
import { escapeHtml } from './utils.js';

export function renderNav() {
  const nav = document.getElementById('nav');
  nav.innerHTML = categories().map(category => `
    <button class="navButton ${category === appState.category ? 'active' : ''}" data-category="${escapeHtml(category)}" aria-pressed="${category === appState.category}">
      <span class="navDot"></span><span>${escapeHtml(({ ALL: 'All', AI: 'AI', HUD: 'HUD' })[category] || category.charAt(0) + category.slice(1).toLowerCase())}</span>
    </button>
  `).join('');

  nav.querySelectorAll('.navButton').forEach(button => {
    button.addEventListener('click', () => {
      appState.category = button.dataset.category;
      appState.detailsOpen = false;
      appState.listening = false;
      navigateTo(appState.settingsOpen ? '/settings' : '/modules');
      render(true);
    });
  });
}
