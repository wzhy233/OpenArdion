import { appState } from './state.js';

export function syncRouteFromLocation() {
  const path = location.pathname.replace(/\/$/, '') || '/modules';
  appState.settingsOpen = path === '/settings';
  appState.modelsOpen = path === '/models';
}

export function navigateTo(path) {
  const nextPath = path === '/settings' || path === '/models' ? path : '/modules';
  if (location.pathname !== nextPath) {
    history.pushState({}, '', nextPath);
  }
  syncRouteFromLocation();
}
