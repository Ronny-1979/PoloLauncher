// Usage: npm install && node build.mjs <sprites-dir> <assets-out-dir>
//   <sprites-dir>: entpacktes sprites.tar.gz der VersaTiles-Releases (base.json/png, base@2x.json/png)
// Erzeugt layers_full.jsonl, layers_lite.jsonl, layers_full_3d.jsonl, sprite*.{json,png}.
import { osm } from '@versatiles/style';
import fs from 'fs';
import path from 'path';

const [spriteDir, outDir] = process.argv.slice(2);
if (!spriteDir || !outDir) { console.error('usage: node build.mjs <sprites-dir> <assets-out-dir>'); process.exit(2); }
fs.mkdirSync(outDir, { recursive: true });

const FONT = ['Open Sans Regular', 'Arial Unicode MS Regular'];

// Ebenen, die auf dem Autoradio nur Rechenzeit kosten oder ohne Daten sinnlos sind.
const DROP = [
  /^slot-/, /^background$/, /^label-address/, /^aerialway/, /^boundary-country/, /^label-boundary-/,
  /^water-dam/, /^water-pier/, /^symbol-transit-(bus|tram)$/, /^tunnel-transport-/, /^bridge-transport-/,
  /-bicycle$/, /street-bus/, /^airport-taxiway/,
];
const drop = id => DROP.some(re => re.test(id));

const LITE = new Set([
  'water-ocean', 'land-forest', 'water-area', 'water-area-river', 'water-river',
  'boundary-state:outline', 'boundary-state',
  'street-trunk:outline', 'street-trunk', 'street-motorway:outline', 'street-motorway',
  'label-place-town', 'label-place-city', 'label-place-statecapital', 'label-place-capital',
]);

function fix(value) {
  if (typeof value === 'string') return value.startsWith('base:') ? value.slice(5) : value;
  if (Array.isArray(value)) return value.map(fix);
  if (value && typeof value === 'object') {
    const o = {}; for (const [k, v] of Object.entries(value)) o[k] = fix(v); return o;
  }
  return value;
}

function convert(layer) {
  const l = fix(JSON.parse(JSON.stringify(layer)));
  l.id = '@SRC@:' + l.id;
  l.source = '@SRC@';
  if (l.layout && l.layout['text-font']) l.layout['text-font'] = FONT;
  delete l.metadata;
  return l;
}

// Zwei Themes: "freedom" (OSM-Liberty-Optik, Tag) und "colorful-dark" (derselbe Ebenenaufbau, Nacht).
const THEMES = [
  { theme: 'freedom', suffix: '', building: ['rgb(226,222,216)', 'rgb(214,208,200)'], opacity: 0.92 },
  { theme: 'colorful-dark', suffix: '_dark', building: ['rgb(62,57,51)', 'rgb(76,70,62)'], opacity: 0.95 },
];
for (const t of THEMES) {
const style = osm({ theme: t.theme, text: { language: 'de' } });
const kept = style.layers.filter(l => !drop(l.id));
const full = kept.map(convert);
const lite = kept.filter(l => LITE.has(l.id)).map(convert);

// 3D-Variante: das flache Gebaeude durch eine Extrusion ersetzen. Shortbread 1.0 liefert KEINE
// Gebaeudehoehen (nur die Property "dummy"); deshalb Standardhoehe, sobald "height" fehlt.
const full3d = full.map(l => {
  if (l.id !== '@SRC@:building') return l;
  return {
    id: l.id, type: 'fill-extrusion', source: '@SRC@', 'source-layer': 'buildings', minzoom: 14,
    paint: {
      'fill-extrusion-color': ['interpolate', ['linear'], ['zoom'], 14, t.building[0], 17, t.building[1]],
      'fill-extrusion-base': 0,
      'fill-extrusion-height': ['interpolate', ['linear'], ['zoom'], 14, 0, 15.5,
        ['case', ['has', 'height'], ['to-number', ['get', 'height'], 9], 9]],
      'fill-extrusion-opacity': t.opacity,
      'fill-extrusion-vertical-gradient': true,
    },
  };
});

const jsonl = list => list.map(l => JSON.stringify(l)).join('\n') + '\n';
fs.writeFileSync(path.join(outDir, `layers_full${t.suffix}.jsonl`), jsonl(full));
fs.writeFileSync(path.join(outDir, `layers_full_3d${t.suffix}.jsonl`), jsonl(full3d));
fs.writeFileSync(path.join(outDir, `layers_lite${t.suffix}.jsonl`), jsonl(lite));
console.log(t.theme, 'background', style.layers.find(l => l.id === 'background')?.paint?.['background-color']);
console.log('layers full/3d/lite:', full.length, full3d.length, lite.length);
}

// Sprite: ein einziges Legacy-Sprite, nur die von MapLibre gelesenen Felder.
for (const suffix of ['', '@2x']) {
  const meta = JSON.parse(fs.readFileSync(path.join(spriteDir, `base${suffix}.json`), 'utf8'));
  const slim = {};
  for (const [name, e] of Object.entries(meta)) {
    slim[name] = { width: e.width, height: e.height, x: e.x, y: e.y, pixelRatio: e.pixelRatio };
    if (e.sdf) slim[name].sdf = true;
  }
  fs.writeFileSync(path.join(outDir, `sprite${suffix}.json`), JSON.stringify(slim));
  fs.copyFileSync(path.join(spriteDir, `base${suffix}.png`), path.join(outDir, `sprite${suffix}.png`));
}
