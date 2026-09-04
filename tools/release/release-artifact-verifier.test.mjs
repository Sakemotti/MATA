import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, relative } from 'node:path';
import test from 'node:test';
import { inspectReleaseArtifacts } from './release-artifact-verifier.mjs';

const gitCommit = '1234567890abcdef1234567890abcdef12345678';
const buildTimestamp = '2026-09-04T00:00:00.000Z';

function sha256(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex');
}

function write(path, content) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, content, 'utf8');
}

function writeJson(path, value) {
  write(path, `${JSON.stringify(value, null, 2)}\n`);
}

function createFixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'mata-release-artifacts-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));

  const sbom = {
    bomFormat: 'CycloneDX',
    specVersion: '1.6',
    metadata: {
      timestamp: buildTimestamp,
      component: {
        type: 'application',
        group: 'com.mochisofts',
        name: 'MATA',
        version: '1.0.0',
      },
    },
    components: [
      {
        'bom-ref': 'pkg:maven/com.google.android.libraries.ads.mobile.sdk/ads-mobile-sdk@1',
        group: 'com.google.android.libraries.ads.mobile.sdk',
        name: 'ads-mobile-sdk',
      },
      {
        'bom-ref': 'pkg:maven/com.google.android.ump/user-messaging-platform@1',
        group: 'com.google.android.ump',
        name: 'user-messaging-platform',
      },
    ],
    dependencies: [{ ref: 'pkg:maven/com.mochisofts/MATA@1.0.0', dependsOn: [] }],
  };

  const files = {
    androidAppBundle: join(root, 'artifacts', 'app-release.aab'),
    r8Mapping: join(root, 'artifacts', 'mapping.txt'),
    openSourceLicenses: join(root, 'artifacts', 'aboutlibraries.json'),
    mergedManifest: join(root, 'artifacts', 'AndroidManifest.xml'),
    cycloneDxSbom: join(root, 'artifacts', 'release-sbom.cdx.json'),
  };
  write(files.androidAppBundle, 'unsigned app bundle fixture');
  write(files.r8Mapping, 'mapping fixture');
  write(files.openSourceLicenses, '{"libraries":[]}\n');
  write(files.mergedManifest, '<manifest package="com.mochisofts.mata"/>\n');
  writeJson(files.cycloneDxSbom, sbom);

  const metadata = {
    schemaVersion: 3,
    publishable: false,
    applicationId: 'com.mochisofts.mata',
    versionName: '1.0.0',
    versionCode: 1,
    gitCommit,
    buildTimestamp,
    signing: { method: 'none', certificateSha256: [] },
    artifacts: Object.entries(files).map(([type, path]) => ({
      type,
      path: relative(root, path).replaceAll('\\', '/'),
      bytes: readFileSync(path).length,
      sha256: sha256(path),
    })),
  };
  const metadataPath = join(root, 'release-metadata.json');
  writeJson(metadataPath, metadata);

  const inspect = () => inspectReleaseArtifacts({
    metadataPath,
    repositoryRoot: root,
    expectedGitCommit: gitCommit,
  });
  const saveMetadata = () => writeJson(metadataPath, metadata);
  const refreshSbomDescriptor = () => {
    const artifact = metadata.artifacts.find(({ type }) => type === 'cycloneDxSbom');
    artifact.bytes = readFileSync(files.cycloneDxSbom).length;
    artifact.sha256 = sha256(files.cycloneDxSbom);
    saveMetadata();
  };

  return { files, inspect, metadata, refreshSbomDescriptor, saveMetadata, sbom };
}

function assertProblem(result, expected) {
  assert.ok(
    result.problems.some((problem) => problem.includes(expected)),
    `Expected a problem containing "${expected}", got: ${result.problems.join('; ')}`,
  );
}

test('accepts an intact release artifact set', (t) => {
  const fixture = createFixture(t);
  const result = fixture.inspect();

  assert.deepEqual(result.problems, []);
  assert.match(result.summary, /5 artifact hashes match/);
});

test('detects changed SBOM content, byte size, and SHA-256', (t) => {
  const fixture = createFixture(t);
  write(fixture.files.cycloneDxSbom, '{"tampered":true}\n');

  const result = fixture.inspect();

  assertProblem(result, 'cycloneDxSbom byte size changed');
  assertProblem(result, 'cycloneDxSbom SHA-256 changed');
  assertProblem(result, 'SBOM format must be CycloneDX');
});

test('rejects an SBOM path that escapes the repository', (t) => {
  const fixture = createFixture(t);
  const artifact = fixture.metadata.artifacts.find(({ type }) => type === 'cycloneDxSbom');
  artifact.path = '../release-sbom.cdx.json';
  fixture.saveMetadata();

  assertProblem(fixture.inspect(), 'cycloneDxSbom path escapes repository');
});

test('detects a missing SBOM file', (t) => {
  const fixture = createFixture(t);
  rmSync(fixture.files.cycloneDxSbom);

  assertProblem(fixture.inspect(), 'cycloneDxSbom file is missing');
});

test('detects independently changed byte size and SHA-256 metadata', (t) => {
  const fixture = createFixture(t);
  const artifact = fixture.metadata.artifacts.find(({ type }) => type === 'cycloneDxSbom');
  artifact.bytes += 1;
  artifact.sha256 = '0'.repeat(64);
  fixture.saveMetadata();

  const result = fixture.inspect();
  assertProblem(result, 'cycloneDxSbom byte size changed');
  assertProblem(result, 'cycloneDxSbom SHA-256 changed');
});

test('detects a missing required runtime component even with refreshed hashes', (t) => {
  const fixture = createFixture(t);
  fixture.sbom.components = fixture.sbom.components.filter(
    ({ name }) => name !== 'ads-mobile-sdk',
  );
  writeJson(fixture.files.cycloneDxSbom, fixture.sbom);
  fixture.refreshSbomDescriptor();

  assertProblem(
    fixture.inspect(),
    'SBOM is missing required runtime component com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk',
  );
});

test('detects a missing dependency graph even with refreshed hashes', (t) => {
  const fixture = createFixture(t);
  fixture.sbom.dependencies = [];
  writeJson(fixture.files.cycloneDxSbom, fixture.sbom);
  fixture.refreshSbomDescriptor();

  assertProblem(fixture.inspect(), 'SBOM dependency graph must be a non-empty array');
});
