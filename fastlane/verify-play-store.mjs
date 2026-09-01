import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, extname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const fastlaneRoot = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(fastlaneRoot, '..');
const manifestPath = resolve(fastlaneRoot, 'play-store-manifest.json');
const canonicalPath = resolve(
  repositoryRoot,
  '.agents/non-functional-specs/release-specs/store-listing-copy-and-assets.md',
);
const releaseMode = process.argv.slice(2).includes('--release');
const failures = [];
const notices = [];

function fail(message) {
  failures.push(message);
}

function readUtf8(path) {
  const value = readFileSync(path, 'utf8');
  if (value.startsWith('\uFEFF')) {
    fail(`${relative(repositoryRoot, path)} contains a UTF-8 BOM.`);
  }
  return value.replace(/\r\n/g, '\n');
}

function extractSection(markdown, sectionNumber, nextSectionNumber) {
  const startPattern = new RegExp(`^## ${sectionNumber}\\.[^\\n]*$`, 'm');
  const startMatch = startPattern.exec(markdown);
  if (startMatch === null) {
    throw new Error(`Canonical section ${sectionNumber} was not found.`);
  }
  const start = startMatch.index + startMatch[0].length;
  const remainder = markdown.slice(start);
  const endPattern = new RegExp(`^## ${nextSectionNumber}\\.[^\\n]*$`, 'm');
  const endMatch = endPattern.exec(remainder);
  return endMatch === null ? remainder : remainder.slice(0, endMatch.index);
}

function extractBlockQuote(section) {
  const lines = section
    .split('\n')
    .filter((line) => line === '>' || line.startsWith('> '))
    .map((line) => (line === '>' ? '' : line.slice(2)));
  if (lines.length === 0) {
    throw new Error('Canonical block quote was not found.');
  }
  return lines.join('\n').trimEnd();
}

function codePointLength(value) {
  return [...value].length;
}

function verifyText(path, expected, maximumLength, label) {
  if (!existsSync(path)) {
    fail(`${label} is missing: ${relative(repositoryRoot, path)}`);
    return;
  }
  const actual = readUtf8(path).trimEnd();
  if (actual !== expected) {
    fail(`${label} is not synchronized with the canonical specification.`);
  }
  const length = codePointLength(actual);
  if (length > maximumLength) {
    fail(`${label} is ${length} characters; maximum is ${maximumLength}.`);
  }
  notices.push(`${label}: ${length}/${maximumLength} characters`);
}

function parsePng(path) {
  const bytes = readFileSync(path);
  const pngSignature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  if (bytes.length < 33 || !bytes.subarray(0, 8).equals(pngSignature)) {
    throw new Error('not a valid PNG file');
  }
  if (bytes.toString('ascii', 12, 16) !== 'IHDR') {
    throw new Error('PNG does not start with an IHDR chunk');
  }
  return {
    bytes: bytes.length,
    width: bytes.readUInt32BE(16),
    height: bytes.readUInt32BE(20),
    bitDepth: bytes[24],
    colorType: bytes[25],
  };
}

function listFiles(path) {
  if (!existsSync(path)) {
    return [];
  }
  const files = [];
  for (const entry of readdirSync(path)) {
    const entryPath = resolve(path, entry);
    if (statSync(entryPath).isDirectory()) {
      files.push(...listFiles(entryPath));
    } else {
      files.push(entryPath);
    }
  }
  return files;
}

if (!existsSync(manifestPath)) {
  throw new Error('play-store-manifest.json is missing.');
}
if (!existsSync(canonicalPath)) {
  throw new Error('Canonical store-listing specification is missing.');
}

const manifest = JSON.parse(readUtf8(manifestPath));
const canonical = readUtf8(canonicalPath);
const metadataRoot = resolve(fastlaneRoot, manifest.metadataRoot);
const titleMatch = /^\| アプリ名 \| ([^|]+) \|$/m.exec(canonical);
if (titleMatch === null) {
  throw new Error('Canonical app title was not found.');
}

