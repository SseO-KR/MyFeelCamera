/*
 * Render English promotional layouts around the user's original photo samples.
 * Photos are embedded whole: no filters, retouching, or cropping.
 * Run with Node.js and sharp available, or set FEELCAMERA_SHARP_MODULE.
 */
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const sharp = require(process.env.FEELCAMERA_SHARP_MODULE || 'sharp');

const directory = __dirname;
const english = path.resolve(directory, '../en-US');
const palette = { background: '#F3EEE6', ink: '#25221E', muted: '#6D665D', amber: '#B58139' };

function svg(width, height, body) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
    <rect width="${width}" height="${height}" fill="${palette.background}"/>
    <g font-family="Helvetica Neue, Arial, sans-serif" fill="${palette.ink}">${body}</g>
  </svg>`;
}

function image(buffer, x, y, width, height) {
  return `<image x="${x}" y="${y}" width="${width}" height="${height}" preserveAspectRatio="xMidYMid meet" href="data:image/png;base64,${buffer.toString('base64')}"/>`;
}

async function render(filename, width, height, body) {
  const output = path.join(english, filename);
  // This locally authored SVG embeds the two dimension-checked PNGs as base64.
  // Permit those long attributes; the canvas and source photo sizes are bounded.
  await sharp(Buffer.from(svg(width, height, body)), { unlimited: true })
    .flatten({ background: palette.background }).removeAlpha().toColourspace('srgb')
    .png({ compressionLevel: 9 }).toFile(output);
  const metadata = await sharp(output).metadata();
  if (metadata.width !== width || metadata.height !== height || metadata.hasAlpha) {
    throw new Error(`Unexpected export specification: ${filename}`);
  }
  return { file: `en-US/${filename}`, width, height, hasAlpha: metadata.hasAlpha, bytes: (await fs.stat(output)).size };
}

async function main() {
  await fs.mkdir(english, { recursive: true });
  const dusk = await fs.readFile(path.join(directory, 'sources/dusk.png'));
  const warm = await fs.readFile(path.join(directory, 'sources/warm.png'));
  const sources = [];
  for (const [film, buffer] of [['DUSK', dusk], ['WARM', warm]]) {
    const metadata = await sharp(buffer).metadata();
    if (metadata.width / metadata.height !== 3 / 4) throw new Error('Expected uncropped 3:4 source photo');
    sources.push({ film, file: `sources/${film.toLowerCase()}.png`, width: metadata.width, height: metadata.height,
      sha256: crypto.createHash('sha256').update(buffer).digest('hex'),
      source: film === 'DUSK' ? 'User attachment 1' : 'User attachment 2' });
  }

  const assets = [];
  assets.push(await render('feature-graphic-photos-1024x500.png', 1024, 500, `
    <text x="56" y="142" font-size="11" font-weight="500" letter-spacing="2.1" fill="${palette.muted}">FILM-INSPIRED CAMERA</text>
    <text x="52" y="205" font-size="49" font-weight="700" letter-spacing="-1.8">FeelCamera</text>
    <text x="56" y="253" font-size="27">Two films.</text>
    <text x="56" y="289" font-size="25">Your everyday moments.</text>
    <rect x="56" y="329" width="52" height="3" fill="${palette.amber}"/>
    <text x="56" y="366" font-size="12" font-weight="500" letter-spacing="2.4" fill="${palette.muted}">WARM / DUSK</text>
    ${image(warm, 450, 70, 240, 320)}
    ${image(dusk, 714, 70, 240, 320)}
    <text x="570" y="418" text-anchor="middle" font-size="13" font-weight="600" letter-spacing="2.5">WARM</text>
    <text x="834" y="418" text-anchor="middle" font-size="13" font-weight="600" letter-spacing="2.5">DUSK</text>
  `));

  const samples = [
    { film: 'WARM', buffer: warm, description: 'A warmer take on everyday moments.', filename: 'photo-sample-warm-1080x1920.png' },
    { film: 'DUSK', buffer: dusk, description: 'A cooler take on everyday moments.', filename: 'photo-sample-dusk-1080x1920.png' },
  ];
  for (const sample of samples) {
    assets.push(await render(sample.filename, 1080, 1920, `
      <text x="90" y="138" font-size="29" font-weight="500" letter-spacing="-0.4">FeelCamera</text>
      <rect x="90" y="176" width="54" height="4" fill="${palette.amber}"/>
      <text x="84" y="292" font-size="91" font-weight="700" letter-spacing="-3">${sample.film}</text>
      <text x="90" y="349" font-size="31" fill="${palette.muted}">${sample.description}</text>
      ${image(sample.buffer, 90, 424, 900, 1200)}
      <text x="90" y="1750" font-size="26" fill="${palette.muted}">Film-inspired color, captured in a photo.</text>
      <text x="90" y="1812" font-size="18" letter-spacing="2.7" fill="${palette.muted}">PHOTO SAMPLE / ${sample.film}</text>
    `));
  }

  const manifest = { language: 'en-US', sources, processing: 'Whole-photo embedding and proportional downsampling only. No color adjustments, retouching, or generated photo content.',
    usage: 'Feature graphic and supplemental photo examples; these do not depict the app UI.', assets };
  await fs.writeFile(path.join(directory, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log(JSON.stringify(manifest, null, 2));
}

main().catch(error => { console.error(error); process.exitCode = 1; });
