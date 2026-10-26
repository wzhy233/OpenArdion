import { appState, filteredModules, settingGroups } from './state.js';
import { settingTemplate, bindSettingEvents } from './settings.js';
import { renderDetails } from './render.js';
import { renderModels } from './models.js';
import { send } from './socket.js';
import { escapeHtml } from './utils.js';

export function moduleCard(module) {
  const configurable = hasVisibleSettings(module);
  return `
    <article class="moduleCard ${module.enabled ? 'enabled' : ''} ${configurable ? 'configurable' : ''} ${appState.detailsOpen && module.name === appState.selected ? 'selected' : ''} entering" data-module="${escapeHtml(module.name)}">
      <div class="moduleTop">
        <div>
          <div class="moduleTitle">${escapeHtml(module.display)}</div>
          <div class="moduleMeta">${escapeHtml(module.category)}</div>
        </div>
        <button class="toggle" type="button" data-toggle="${escapeHtml(module.name)}" role="switch" aria-checked="${module.enabled}" aria-label="Toggle ${escapeHtml(module.display)}"><span class="toggleKnob"></span></button>
      </div>
      <div class="moduleFooter">
        <span class="bindMini">Bind ${escapeHtml(module.bind)}</span>
        ${configurable ? '<button class="moduleSettings" type="button">Settings ↗</button>' : ''}
      </div>
    </article>
  `;
}

export function renderCards() {
  const grid = document.getElementById('grid');
  if (appState.modelsOpen) {
    renderModels(grid);
    return;
  }
  if (appState.settingsOpen) {
    renderSettingsCards(grid);
    return;
  }

  const modules = filteredModules();
  grid.querySelector('.emptyState')?.remove();
  if (!modules.length) grid.insertAdjacentHTML('beforeend', '<div class="emptyState">No modules match your search.</div>');
  const visibleNames = new Set(modules.map(module => module.name));

  grid.querySelectorAll('.moduleCard').forEach(card => {
    if (visibleNames.has(card.dataset.module)) return;
    card.classList.add('exiting');
    card.addEventListener('transitionend', () => card.remove(), { once: true });
    setTimeout(() => card.remove(), 620);
  });

  modules.forEach(module => {
    let card = grid.querySelector(`.moduleCard[data-module="${CSS.escape(module.name)}"]`);
    if (!card) {
      grid.insertAdjacentHTML('beforeend', moduleCard(module));
      card = grid.lastElementChild;
      bindCard(card);
      requestAnimationFrame(() => card.classList.remove('entering'));
    }
    updateCard(card, module);
    grid.appendChild(card);
  });
}

function bindCard(card) {
  card.addEventListener('click', () => {
    if (!appState.connected) return;
    const module = appState.data.modules.find(item => item.name === card.dataset.module);
    if (!module) return;
    card.classList.remove('toggleFade');
    void card.offsetWidth;
    card.classList.add('toggleFade');
    send({ type: 'toggle', module: module.name, enabled: !module.enabled });
  });

  function openSettings(event) {
    event.preventDefault();
    event.stopPropagation();
    if (!appState.connected) return;
    const module = appState.data.modules.find(item => item.name === card.dataset.module);
    if (!module || !hasVisibleSettings(module)) {
      if (appState.selected === card.dataset.module) {
        appState.detailsOpen = false;
        appState.listening = false;
        appState.selected = null;
        renderDetails();
        updateSelection();
      }
      return;
    }
    const alreadyOpen = appState.detailsOpen && appState.selected === module.name;
    appState.detailsOpen = !alreadyOpen;
    if (appState.detailsOpen) {
      appState.selected = module.name;
    }
    appState.listening = false;
    renderDetails();
    updateSelection();
  }

  card.addEventListener('contextmenu', openSettings);
  card.querySelector('.moduleSettings')?.addEventListener('click', openSettings);

  card.querySelector('[data-toggle]').addEventListener('click', event => {
    event.stopPropagation();
    if (!appState.connected) return;
    const module = appState.data.modules.find(item => item.name === event.currentTarget.dataset.toggle);
    if (!module) return;
    card.classList.remove('toggleFade');
    void card.offsetWidth;
    card.classList.add('toggleFade');
    send({ type: 'toggle', module: module.name, enabled: !module.enabled });
  });
}

function updateCard(card, module) {
  card.classList.toggle('enabled', module.enabled);
  card.classList.toggle('configurable', hasVisibleSettings(module));
  card.classList.toggle('selected', appState.detailsOpen && module.name === appState.selected);
  card.querySelector('.bindMini').textContent = `Bind ${module.bind}`;
  card.querySelector('[data-toggle]').setAttribute('aria-checked', String(module.enabled));
}

function updateSelection() {
  document.querySelectorAll('.moduleCard').forEach(card => {
    card.classList.toggle('selected', appState.detailsOpen && card.dataset.module === appState.selected);
  });
}

function renderSettingsCards(grid) {
  const needle = appState.query.toLowerCase();
  const groups = settingGroups().filter(group => {
    const categoryMatch = appState.category === 'ALL' || group.name.toUpperCase() === appState.category;
    const searchText = `${group.name} ${group.display} ${group.meta} ${(group.values || []).map(value => `${value.name} ${value.display}`).join(' ')}`.toLowerCase();
    return categoryMatch && searchText.includes(needle);
  });

  grid.innerHTML = groups.length
    ? groups.map(settingsGroupCard).join('')
    : '<div class="emptyState">No settings in this category.</div>';

  grid.querySelectorAll('[data-settings-group]').forEach(card => {
    bindSettingEvents(null, card);
  });
}

function settingsGroupCard(group) {
  const values = (group.values || []).filter(value => value.visible);
  return `
    <article class="moduleCard settingsCard" data-settings-group="${escapeHtml(group.name)}">
      <div class="moduleTop">
        <div>
          <div class="moduleTitle">${escapeHtml(group.display)}</div>
          <div class="moduleMeta">${escapeHtml(group.meta)}</div>
        </div>
      </div>
      <div class="settings">
        ${values.length ? values.map(value => settingTemplate(null, value)).join('') : '<div class="emptyState">No settings.</div>'}
      </div>
    </article>
  `;
}

function hasVisibleSettings(module) {
  return (module.values || []).some(value => value.visible);
}
