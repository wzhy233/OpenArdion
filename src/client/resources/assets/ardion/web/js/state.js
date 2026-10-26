export const appState = {
  socket: null,
  data: null,
  category: 'ALL',
  selected: null,
  detailsOpen: false,
  settingsOpen: false,
  modelsOpen: false,
  modelsDirty: false,
  modelsPending: 0,
  modelsDraft: null,
  modelsServer: '',
  page: '',
  connected: false,
  listening: false,
  query: ''
};

export function setState(data) {
  appState.data = data;
  if (!appState.selected && data.modules.length > 0) {
    appState.selected = data.modules[0].name;
  }
}

export function currentModule() {
  return appState.data?.modules.find(module => module.name === appState.selected) || appState.data?.modules[0] || null;
}

export function globalSettings() {
  return appState.data?.settings || [];
}

export function settingGroups() {
  if (appState.data?.settingGroups?.length) {
    return appState.data.settingGroups;
  }
  return globalSettings().length
    ? [{ name: 'global', display: 'Global', meta: 'client wide behavior', values: globalSettings() }]
    : [];
}

export function categories() {
  const modules = appState.data?.modules || [];
  if (appState.settingsOpen) {
    return ['ALL', ...settingGroups().map(group => group.name.toUpperCase())];
  }
  return ['ALL', ...new Set(modules.map(module => module.category))];
}

export function filteredModules() {
  const needle = appState.query.toLowerCase();
  return (appState.data?.modules || []).filter(module => {
    return (appState.category === 'ALL' || module.category === appState.category)
      && `${module.name} ${module.display} ${module.category}`.toLowerCase().includes(needle);
  });
}
