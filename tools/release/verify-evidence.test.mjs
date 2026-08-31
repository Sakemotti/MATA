import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import test from 'node:test';
import { createEvidenceManifest, verifyEvidence } from './verify-evidence.mjs';

const commit = 'b'.repeat(40);
const certificate = 'a'.repeat(64);
const acceptFixtureBundle = () => [certificate];

function verifyFixture(root, options = {}) {
  return verifyEvidence({
    root,
    expectedCommit: commit,
    bundleSignatureVerifier: acceptFixtureBundle,
    ...options,
  });
}

function write(root, path, contents) {
  const destination = resolve(root, ...path.split('/'));
  mkdirSync(dirname(destination), { recursive: true });
  writeFileSync(destination, contents);
  return destination;
}

function hash(contents) {
  return createHash('sha256').update(contents).digest('hex');
}

function createFixture() {
  const root = mkdtempSync(join(tmpdir(), 'mata-release-evidence-'));
  const artifactDefinitions = [
    ['androidAppBundle', 'app/build/outputs/bundle/release/app-release.aab', 'signed-aab'],
    ['r8Mapping', 'app/build/outputs/mapping/release/mapping.txt', 'mapping'],
    [
      'openSourceLicenses',
      'app/build/generated/aboutLibraries/release/res/raw/aboutlibraries.json',
      '{"libraries":[]}',
    ],
    [
      'mergedManifest',
      'app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml',
      '<manifest package="com.mochisofts.mata"/>',
    ],
    ['cycloneDxSbom', 'app/build/outputs/sbom/release-sbom.cdx.json', '{"bomFormat":"CycloneDX"}'],
  ];
  const artifacts = artifactDefinitions.map(([type, path, contents]) => {
    write(root, path, contents);
    return { type, path, bytes: Buffer.byteLength(contents), sha256: hash(contents) };
  });
  const metadata = {
    schemaVersion: 3,
    publishable: true,
    purpose: 'test fixture',
    applicationId: 'com.mochisofts.mata',
    versionName: '1.0.0',
    versionCode: 1,
    gitCommit: commit,
    buildTimestamp: '2026-08-31T00:00:00.000Z',
    signing: { method: 'uploadKey', certificateSha256: [certificate] },
    artifacts,
  };
  write(
    root,
    'app/build/outputs/release-metadata/release-metadata.json',
    `${JSON.stringify(metadata, null, 2)}\n`,
  );

  const checkIds = [
    'build_configuration',
    'release_workflow',
    'git_identity',
    'release_branch',
    'clean_worktree',
    'legal_site',
    'play_store',
    'release_artifacts',
  ];
  const readiness = {
    schemaVersion: 1,
    mode: 'release',
    generatedAt: '2026-08-31T00:01:00.000Z',
    status: 'passed',
    checks: checkIds.map((id) => ({
      id,
      status: 'passed',
      detail: id === 'git_identity' ? `main @ ${commit}` : 'passed',
    })),
  };
  write(
    root,
    'app/build/outputs/release-metadata/release-readiness.json',
    `${JSON.stringify(readiness, null, 2)}\n`,
  );

  const legalFiles = new Map([
    ['.nojekyll', ''],
    ['index.html', '<!doctype html><title>MATA</title>'],
  ]);
  for (const [path, contents] of legalFiles) {
    write(root, `app/build/outputs/release-candidate/legal-site/${path}`, contents);
  }
  const checksumLines = [...legalFiles]
    .map(([path, contents]) => `${hash(contents)}  ${path}`)
    .join('\n');
  write(
    root,
    'app/build/outputs/release-candidate/legal-site/SHA256SUMS',
    `${checksumLines}\n`,
  );

  const storeFiles = new Map([
    ['title.txt', 'MATA\n'],
    ['short_description.txt', '短い説明\n'],
    ['full_description.txt', '詳細説明\n'],
    ['changelogs/1.txt', '初回リリース\n'],
    ['images/icon.png', 'png-fixture'],
  ]);
  for (const [path, contents] of storeFiles) {
    write(root, `fastlane/metadata/android/ja-JP/${path}`, contents);
  }
  const storeManifest = {
    locale: 'ja-JP',
    metadataRoot: 'metadata/android/ja-JP',
    title: 'MATA',
    assets: [
      {
        path: 'metadata/android/ja-JP/images/icon.png',
        kind: 'storeIcon',
        width: 512,
        height: 512,
        altText: 'MATA icon',
      },
    ],
  };
  write(root, 'fastlane/play-store-manifest.json', `${JSON.stringify(storeManifest, null, 2)}\n`);
  return root;
}

test('creates and verifies a complete Release evidence package', () => {
  const root = createFixture();
  try {
    const created = createEvidenceManifest({ root });
    const result = verifyFixture(root);
    assert.equal(created.manifest.gitCommit, commit);
    assert.equal(result.fileCount, created.manifest.files.length);
    assert.deepEqual(result.signingCertificateSha256, [certificate]);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects a modified build artifact', () => {
  const root = createFixture();
  try {
    createEvidenceManifest({ root });
    write(root, 'app/build/outputs/bundle/release/app-release.aab', 'modified-aab');
    assert.throws(
      () => verifyFixture(root),
      /androidAppBundle byte size does not match|androidAppBundle SHA-256 does not match/,
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects an unexpected file in a downloaded artifact', () => {
  const root = createFixture();
  try {
    createEvidenceManifest({ root });
    write(root, 'unexpected.txt', 'not declared');
    assert.throws(
      () => verifyFixture(root),
      /Unexpected file in downloaded evidence: unexpected.txt/,
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('requires the independently supplied commit and signing certificate', () => {
  const root = createFixture();
  try {
    createEvidenceManifest({ root });
    assert.throws(
      () => verifyFixture(root, { expectedCommit: 'c'.repeat(40) }),
      /commit does not match expected commit/,
    );
    assert.throws(
      () => verifyFixture(root, {
        expectedCertificateSha256: 'd'.repeat(64),
      }),
      /Signing certificate does not match expected SHA-256/,
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects traversal in an evidence manifest path', () => {
  const root = createFixture();
  try {
    const created = createEvidenceManifest({ root });
    const manifest = JSON.parse(readFileSync(created.absoluteOutput, 'utf8'));
    manifest.files[0].path = '../outside';
    write(root, created.outputPath, `${JSON.stringify(manifest, null, 2)}\n`);
    assert.throws(
      () => verifyFixture(root),
      /contains an unsafe segment/,
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
