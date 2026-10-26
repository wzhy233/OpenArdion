import { appState } from './state.js';
import { send } from './socket.js';
import { escapeHtml } from './utils.js';

let saveSeq = 1;

export function renderModels(grid) {
  const draft = syncDraft();
  const active = document.activeElement;
  if (grid.contains(active) && active.matches('input, select')) return;
  const signature = appState.query + '\0' + JSON.stringify(draft);
  if (grid.dataset.modelsSignature === signature && grid.querySelector('[data-models-root]')) return;
  grid.dataset.modelsSignature = signature;
  const needle = appState.query.toLowerCase();
  const providers = draft.providers
    .map((provider, index) => ({ provider, index }))
    .filter(({ provider }) => `${provider.id} ${provider.name} ${provider.baseURL}`.toLowerCase().includes(needle));
  grid.innerHTML = `
    <div data-models-root>
      <article class="moduleCard settingsCard">
        <div class="moduleTop">
          <div>
            <div class="moduleTitle">Default</div>
            <div class="moduleMeta">provider/model</div>
          </div>
        </div>
        <div class="settings">
          <div class="setting">
            <label><span>Model</span></label>
            <select data-field="model">${modelOptions(draft)}</select>
          </div>
        </div>
      </article>
      ${providers.map(({ provider, index }) => providerCard(provider, index)).join('') || '<div class="emptyState">No providers.</div>'}
      <div class="modelActions">
        <button class="chip" type="button" data-add-provider>Add provider</button>
        <button class="chip" type="button" data-save-models>Save</button>
      </div>
    </div>
  `;
  const root = grid.querySelector('[data-models-root]');
  root.addEventListener('input', onEdit);
  root.addEventListener('change', onEdit);
  root.addEventListener('click', onClick);
}

function syncDraft() {
  const incoming = appState.data?.agent || { model: '', providers: [] };
  const serialized = JSON.stringify(incoming);
  if (!appState.modelsDraft || (!appState.modelsDirty && !appState.modelsPending && serialized !== appState.modelsServer)) {
    appState.modelsServer = serialized;
    appState.modelsDraft = JSON.parse(serialized);
    for (const provider of appState.modelsDraft.providers || []) provider.key = provider.id || '';
  }
  appState.modelsDraft.model ||= '';
  appState.modelsDraft.providers ||= [];
  return appState.modelsDraft;
}

function modelOptions(draft) {
  const refs = [];
  for (const provider of draft.providers) {
    for (const model of provider.models || []) {
      if (!provider.id || !model.id) continue;
      refs.push(`${provider.id}/${model.id}`);
    }
  }
  if (draft.model && !refs.includes(draft.model)) refs.unshift(draft.model);
  return [`<option value=""${draft.model ? '' : ' selected'}>None</option>`]
    .concat(refs.map(ref => `<option value="${escapeHtml(ref)}"${ref === draft.model ? ' selected' : ''}>${escapeHtml(ref)}</option>`))
    .join('');
}

function providerCard(provider, index) {
  const models = provider.models || [];
  return `
    <article class="moduleCard settingsCard" data-provider-index="${index}">
      <div class="moduleTop">
        <div>
          <div class="moduleTitle">${escapeHtml(provider.name || provider.id || 'Provider')}</div>
          <div class="moduleMeta">${escapeHtml(provider.id || 'new')}</div>
        </div>
        <button class="moduleSettings" type="button" data-remove-provider>Remove</button>
      </div>
      <div class="settings">
        <div class="setting">
          <label><span>ID</span></label>
          <input type="text" data-field="id" value="${escapeHtml(provider.id)}" spellcheck="false" autocomplete="off">
        </div>
        <div class="setting">
          <label><span>Name</span></label>
          <input type="text" data-field="name" value="${escapeHtml(provider.name)}" autocomplete="off">
        </div>
        <div class="setting">
          <label><span>Base URL</span></label>
          <input type="text" data-field="baseURL" value="${escapeHtml(provider.baseURL)}" spellcheck="false" autocomplete="off">
        </div>
        <div class="setting">
          <label><span>API key</span></label>
          <input type="text" data-field="apiKey" value="${escapeHtml(provider.apiKey)}" spellcheck="false" autocomplete="off">
        </div>
        <div class="setting">
          <label><span>Models</span></label>
          <div class="modelList">
            ${models.map((model, modelIndex) => `
              <div class="modelRow" data-model-index="${modelIndex}">
                <input type="text" data-field="model-id" value="${escapeHtml(model.id)}" placeholder="ID" spellcheck="false" autocomplete="off">
                <input type="text" data-field="model-name" value="${escapeHtml(model.name)}" placeholder="Name" autocomplete="off">
                <button class="moduleSettings" type="button" data-remove-model>Remove</button>
              </div>
            `).join('')}
            <button class="chip" type="button" data-add-model>Add model</button>
          </div>
        </div>
      </div>
    </article>
  `;
}

