const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { execFile } = require('node:child_process');
const { promisify } = require('node:util');

const run = promisify(execFile);
const hash = buffer => crypto.createHash('sha256').update(buffer).digest('hex');

async function snapshot(directory) {
  const files = {};
  async function visit(relative) {
    for (const entry of await fs.readdir(path.join(directory, relative), { withFileTypes: true })) {
      const file = path.join(relative, entry.name);
      if (entry.isDirectory()) await visit(file);
      else files[file] = hash(await fs.readFile(path.join(directory, file)));
    }
  }
  await visit('');
  return files;
}

test('regeneration preserves README images and provenance and refreshes a separate ZIP', { timeout: 60000 }, async t => {
  const project = await fs.realpath(await fs.mkdtemp(path.join(os.tmpdir(), 'feelcamera-capture-package-')));
  t.after(() => fs.rm(project, { recursive: true, force: true }));
  const source = path.join(project, 'design/play-store');
  const raw = path.join(project, 'raw');
  const output = path.join(project, 'build/play-store-capture-package');
  await fs.mkdir(path.join(project, 'app'), { recursive: true });
  await fs.writeFile(path.join(project, 'app/build.gradle.kts'), `android {
    defaultConfig {
      applicationId = "com.sseo.feelcamera"
      versionName = "1.0.0"
      versionCode = 1
    }
  }`);
  for (const relative of [
    'build-capture-package.cjs', 'README.md', 'UPLOAD-GUIDE.txt',
    'capture-manifest.json', 'asset-manifest.json', 'photo-samples/manifest.json', 'app-icon-512.png',
    'en-US/feature-graphic-photos-1024x500.png', 'en-US/listing.txt',
    'en-US/phone/01-warm.png', 'en-US/phone/02-dusk.png', 'en-US/phone/04-settings.png',
  ]) {
    await fs.mkdir(path.dirname(path.join(source, relative)), { recursive: true });
    await fs.copyFile(path.join(__dirname, relative), path.join(source, relative));
  }
  await fs.mkdir(raw);
  for (const group of ['phone', 'tablet-7', 'tablet-10']) {
    for (const screen of ['01-warm', '02-dusk', '03-grid', '04-settings']) {
      await fs.copyFile(path.join(__dirname, `en-US/${group}/${screen}.png`), path.join(raw, `${group}-${screen}.png`));
    }
  }
  const originals = await snapshot(source);
  let previousScreenshotHash;
  // Use different existing captures to ensure neither run reuses the README image.
  for (const input of ['03-grid', '02-dusk']) {
    await fs.copyFile(path.join(__dirname, `en-US/phone/${input}.png`), path.join(raw, 'phone-01-warm.png'));
    const { stdout } = await run(process.execPath, [path.join(source, 'build-capture-package.cjs'), raw]);
    const result = JSON.parse(stdout);
    assert.equal(result.output, output);
    assert.equal(result.screenshots, 12);
    assert.deepEqual(await snapshot(source), originals, 'curated images and provenance must remain byte-for-byte unchanged');

    const manifest = JSON.parse(await fs.readFile(path.join(output, 'capture-manifest.json'), 'utf8'));
    assert.equal(manifest.target_app.package, 'com.sseo.feelcamera');
    assert.equal(manifest.target_app.versionName, '1.0.0');
    assert.equal(manifest.target_app.versionCode, 1);
    assert.equal(manifest.app.package, 'com.newfeelcamera');
    assert.equal(manifest.app.versionName, '1.0.3');
    assert.deepEqual(JSON.parse(await fs.readFile(path.join(output, 'asset-manifest.json'), 'utf8')), manifest);
    for (const asset of manifest.assets) {
      const bytes = await fs.readFile(path.join(output, asset.file));
      assert.equal(hash(bytes), asset.sha256);
      assert.equal(bytes.length, asset.bytes);
    }
    const screenshot = await fs.readFile(path.join(output, 'en-US/phone/01-warm.png'));
    assert.notEqual(hash(screenshot), originals['en-US/phone/01-warm.png']);
    assert.notEqual(hash(screenshot), previousScreenshotHash);
    previousScreenshotHash = hash(screenshot);
    const zip = path.join(output, result.package);
    const { stdout: zippedScreenshot } = await run('/usr/bin/unzip', ['-p', zip, 'en-US/phone/01-warm.png'], { encoding: 'buffer', maxBuffer: 8 * 1024 * 1024 });
    assert.deepEqual(zippedScreenshot, screenshot);
    const { stdout: zippedManifest } = await run('/usr/bin/unzip', ['-p', zip, 'capture-manifest.json']);
    assert.deepEqual(JSON.parse(zippedManifest), manifest);
    const { stdout: zippedProvenance } = await run('/usr/bin/unzip', ['-p', zip, manifest.photo_sources_manifest]);
    assert.deepEqual(JSON.parse(zippedProvenance), JSON.parse(await fs.readFile(path.join(source, manifest.photo_sources_manifest), 'utf8')));
  }
});