verifyText(resolve(metadataRoot, 'title.txt'), titleMatch[1].trim(), 30, 'Title');
verifyText(
  resolve(metadataRoot, 'short_description.txt'),
  extractBlockQuote(extractSection(canonical, 2, 3)),
  80,
  'Short description',
);
verifyText(
  resolve(metadataRoot, 'full_description.txt'),
  extractBlockQuote(extractSection(canonical, 3, 4)),
  4000,
  'Full description',
);
verifyText(
  resolve(metadataRoot, 'changelogs/1.txt'),
  extractBlockQuote(extractSection(canonical, 4, 5)),
  500,
  'Release notes',
);

if (manifest.locale !== 'ja-JP') {
  fail(`Unsupported locale in manifest: ${manifest.locale}`);
}
if (manifest.title !== titleMatch[1].trim()) {
  fail('Manifest title is not synchronized with the canonical specification.');
}

const expectedKindCounts = new Map([
  ['storeIcon', 1],
  ['featureGraphic', 1],
  ['phoneScreenshot', 6],
  ['tabletScreenshot', 8],
]);
const actualKindCounts = new Map();
const expectedPaths = new Set();

for (const asset of manifest.assets) {
  actualKindCounts.set(asset.kind, (actualKindCounts.get(asset.kind) ?? 0) + 1);
  if (expectedPaths.has(asset.path)) {
    fail(`Duplicate asset path in manifest: ${asset.path}`);
  }
  expectedPaths.add(asset.path);
  if (typeof asset.altText !== 'string' || asset.altText.trim() === '') {
    fail(`Asset has no alternative text: ${asset.path}`);
  }

  const assetPath = resolve(fastlaneRoot, asset.path);
  if (assetPath !== fastlaneRoot && !assetPath.startsWith(`${fastlaneRoot}${sep}`)) {
    fail(`Asset path escapes fastlane directory: ${asset.path}`);
    continue;
  }
  if (!existsSync(assetPath)) {
    const message = `Missing ${asset.kind}: ${asset.path}`;
    if (releaseMode) {
      fail(message);
    } else {
      notices.push(message);
    }
    continue;
  }
  if (extname(assetPath).toLowerCase() !== '.png') {
    fail(`Asset must be PNG: ${asset.path}`);
    continue;
  }

  try {
    const image = parsePng(assetPath);
    if (image.width !== asset.width || image.height !== asset.height) {
      fail(
        `${asset.path} is ${image.width}x${image.height}; expected ${asset.width}x${asset.height}.`,
      );
    }
    if (image.bitDepth !== 8) {
      fail(`${asset.path} uses ${image.bitDepth}-bit channels; expected 8-bit channels.`);
    }
    if (asset.kind === 'storeIcon') {
      if (image.colorType !== 6) {
        fail(`${asset.path} must be a 32-bit RGBA PNG.`);
      }
      if (image.bytes > 1024 * 1024) {
        fail(`${asset.path} exceeds 1,024KB.`);
      }
    } else if (asset.kind === 'featureGraphic') {
      if (image.colorType !== 2) {
        fail(`${asset.path} must be a 24-bit RGB PNG without alpha.`);
      }
    } else if (![2, 6].includes(image.colorType)) {
      fail(`${asset.path} must be an RGB or RGBA PNG.`);
    }
  } catch (error) {
    fail(`${asset.path}: ${error.message}`);
  }
}

for (const [kind, expectedCount] of expectedKindCounts) {
  const actualCount = actualKindCounts.get(kind) ?? 0;
  if (actualCount !== expectedCount) {
    fail(`Manifest has ${actualCount} ${kind} entries; expected ${expectedCount}.`);
  }
}

const imageRoot = resolve(metadataRoot, 'images');
for (const file of listFiles(imageRoot)) {
  const pathFromFastlane = relative(fastlaneRoot, file).replaceAll('\\', '/');
  if (!expectedPaths.has(pathFromFastlane)) {
    fail(`Unexpected image is not declared in manifest: ${pathFromFastlane}`);
  }
}

for (const notice of notices) {
  console.log(`- ${notice}`);
}

if (failures.length > 0) {
  console.error(`Play Store verification failed:\n- ${failures.join('\n- ')}`);
  process.exitCode = 1;
} else {
  const mode = releaseMode ? 'release' : 'draft';
  console.log(`Play Store ${mode} verification passed.`);
}