function onEdit(event) {
  const field = event.target.dataset.field;
  if (!field) return;
  const draft = appState.modelsDraft;
  appState.modelsDirty = true;
  if (field === 'model') {
    draft.model = event.target.value;
    return;
  }
  const card = event.target.closest('[data-provider-index]');
  const provider = draft.providers[Number(card.dataset.providerIndex)];
  if (field === 'model-id' || field === 'model-name') {
    const row = provider.models[Number(event.target.closest('[data-model-index]').dataset.modelIndex)];
    row[field === 'model-id' ? 'id' : 'name'] = event.target.value;
    if (field === 'model-id') refreshDefault(draft);
    return;
  }
  provider[field] = event.target.value;
  if (field === 'id' || field === 'name') {
    card.querySelector('.moduleTitle').textContent = provider.name || provider.id || 'Provider';
    card.querySelector('.moduleMeta').textContent = provider.id || 'new';
  }
  if (field === 'id') refreshDefault(draft);
}

function refreshDefault(draft) {
  const select = document.querySelector('[data-models-root] [data-field="model"]');
  if (!select || document.activeElement === select) return;
  select.innerHTML = modelOptions(draft);
}

function onClick(event) {
  const draft = appState.modelsDraft;
  const grid = event.currentTarget.parentElement;
  if (event.target.closest('[data-add-provider]')) {
    draft.providers.push({ key: '', id: '', name: '', baseURL: '', apiKey: '', models: [] });
    appState.modelsDirty = true;
    appState.query = '';
    const search = document.getElementById('search');
    if (search) search.value = '';
    grid.dataset.modelsSignature = '';
    renderModels(grid);
    return;
  }
  if (event.target.closest('[data-save-models]')) {
    save(draft);
    return;
  }
  const card = event.target.closest('[data-provider-index]');
  if (!card) return;
  const index = Number(card.dataset.providerIndex);
  if (event.target.closest('[data-remove-provider]')) {
    draft.providers.splice(index, 1);
    changed(grid);
    return;
  }
  const row = event.target.closest('[data-model-index]');
  if (event.target.closest('[data-remove-model]') && row) {
    draft.providers[index].models.splice(Number(row.dataset.modelIndex), 1);
    changed(grid);
    return;
  }
  if (event.target.closest('[data-add-model]')) {
    draft.providers[index].models.push({ id: '', name: '' });
    changed(grid);
  }
}

function changed(grid) {
  appState.modelsDirty = true;
  grid.dataset.modelsSignature = '';
  renderModels(grid);
}

function save(draft) {
  if (!appState.socket || appState.socket.readyState !== WebSocket.OPEN) return;
  const id = saveSeq++;
  appState.modelsPending = id;
  send({
    type: 'models',
    save: id,
    model: draft.model || '',
    providers: draft.providers.map(provider => ({
      key: provider.key || '',
      id: provider.id || '',
      name: provider.name || '',
      baseURL: provider.baseURL || '',
      apiKey: provider.apiKey || '',
      models: (provider.models || []).map(model => ({ id: model.id || '', name: model.name || '' }))
    }))
  });
}
