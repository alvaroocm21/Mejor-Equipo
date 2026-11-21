// Genera app/src/main/assets/{pokedex,moves}.tsv a partir del paquete npm "pokemon-showdown".
// Uso: npm pack pokemon-showdown && tar xzf pokemon-showdown-*.tgz && node tools/build-data.js package/dist/data
const fs = require('fs'), path = require('path');
const dir = process.argv[2];
const dex = require(path.resolve(dir, 'pokedex.js')).Pokedex;
const mv = require(path.resolve(dir, 'moves.js')).Moves;
const out = path.join(__dirname, '..', 'app', 'src', 'main', 'assets');
fs.mkdirSync(out, { recursive: true });
const P = [];
for (const [id, s] of Object.entries(dex)) {
  if (s.isNonstandard === 'CAP' || s.isNonstandard === 'Custom' || !s.types || !s.baseStats) continue;
  const b = s.baseStats;
  P.push([id, s.name, s.types.join('/'), [b.hp, b.atk, b.def, b.spa, b.spd, b.spe].join(','), Object.values(s.abilities).join('|'), s.evos && s.evos.length ? 1 : 0,
    /^(Mega|Primal)/.test(s.forme || '') ? (s.requiredItem || '') : '', /^(Mega|Primal)/.test(s.forme || '') ? (s.baseSpecies || '') : ''].join('\t'));
}
fs.writeFileSync(path.join(out, 'pokedex.tsv'), P.join('\n') + '\n');
const M = [];
for (const [id, m] of Object.entries(mv)) {
  if (m.isZ || m.isMax || m.isNonstandard === 'CAP' || m.isNonstandard === 'Custom') continue;
  const extra = [m.selfSwitch ? 'sw' : '', m.multihit ? 'mh' : '', m.damage ? 'fx' : '', m.ohko ? 'ko' : '', m.flags && m.flags.contact ? 'ct' : ''].filter(Boolean).join(',');
  M.push([id, m.name, m.type, m.category === 'Status' ? 'N' : m.category[0], m.basePower, m.accuracy === true ? 0 : m.accuracy, m.priority, m.target, extra].join('\t'));
}
fs.writeFileSync(path.join(out, 'moves.tsv'), M.join('\n') + '\n');
console.log(P.length, 'pokemon;', M.length, 'moves');
