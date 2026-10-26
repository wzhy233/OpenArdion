export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"]/g, char => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;'
  }[char]));
}

export function keyName(event) {
  if (event.key === 'Escape') return 'none';
  if (event.key === ' ') return 'SPACE';
  return event.key.toUpperCase().replace(' ', 'SPACE');
}
