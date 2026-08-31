import { createHash } from 'node:crypto';
import {
  existsSync,
  lstatSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const defaultManifestPath = 'app/build/outputs/release-candidate/evidence-manifest.json';
const metadataPath = 'app/build/outputs/release-metadata/release-metadata.json';
const readinessPath = 'app/build/outputs/release-metadata/release-readiness.json';
const legalRootPath = 'app/build/outputs/release-candidate/legal-site';
const storeManifestPath = 'fastlane/play-store-manifest.json';
const storeMetadataPath = 'fastlane/metadata/android/ja-JP';
const sha256Pattern = /^[0-9a-f]{64}$/;
const gitCommitPattern = /^[0-9a-f]{40}$/;

function fail(message) {
  throw new Error(message);
}

function normalizedRelativePath(value, label = 'path') {
  if (typeof value !== 'string' || value === '' || isAbsolute(value) || value.includes('\\')) {
    fail(`${label} must be a non-empty, forward-slash relative path: ${value}`);
  }
  const segments = value.split('/');
  if (segments.some((segment) => segment === '' || segment === '.' || segment === '..')) {
    fail(`${label} contains an unsafe segment: ${value}`);
  }
  return value;
}

function resolveInside(root, value, label = 'path') {
  const normalized = normalizedRelativePath(value, label);
  const path = resolve(root, ...normalized.split('/'));
  const fromRoot = relative(root, path);
  if (fromRoot === '' || fromRoot.startsWith(`..${sep}`) || fromRoot === '..' || isAbsolute(fromRoot)) {
    fail(`${label} escapes the evidence root: ${value}`);
  }
  return path;
}

function requireRegularFile(root, value, label = 'file') {
  const path = resolveInside(root, value, label);
  if (!existsSync(path)) fail(`${label} is missing: ${value}`);
  const status = lstatSync(path);
  if (status.isSymbolicLink() || !status.isFile()) fail(`${label} is not a regular file: ${value}`);
  return path;
}

function readJson(root, value, label) {
  const path = requireRegularFile(root, value, label);
  try {
    return JSON.parse(readFileSync(path, 'utf8'));
  } catch (error) {
    fail(`${label} is not valid JSON: ${error.message}`);
  }
}

function sha256(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex');
}

function commandFailure(result) {
  return `${result.stdout ?? ''}\n${result.stderr ?? ''}`
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .slice(-10)
    .join(' | ');
}

export function verifyBundleSignature({ bundlePath, expectedCertificates }) {
  const verification = spawnSync('jarsigner', ['-verify', '-certs', bundlePath], {
    encoding: 'utf8',
    windowsHide: true,
  });
  if (verification.error !== undefined) {
    fail(`jarsigner could not be started: ${verification.error.message}`);
  }
  if (verification.status !== 0) {
    fail(`AAB signature verification failed: ${commandFailure(verification)}`);
  }

  const certificate = spawnSync('keytool', ['-printcert', '-jarfile', bundlePath], {
    encoding: 'utf8',
    windowsHide: true,
  });
  if (certificate.error !== undefined) fail(`keytool could not be started: ${certificate.error.message}`);
  if (certificate.status !== 0) {
    fail(`AAB signer certificate could not be read: ${commandFailure(certificate)}`);
  }
  const fingerprints = new Set();
  const pattern = /SHA-?256:\s*((?:[0-9a-f]{2}:){31}[0-9a-f]{2})/gi;
  for (const match of certificate.stdout.matchAll(pattern)) {
    fingerprints.add(match[1].replaceAll(':', '').toLowerCase());
  }
  const actual = [...fingerprints].sort();
  if (actual.length === 0) fail('keytool returned no AAB signer certificate SHA-256.');
  if (JSON.stringify(actual) !== JSON.stringify([...expectedCertificates].sort())) {
    fail('AAB signer certificate does not match Release metadata.');
  }
  return actual;
}

function fileRecord(root, value) {
  const path = requireRegularFile(root, value, 'evidence file');
  return {
    path: value,
    bytes: statSync(path).size,
    sha256: sha256(path),
  };
}

function listFiles(root, directoryPath) {
  const directory = directoryPath === '.'
    ? root
    : resolveInside(root, directoryPath, 'evidence directory');
  if (!existsSync(directory)) fail(`Evidence directory is missing: ${directoryPath}`);
  const directoryStatus = lstatSync(directory);
  if (directoryStatus.isSymbolicLink() || !directoryStatus.isDirectory()) {
    fail(`Evidence directory is not a regular directory: ${directoryPath}`);
  }

  const files = [];
  function visit(path) {
    for (const entry of readdirSync(path, { withFileTypes: true })) {
      const entryPath = resolve(path, entry.name);
      const status = lstatSync(entryPath);
      if (status.isSymbolicLink()) fail(`Symbolic links are not allowed in evidence: ${entryPath}`);
      if (status.isDirectory()) {
        visit(entryPath);
      } else if (status.isFile()) {
        files.push(relative(root, entryPath).split(sep).join('/'));
      } else {
        fail(`Unsupported filesystem entry in evidence: ${entryPath}`);
      }
    }
  }
  visit(directory);
  return files.sort();
}

function validateReleaseMetadata(root) {
  const metadata = readJson(root, metadataPath, 'Release metadata');
  if (metadata.schemaVersion !== 3) fail('Release metadata schemaVersion must be 3.');
  if (metadata.publishable !== true) fail('Release metadata must mark the candidate as publishable.');
  if (metadata.applicationId !== 'com.mochisofts.mata') fail('Release applicationId is invalid.');
  if (!/^\d+\.\d+\.\d+$/.test(metadata.versionName)) fail('Release versionName is invalid.');
  if (!Number.isInteger(metadata.versionCode) || metadata.versionCode < 1) {
    fail('Release versionCode is invalid.');
  }
  if (!gitCommitPattern.test(metadata.gitCommit)) fail('Release gitCommit is invalid.');
  if (Number.isNaN(Date.parse(metadata.buildTimestamp))) fail('Release buildTimestamp is invalid.');
  if (metadata.signing?.method !== 'uploadKey') fail('Release candidate is not Upload Key signed.');
  const certificates = metadata.signing?.certificateSha256;
  if (
    !Array.isArray(certificates) ||
    certificates.length === 0 ||
    certificates.some((value) => typeof value !== 'string' || !sha256Pattern.test(value)) ||
    new Set(certificates).size !== certificates.length
  ) {
    fail('Release signing certificate SHA-256 list is invalid.');
  }

  const expectedTypes = new Set([
    'androidAppBundle',
    'r8Mapping',
    'openSourceLicenses',
    'mergedManifest',
    'cycloneDxSbom',
  ]);
  if (!Array.isArray(metadata.artifacts) || metadata.artifacts.length !== expectedTypes.size) {
    fail('Release metadata must contain exactly five artifacts.');
  }
  const paths = [];
  let bundlePath = null;
  const actualTypes = new Set();
  for (const artifact of metadata.artifacts) {
    if (!expectedTypes.has(artifact.type) || actualTypes.has(artifact.type)) {
      fail(`Release artifact type is missing, duplicated, or unsupported: ${artifact.type}`);
    }
    actualTypes.add(artifact.type);
    const value = normalizedRelativePath(artifact.path, `${artifact.type} path`);
    const path = requireRegularFile(root, value, artifact.type);
    if (!Number.isInteger(artifact.bytes) || artifact.bytes < 1 || statSync(path).size !== artifact.bytes) {
      fail(`${artifact.type} byte size does not match Release metadata.`);
    }
    if (typeof artifact.sha256 !== 'string' || !sha256Pattern.test(artifact.sha256)) {
      fail(`${artifact.type} SHA-256 is invalid.`);
    }
    if (sha256(path) !== artifact.sha256) fail(`${artifact.type} SHA-256 does not match.`);
    if (artifact.type === 'androidAppBundle') bundlePath = path;
    paths.push(value);
  }
  if (new Set(paths).size !== paths.length) fail('Release artifact paths must be unique.');
  return { metadata, paths, bundlePath };
}

function validateReadiness(root, expectedCommit) {
  const readiness = readJson(root, readinessPath, 'Release readiness report');
  if (readiness.schemaVersion !== 1 || readiness.mode !== 'release' || readiness.status !== 'passed') {
    fail('Release readiness report is not a passed release report.');
  }
  if (!Array.isArray(readiness.checks) || readiness.checks.some((check) => check.status !== 'passed')) {
    fail('Release readiness report contains a missing or failed check.');
  }
  const expectedChecks = [
    'build_configuration',
    'release_workflow',
    'git_identity',
    'release_branch',
    'clean_worktree',
    'legal_site',
    'play_store',
    'release_artifacts',
  ];
  const checks = new Map(readiness.checks.map((check) => [check.id, check]));
  if (checks.size !== readiness.checks.length) fail('Release readiness check IDs must be unique.');
  for (const id of expectedChecks) {
    if (checks.get(id)?.status !== 'passed') fail(`Release readiness check is missing: ${id}`);
  }
  const identity = checks.get('git_identity')?.detail;
  if (typeof identity !== 'string' || !identity.includes(expectedCommit)) {
    fail('Release readiness git identity does not match Release metadata.');
  }
}

function validateLegalSite(root) {
  const files = listFiles(root, legalRootPath);
  const checksumRelativePath = `${legalRootPath}/SHA256SUMS`;
  const checksumPath = requireRegularFile(root, checksumRelativePath, 'Legal-site checksums');
  const lines = readFileSync(checksumPath, 'utf8').replace(/\r\n/g, '\n').trimEnd().split('\n');
  const declared = new Map();
  for (const line of lines) {
    const match = /^([0-9a-f]{64})  (.+)$/.exec(line);
    if (match === null) fail(`Invalid legal-site checksum line: ${line}`);
    const value = normalizedRelativePath(match[2], 'legal-site checksum path');
    if (declared.has(value)) fail(`Duplicate legal-site checksum path: ${value}`);
    declared.set(value, match[1]);
  }
  const packaged = files
    .filter((value) => value !== checksumRelativePath)
    .map((value) => value.slice(`${legalRootPath}/`.length));
  if (packaged.length === 0 || declared.size !== packaged.length) {
    fail('Legal-site SHA256SUMS does not cover every packaged file exactly once.');
  }
  for (const value of packaged) {
    if (!declared.has(value)) fail(`Legal-site checksum is missing: ${value}`);
    const path = requireRegularFile(root, `${legalRootPath}/${value}`, 'legal-site file');
    if (sha256(path) !== declared.get(value)) fail(`Legal-site SHA-256 does not match: ${value}`);
  }
  return files;
}

function validateStorePackage(root) {
  const manifest = readJson(root, storeManifestPath, 'Play Store manifest');
  if (manifest.locale !== 'ja-JP' || manifest.metadataRoot !== 'metadata/android/ja-JP') {
    fail('Play Store manifest locale or metadataRoot is invalid.');
  }
  if (!Array.isArray(manifest.assets) || manifest.assets.length === 0) {
    fail('Play Store manifest contains no assets.');
  }
  const requiredFiles = new Set([
    `${storeMetadataPath}/title.txt`,
    `${storeMetadataPath}/short_description.txt`,
    `${storeMetadataPath}/full_description.txt`,
    `${storeMetadataPath}/changelogs/1.txt`,
  ]);
  const assetPaths = new Set();
  for (const asset of manifest.assets) {
    const value = normalizedRelativePath(asset.path, 'Play Store asset path');
    if (!value.startsWith('metadata/android/ja-JP/images/')) {
      fail(`Play Store asset is outside the Japanese image directory: ${value}`);
    }
    if (assetPaths.has(value)) fail(`Duplicate Play Store asset path: ${value}`);
    assetPaths.add(value);
    requiredFiles.add(`fastlane/${value}`);
  }
  const files = listFiles(root, storeMetadataPath);
  if (new Set(files).size !== files.length || files.length !== requiredFiles.size) {
    fail('Play Store metadata contains a missing, duplicated, or undeclared file.');
  }
  for (const value of files) {
    if (!requiredFiles.has(value)) fail(`Undeclared Play Store metadata file: ${value}`);
  }
  for (const value of requiredFiles) requireRegularFile(root, value, 'Play Store evidence');
  return [storeManifestPath, ...files];
}

function collectEvidence(root) {
  const absoluteRoot = resolve(root);
  if (!existsSync(absoluteRoot) || !lstatSync(absoluteRoot).isDirectory()) {
    fail(`Evidence root is not a directory: ${absoluteRoot}`);
  }
  const { metadata, paths: artifactPaths, bundlePath } = validateReleaseMetadata(absoluteRoot);
  validateReadiness(absoluteRoot, metadata.gitCommit);
  const legalFiles = validateLegalSite(absoluteRoot);
  const storeFiles = validateStorePackage(absoluteRoot);
  const paths = [metadataPath, readinessPath, ...artifactPaths, ...legalFiles, ...storeFiles].sort();
  if (new Set(paths).size !== paths.length) fail('Evidence paths must be unique.');
  return { root: absoluteRoot, metadata, paths, bundlePath };
}

export function createEvidenceManifest({ root = repositoryRoot, output = defaultManifestPath } = {}) {
  const evidence = collectEvidence(root);
  const outputPath = normalizedRelativePath(output, 'evidence manifest output');
  if (evidence.paths.includes(outputPath)) fail('Evidence manifest output overlaps an evidence input.');
  const manifest = {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    applicationId: evidence.metadata.applicationId,
    versionName: evidence.metadata.versionName,
    versionCode: evidence.metadata.versionCode,
    gitCommit: evidence.metadata.gitCommit,
    buildTimestamp: evidence.metadata.buildTimestamp,
    signingCertificateSha256: [...evidence.metadata.signing.certificateSha256].sort(),
    files: evidence.paths.map((value) => fileRecord(evidence.root, value)),
  };
  const absoluteOutput = resolveInside(evidence.root, outputPath, 'evidence manifest output');
  if (existsSync(absoluteOutput) && lstatSync(absoluteOutput).isSymbolicLink()) {
    fail('Evidence manifest output must not be a symbolic link.');
  }
  mkdirSync(dirname(absoluteOutput), { recursive: true });
  writeFileSync(absoluteOutput, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  return { manifest, outputPath, absoluteOutput };
}

export function verifyEvidence({
  root,
  manifestPath = defaultManifestPath,
  expectedCommit,
  expectedCertificateSha256,
  rejectUnexpectedFiles = true,
  bundleSignatureVerifier = verifyBundleSignature,
}) {
  const absoluteRoot = resolve(root);
  const trustedCommit = typeof expectedCommit === 'string' ? expectedCommit.toLowerCase() : '';
  if (!gitCommitPattern.test(trustedCommit)) fail('Expected commit must be a full 40-character SHA-1.');
  const evidence = collectEvidence(absoluteRoot);
  if (evidence.metadata.gitCommit !== trustedCommit) fail('Release candidate commit does not match expected commit.');

  const manifest = readJson(absoluteRoot, manifestPath, 'Evidence manifest');
  if (manifest.schemaVersion !== 1) fail('Evidence manifest schemaVersion must be 1.');
  const identityFields = ['applicationId', 'versionName', 'versionCode', 'gitCommit', 'buildTimestamp'];
  for (const field of identityFields) {
    if (manifest[field] !== evidence.metadata[field]) fail(`Evidence manifest ${field} does not match.`);
  }
  const certificates = [...evidence.metadata.signing.certificateSha256].sort();
  if (
    !Array.isArray(manifest.signingCertificateSha256) ||
    JSON.stringify(manifest.signingCertificateSha256) !== JSON.stringify(certificates)
  ) {
    fail('Evidence manifest signing certificate list does not match.');
  }
  if (expectedCertificateSha256 !== undefined) {
    const trustedCertificate = expectedCertificateSha256.toLowerCase();
    if (!sha256Pattern.test(trustedCertificate)) {
      fail('Expected signing certificate must be a 64-character SHA-256.');
    }
    if (!certificates.includes(trustedCertificate)) fail('Signing certificate does not match expected SHA-256.');
  }
  bundleSignatureVerifier({
    bundlePath: evidence.bundlePath,
    expectedCertificates: certificates,
  });

  if (!Array.isArray(manifest.files) || manifest.files.length !== evidence.paths.length) {
    fail('Evidence manifest file count does not match the required evidence.');
  }
  const records = new Map();
  for (const record of manifest.files) {
    const value = normalizedRelativePath(record.path, 'evidence record path');
    if (records.has(value)) fail(`Duplicate evidence record: ${value}`);
    records.set(value, record);
  }
  for (const value of evidence.paths) {
    const record = records.get(value);
    if (record === undefined) fail(`Evidence manifest is missing: ${value}`);
    const actual = fileRecord(absoluteRoot, value);
    if (record.bytes !== actual.bytes || record.sha256 !== actual.sha256) {
      fail(`Evidence manifest hash or byte size does not match: ${value}`);
    }
  }
  if (rejectUnexpectedFiles) {
    const actualFiles = listFiles(absoluteRoot, '.');
    const expectedFiles = new Set([...evidence.paths, normalizedRelativePath(manifestPath)]);
    for (const value of actualFiles) {
      if (!expectedFiles.has(value)) fail(`Unexpected file in downloaded evidence: ${value}`);
    }
    if (actualFiles.length !== expectedFiles.size) fail('Downloaded evidence file set is incomplete.');
  }
  return {
    versionName: evidence.metadata.versionName,
    versionCode: evidence.metadata.versionCode,
    gitCommit: trustedCommit,
    signingCertificateSha256: certificates,
    fileCount: evidence.paths.length,
  };
}

function parseArguments(argv) {
  const [command, ...rest] = argv;
  if (!['create', 'verify'].includes(command)) fail('First argument must be create or verify.');
  const options = {};
  for (let index = 0; index < rest.length; index += 2) {
    const name = rest[index];
    const value = rest[index + 1];
    if (value === undefined || !name.startsWith('--')) fail(`Missing value for ${name}.`);
    if (Object.hasOwn(options, name)) fail(`Duplicate argument: ${name}`);
    options[name] = value;
  }
  const allowed = new Set([
    '--root',
    '--manifest',
    '--expected-commit',
    '--expected-certificate-sha256',
  ]);
  for (const name of Object.keys(options)) {
    if (!allowed.has(name)) fail(`Unknown argument: ${name}`);
  }
  return { command, options };
}

function usage() {
  return [
    'Usage:',
    '  node tools/release/verify-evidence.mjs create [--root <directory>] [--manifest <relative-path>]',
    '  node tools/release/verify-evidence.mjs verify --root <directory> --expected-commit <sha> [--manifest <relative-path>] [--expected-certificate-sha256 <sha256>]',
  ].join('\n');
}

const isMain = process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  try {
    const { command, options } = parseArguments(process.argv.slice(2));
    const root = resolve(options['--root'] ?? repositoryRoot);
    const manifestPath = options['--manifest'] ?? defaultManifestPath;
    if (command === 'create') {
      const result = createEvidenceManifest({ root, output: manifestPath });
      console.log(`Release evidence manifest created: ${result.outputPath}`);
      console.log(`Files: ${result.manifest.files.length}; commit: ${result.manifest.gitCommit}`);
    } else {
      if (options['--root'] === undefined || options['--expected-commit'] === undefined) fail(usage());
      const result = verifyEvidence({
        root,
        manifestPath,
        expectedCommit: options['--expected-commit'],
        expectedCertificateSha256: options['--expected-certificate-sha256'],
      });
      console.log(
        `Release evidence verified: ${result.versionName} (${result.versionCode}) @ ${result.gitCommit}`,
      );
      console.log(`Files: ${result.fileCount}; signing certificates: ${result.signingCertificateSha256.join(', ')}`);
    }
  } catch (error) {
    console.error(`Release evidence verification failed: ${error.message}`);
    process.exitCode = 1;
  }
}
