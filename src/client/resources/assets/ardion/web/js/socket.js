import { appState, setState } from './state.js';
import { render } from './render.js';

export function connect() {
  appState.socket = new WebSocket(`ws://${location.host}/ws`);
  appState.socket.onopen = () => {
    appState.connected = true;
    const status = document.getElementById('status');
    status.textContent = 'online';
    status.className = 'connection online';
  };
  appState.socket.onclose = () => {
    appState.connected = false;
    appState.detailsOpen = false;
    appState.listening = false;
    const status = document.getElementById('status');
    status.textContent = 'reconnecting';
    status.className = 'connection';
    document.getElementById('loaderText').textContent = 'Reconnecting to OpenArdion';
    document.getElementById('loader').classList.remove('hide');
    document.querySelector('.shell').classList.remove('detailsOpen');
    setTimeout(connect, 700);
  };
  appState.socket.onmessage = event => {
    const data = JSON.parse(event.data);
    const pending = appState.modelsPending;
    setState(data);
    if (pending && data.modelsSave === pending) {
      appState.modelsPending = 0;
      if (!data.modelsError) appState.modelsDirty = false;
    }
    render(false);
    document.getElementById('loaderText').textContent = 'Connected to OpenArdion';
    document.getElementById('loader').classList.add('hide');
  };
}

export function send(payload) {
  if (appState.connected && appState.socket && appState.socket.readyState === WebSocket.OPEN) {
    appState.socket.send(JSON.stringify(payload));
  }
}
