/* PlayerInfo dashboard (vanilla JS, no dependencies) */
const app = document.getElementById('app');

const esc = (s) => String(s ?? '').replace(/[&<>"']/g,
  (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = (n) => Number(n).toLocaleString('en-US');
const head = (name, s = 64) => `https://mc-heads.net/avatar/${encodeURIComponent(name)}/${s}`;
const timeAgo = (ms) => {
  if (!ms) return 'never';
  const m = Math.floor((Date.now() - ms) / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return m + 'm ago';
  const h = Math.floor(m / 60);
  if (h < 24) return h + 'h ago';
  return Math.floor(h / 24) + 'd ago';
};
const dateStr = (ms) => (ms > 0 ? new Date(ms).toLocaleString() : 'unknown');

async function api(path) {
  const r = await fetch('/api/' + path);
  if (!r.ok) {
    let msg = r.status;
    try { msg = (await r.json()).error || msg; } catch (_) { /* plain error */ }
    throw new Error(msg);
  }
  return r.json();
}

function fail(err) {
  app.innerHTML = `<div class="error">⚠ ${esc(err.message || err)}</div>`;
}

/* ---------- home ---------- */
async function renderHome() {
  app.innerHTML = '<div class="loading">Loading...</div>';
  const s = await api('server');
  document.title = s.siteName;
  document.getElementById('brand').textContent = s.siteName;
  const addr = s.serverAddress || location.host;
  const players = s.players.map((p) => `
    <a class="player-card" href="#/player/${encodeURIComponent(p.name)}">
      <img src="${head(p.name, 40)}" alt="">
      <div><div class="p-name">${esc(p.name)}</div>
      <div class="p-sub">${esc(p.gamemode)} — ${esc(p.world)} — ${p.ping}ms</div></div>
    </a>`).join('');
  app.innerHTML = `
    <div class="hero">
      <h1>${esc(s.siteName)}</h1>
      <div class="motd">${esc(s.motd)} — running ${esc(s.version)}</div>
      <div class="ip-box" id="copy-ip" title="Click to copy">
        <span>🖥</span><span class="addr">${esc(addr)}</span>
      </div>
      <div class="online-count"><b>${s.online}</b> / ${s.max} players online</div>
    </div>
    <section class="panel">
      <h2>Online players</h2>
      ${s.online ? `<div class="grid">${players}</div>` : '<div class="loading">Nobody is online right now.</div>'}
    </section>`;
  document.getElementById('copy-ip').onclick = () =>
    navigator.clipboard.writeText(addr).catch(() => {});
}

/* ---------- player profile ---------- */
async function renderProfile(name) {
  app.innerHTML = '<div class="loading">Loading profile...</div>';
  const [p, fightsData] = await Promise.all([
    api('player/' + encodeURIComponent(name)),
    api('fights').catch(() => ({ fights: [] })),
  ]);
  const fights = fightsData.fights.filter(
    (f) => f.winner.name === p.name || f.loser.name === p.name);
  const general = p.general.map((g) =>
    `<tr><td>${esc(g.label)}</td><td>${esc(g.value)}</td></tr>`).join('');
  const bd = (list) => list.length
    ? `<div class="chips">${list.map((e) =>
        `<span class="chip">${esc(e.name)} <b>${fmt(e.value)}</b></span>`).join('')}</div>`
    : '<div class="loading">None yet.</div>';
  const b = p.breakdowns;
  const pct = p.advancements.total ? Math.round(100 * p.advancements.done / p.advancements.total) : 0;

  app.innerHTML = `
    <div class="profile-head">
      <img src="${head(p.name, 72)}" alt="">
      <div>
        <h1>${esc(p.name)}</h1>
        <span class="badge ${p.online ? 'online' : 'offline'}">${p.online ? 'Online' : 'Offline'}</span>
        <div class="sub-line">First join: ${esc(dateStr(p.firstPlayed))} — Last seen: ${esc(dateStr(p.lastSeen))}</div>
      </div>
    </div>
    <section class="panel">
      <h2>Advancements</h2>
      <div class="progress-label">${p.advancements.done} / ${p.advancements.total} (${pct}%)</div>
      <div class="progress"><div style="width:${pct}%"></div></div>
    </section>
    <section class="panel"><h2>General statistics</h2>
      <table class="stat-table">${general}</table></section>
    <section class="panel"><h2>Blocks mined</h2>${bd(b.mined)}</section>
    <section class="panel"><h2>Items</h2>
      <h2 style="color:var(--text)">Crafted</h2>${bd(b.crafted)}
      <h2 style="color:var(--text)">Used</h2>${bd(b.used)}
      <h2 style="color:var(--text)">Broken</h2>${bd(b.broken)}
      <h2 style="color:var(--text)">Picked up</h2>${bd(b.picked_up)}
      <h2 style="color:var(--text)">Dropped</h2>${bd(b.dropped)}
      <h2 style="color:var(--text)">Enchanted</h2>${bd(b.enchanted)}
    </section>
    <section class="panel"><h2>Mobs killed</h2>${bd(p.mobs.killed)}</section>
    <section class="panel"><h2>Killed by</h2>${bd(p.mobs.killed_by)}</section>
    <section class="panel"><h2>Recent fights (${fights.length})</h2>${fightCards(fights)}</section>`;
}

/* ---------- fights ---------- */
function fightCards(fights) {
  if (!fights.length) return '<div class="loading">No recorded fights yet.</div>';
  return fights.map((f) => `
    <div class="fight">
      <div class="time">Fight #${f.id} — ${timeAgo(f.time)}</div>
      <div class="duel">
        ${panel(f.winner, 'Winner', 'panel-w')}
        ${panel(f.loser, 'Defeated', 'panel-l')}
      </div>
    </div>`).join('');
}

function panel(side, title, cls) {
  const stats = [
    [side.hits, 'Hits'], [side.hitsTaken, 'Hits taken'], [side.crits, 'Crits'],
    [side.bestCombo, 'Best combo'], [fmt(Math.round(side.damageDealt * 10) / 10), 'Damage dealt'],
    [fmt(Math.round(side.damageTaken * 10) / 10), 'Damage taken'],
    [side.arrowsHit, 'Arrows hit'], [side.potsThrown, 'Pots thrown'], [side.totems, 'Totems'],
  ];
  return `
    <div class="${cls}">
      <div class="panel-top">
        <img src="${head(side.name, 40)}" alt="">
        <div><div class="title">${esc(title)}</div>
        <a href="#/player/${encodeURIComponent(side.name)}">${esc(side.name)}</a></div>
      </div>
      <div class="stat-grid">
        ${stats.map(([v, k]) => `<div class="stat"><div class="v">${esc(v)}</div><div class="k">${esc(k)}</div></div>`).join('')}
      </div>
    </div>`;
}

async function renderFights() {
  app.innerHTML = '<div class="loading">Loading fights...</div>';
  const data = await api('fights');
  document.getElementById('brand').textContent = SITE.siteName;
  app.innerHTML = `<section class="panel"><h2>Recent fights</h2>${fightCards(data.fights)}</section>`;
}

async function renderLeaderboard(category) {
  app.innerHTML = '<div class="loading">Loading leaderboard...</div>';
  const data = await api('leaderboard/' + encodeURIComponent(category));
  const rows = data.entries.map((e) => `
    <tr><td style="width:60px;color:var(--gold)">#${e.rank}</td>
    <td><img src="${head(e.name, 24)}" style="width:24px;height:24px;image-rendering:pixelated;vertical-align:middle;border-radius:3px">
    <a href="#/player/${encodeURIComponent(e.name)}" style="margin-left:8px">${esc(e.name)}</a></td>
    <td style="text-align:right;font-family:Consolas,monospace;color:var(--aqua)">${fmt(e.value)}</td></tr>`).join('');
  app.innerHTML = `<section class="panel"><h2>Leaderboard — ${esc(data.category)}</h2>
    <table class="stat-table">${rows}</table></section>`;
}

/* ---------- router ---------- */
let SITE = { siteName: 'Server Stats', serverAddress: '' };
async function loadServer() {
  try { SITE = { ...SITE, ...(await api('server')) }; } catch (_) { /* offline snapshot later */ }
  document.title = SITE.siteName;
  document.getElementById('brand').textContent = SITE.siteName;
}

function route() {
  const hash = location.hash.replace(/^#/, '');
  renderPage(hash).catch(fail);
}

async function renderPage(hash) {
  if (hash.startsWith('/player/')) {
    await renderProfile(decodeURIComponent(hash.slice('/player/'.length)));
  } else if (hash === '/fights') {
    await renderFights();
  } else if (hash.startsWith('/leaderboard/')) {
    await renderLeaderboard(decodeURIComponent(hash.slice('/leaderboard/'.length)));
  } else {
    await renderHome();
  }
}

document.getElementById('search').addEventListener('submit', (e) => {
  e.preventDefault();
  const value = document.getElementById('search-box').value.trim();
  if (value) location.hash = '#/player/' + encodeURIComponent(value);
});

window.addEventListener('hashchange', route);
loadServer().then(route);
