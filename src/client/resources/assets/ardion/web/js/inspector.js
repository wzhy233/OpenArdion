import { appState, currentModule } from './state.js';
import { settingTemplate, bindSettingEvents } from './settings.js';
import { escapeHtml } from './utils.js';
import { render } from './render.js';

export function renderInspector() {
  const details = document.getElementById('details');
  if (!details) return;
  if (!appState.detailsOpen) {
    details.innerHTML = '<div class="emptyState">Right-click a module to edit bind and settings.</div>';
    return;
  }

  const module = currentModule();
  if (!module) {
    details.innerHTML = '<div class="emptyState">No modules.</div>';
    return;
  }

  appState.selected = module.name;
  const values = module.values.filter(value => value.visible);
  if (!values.length) {
    appState.detailsOpen = false;
    appState.selected = null;
    document.querySelector('.shell').classList.remove('detailsOpen');
    details.innerHTML = '<div class="emptyState">Right-click a module to edit settings.</div>';
    return;
  }
  details.innerHTML = `
    <div class="inspectorContent">
      <div class="inspectorHeader">
        <h2>${escapeHtml(module.display)}</h2>
        <button class="closeInspector" id="closeInspector" type="button" aria-label="Close module settings">×</button>
      </div>
      <div class="subtle">${escapeHtml(module.category)} module</div>
      <div class="bindBox">
        <div>Keyboard bind</div>
        <button class="bindKey ${appState.listening ? 'listening' : ''}" id="bindKey" type="button">${appState.listening ? 'Press any key' : escapeHtml(module.bind)}</button>
      </div>
      <div class="settings">
        ${values.map(value => settingTemplate(module, value)).join('')}
      </div>
    </div>
  `;

  document.getElementById('closeInspector').addEventListener('click', () => {
    appState.detailsOpen = false;
    appState.listening = false;
    render(false);
  });
  document.getElementById('bindKey').addEventListener('click', () => {
    appState.listening = true;
    renderInspector();
  });
  bindSettingEvents(module, details);
}
