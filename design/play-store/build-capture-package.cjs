const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const sharp = require(process.env.SHARP_MODULE || 'sharp');

const root = __dirname;
const raw = path.resolve(process.argv[2] || path.join(root, '../../build/reports/newfeel-emulator-check/store-20261009/screenshots'));
const groups = ['phone', 'tablet-7', 'tablet-10'];
const screens = ['01-warm', '02-dusk', '03-grid', '04-settings'];
const screenshotAssets = [];

async function describe(relative) {
  const file = await fs.readFile(path.join(root, relative));
  const meta = await sharp(file).metadata();
  return { file: relative, width: meta.width, height: meta.height, hasAlpha: meta.hasAlpha,
    bytes: file.length, sha256: crypto.createHash('sha256').update(file).digest('hex') };
}

async function main() {
  const gradle = await fs.readFile(path.resolve(root, '../../app/build.gradle.kts'), 'utf8');
  const packageName = gradle.match(/applicationId\s*=\s*"([^"]+)"/)?.[1];
  const versionName = gradle.match(/versionName\s*=\s*"([^"]+)"/)?.[1];
  const versionCode = Number(gradle.match(/versionCode\s*=\s*(\d+)/)?.[1]);
  if (!packageName || !versionName || !Number.isInteger(versionCode)) {
    throw new Error('Cannot read the Play registration target from app/build.gradle.kts');
  }
  for (const group of groups) {
    await fs.mkdir(path.join(root, 'en-US', group), { recursive: true });
    for (const screen of screens) {
      const relative = `en-US/${group}/${screen}.png`;
      // Preserve every captured UI pixel; only remove the unused alpha channel.
      await sharp(path.join(raw, `${group}-${screen}.png`)).removeAlpha().png().toFile(path.join(root, relative));
      const item = await describe(relative);
      if (item.hasAlpha || item.width < 1080 || item.height > 3840 || item.height / item.width > 2) {
        throw new Error(`Unexpected upload format: ${JSON.stringify(item)}`);
      }
      screenshotAssets.push(item);
    }
  }
  const banner = 'en-US/feature-graphic-photos-1024x500.png';
  const assets = [await describe('app-icon-512.png'), await describe(banner), ...screenshotAssets];
  const manifest = {
    language: 'en-US', created: '2026-10-09', screenshots_status: 'captured from app 1.0.3 (4); current registration target is recorded separately',
    app: { package: 'com.newfeelcamera', versionName: '1.0.3', versionCode: 4,
      build: 'debug, firebaseDebugCollectionEnabled=true; Analytics and Crashlytics consent off' },
    target_app: { package: packageName, versionName, versionCode,
      note: 'New Play account registration target; app and capture retain the original screenshot provenance.' },
    capture: { api: 36, orientation: 'portrait',
      phone: '1080x1920, 480 dpi', tablet7: '1440x2560, 320 dpi', tablet10: '1440x2560, 240 dpi',
      source: 'Android emulator virtual scene camera, with photo-samples/sources/warm.png as the wall poster',
      position: [-1.4, 0.32, 4.3], rotationDegrees: [0, 210, 0],
      note: 'Actual running app UI. The supplied WARM photo was already filtered and is rephotographed through the virtual camera and current app film. These screenshots illustrate the UI; they are not raw-source filter comparisons or physical-device verification. No preview replacement, cropping, or UI compositing was performed.' },
    assets, photo_sources_manifest: 'photo-samples/manifest.json'
  };
  await fs.writeFile(path.join(root, 'capture-manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  await fs.writeFile(path.join(root, 'asset-manifest.json'), JSON.stringify(manifest, null, 2) + '\n');

  const cellWidth = 300, cellHeight = 600, header = 46;
  const tiles = [];
  for (let row = 0; row < groups.length; row++) {
    for (let col = 0; col < screens.length; col++) {
      const relative = `en-US/${groups[row]}/${screens[col]}.png`;
      const tile = await sharp(path.join(root, relative)).resize(276, 536, {fit:'inside'}).toBuffer();
      const m = await sharp(tile).metadata();
      tiles.push({input: tile, left: col * cellWidth + Math.floor((cellWidth - m.width)/2), top: row * cellHeight + header});
    }
  }
  const labels = groups.flatMap((group, row) => screens.map((screen, col) =>
    `<text x="${col*cellWidth+14}" y="${row*cellHeight+28}" font-family="sans-serif" font-size="17" fill="#f3f0e8">${group} · ${screen.slice(3)}</text>`)).join('');
  tiles.push({input:Buffer.from(`<svg width="1200" height="1800" xmlns="http://www.w3.org/2000/svg">${labels}</svg>`),left:0,top:0});
  await sharp({create:{width:1200,height:1800,channels:3,background:'#242321'}}).composite(tiles).png().toFile(path.join(root,'capture-overview.png'));
  const files = ['app-icon-512.png', banner, 'en-US/listing.txt', 'UPLOAD-GUIDE.txt', 'capture-manifest.json', 'capture-overview.png', ...screenshotAssets.map(a=>a.file)];
  const zip = 'feelcamera-play-store-complete-en.zip';
  await fs.rm(path.join(root,zip),{force:true});
  execFileSync('/usr/bin/zip',['-q',zip,...files],{cwd:root});
  console.log(JSON.stringify({screenshots:screenshotAssets.length, package:zip, overview:'capture-overview.png', assets},null,2));
}
main().catch(error=>{console.error(error);process.exitCode=1;});
