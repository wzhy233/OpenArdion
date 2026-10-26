import { send } from './socket.js';
import { escapeHtml } from './utils.js';

export function settingTemplate(module, value) {
  if (value.type === 'boolean') return booleanTemplate(value);
  if (value.type === 'number') return numberTemplate(value);
  if (value.type === 'mode') return modeTemplate(value);
  if (value.type === 'color') return colorTemplate(value);
  return stringTemplate(value);
}

function booleanTemplate(value) {
  const enabled = value.value === 'true';
  return `
    <div class="setting">
      <label><span>${escapeHtml(value.display)}</span><span>${enabled ? 'On' : 'Off'}</span></label>
      <button aria-pressed="${enabled}" class="boolButton ${enabled ? 'on' : ''}" aria-label="${escapeHtml(value.display)}" data-value="${escapeHtml(value.name)}" data-data="${enabled ? 'false' : 'true'}">${enabled ? 'Enabled' : 'Disabled'}</button>
    </div>
  `;
}

function numberTemplate(value) {
  const direct = value.name === 'proxy-port';
  return `
    <div class="setting">
      <label><span>${escapeHtml(value.display)}</span><span class="liveValue">${escapeHtml(value.value)}</span></label>
      <input class="${direct ? 'numberInput' : 'range'}" aria-label="${escapeHtml(value.display)}" data-value="${escapeHtml(value.name)}" type="${direct ? 'number' : 'range'}" ${direct ? 'required inputmode="numeric"' : ''} min="${value.min}" max="${value.max}" step="${value.integer ? '1' : '0.01'}" value="${escapeHtml(value.value)}">
    </div>
  `;
}

function modeTemplate(value) {
  return `
    <div class="setting">
      <label><span>${escapeHtml(value.display)}</span><span>${escapeHtml(value.value)}</span></label>
      <select aria-label="${escapeHtml(value.display)}" data-value="${escapeHtml(value.name)}">
        ${value.modes.map(mode => `<option ${mode === value.value ? 'selected' : ''}>${escapeHtml(mode)}</option>`).join('')}
      </select>
    </div>
  `;
}

function stringTemplate(value) {
  return `
    <div class="setting">
      <label><span>${escapeHtml(value.display)}</span><span>${escapeHtml(value.value)}</span></label>
      <input type="text" aria-label="${escapeHtml(value.display)}" data-value="${escapeHtml(value.name)}" value="${escapeHtml(value.value)}">
    </div>
  `;
}

function colorTemplate(value) {
  return `
    <div class="setting">
      <label><span>${escapeHtml(value.display)}</span><span class="liveValue">${intToHex(value.value)}</span></label>
      <input class="colorInput" type="color" aria-label="${escapeHtml(value.display)}" data-value="${escapeHtml(value.name)}" value="${intToHex(value.value)}">
    </div>
  `;
}

export function bindSettingEvents(module, root = document) {
  root.querySelectorAll('[data-value]').forEach(control => {
    const valueName = control.dataset.value;
    if (control.classList.contains('boolButton')) {
      control.addEventListener('click', () => sendMessage(module, valueName, control.dataset.data));
      return;
    }
    if (control.classList.contains('range') || control.type === 'number') {
      control.addEventListener('input', () => control.closest('.setting').querySelector('.liveValue').textContent = control.value);
    }
    if (control.classList.contains('colorInput')) {
      control.addEventListener('input', () => control.closest('.setting').querySelector('.liveValue').textContent = control.value);
      control.addEventListener('change', () => sendMessage(module, valueName, hexToSignedArgb(control.value)));
      return;
    }
    control.addEventListener('change', () => {
      if (control.type === 'number' && !control.reportValidity()) return;
      sendMessage(module, valueName, control.value);
    });
  });
}

function sendMessage(module, valueName, data) {
  if (module) {
    send({ type: 'value', module: module.name, value: valueName, data });
    return;
  }
  send({ type: 'setting', setting: valueName, data });
}

function intToHex(value) {
  const rgb = Number(value) & 0xFFFFFF;
  return `#${rgb.toString(16).padStart(6, '0')}`;
}

function hexToSignedArgb(value) {
  const rgb = Number.parseInt(value.replace('#', ''), 16) & 0xFFFFFF;
  return String((0xFF000000 | rgb) << 0);
}
