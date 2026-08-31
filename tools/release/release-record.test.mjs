import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import test from 'node:test';
import { createReleaseRecord, validateReleaseRecord } from './release-record.mjs';

const commit = 'b'.repeat(40);
const certificate = 'a'.repeat(64);
const artifactDigest = 'c'.repeat(64);

function writeJson(root, path, value) {
  const destination = resolve(root, ...path.split('/'));
  mkdirSync(dirname(destination), { recursive: true });
  writeFileSync(destination, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function createFixture() {
  const root = mkdtempSync(join(tmpdir(), 'mata-release-record-'));
  const metadata = {
    schemaVersion: 3,
    publishable: true,
    applicationId: 'com.mochisofts.mata',
    versionName: '1.0.0',
    versionCode: 1,
    gitCommit: commit,
    buildTimestamp: '2026-08-31T00:00:00.000Z',
    signing: {
      method: 'uploadKey',
      certificateSha256: [certificate],
    },
  };
  const evidence = {
    schemaVersion: 1,
    generatedAt: '2026-08-31T00:01:00.000Z',
    applicationId: metadata.applicationId,
    versionName: metadata.versionName,
    versionCode: metadata.versionCode,
    gitCommit: metadata.gitCommit,
    buildTimestamp: metadata.buildTimestamp,
    signingCertificateSha256: [certificate],
    files: [{ path: 'fixture', bytes: 1, sha256: 'd'.repeat(64) }],
  };
  writeJson(root, 'app/build/outputs/release-metadata/release-metadata.json', metadata);
  writeJson(root, 'app/build/outputs/release-candidate/evidence-manifest.json', evidence);
  const environment = {
    GITHUB_REPOSITORY: 'Sakemotti/MATA',
    GITHUB_RUN_ID: '123456',
    GITHUB_RUN_ATTEMPT: '1',
    MATA_RELEASE_EVIDENCE_ARTIFACT_ID: '987654',
    MATA_RELEASE_EVIDENCE_ARTIFACT_URL:
      'https://github.com/Sakemotti/MATA/actions/runs/123456/artifacts/987654',
    MATA_RELEASE_EVIDENCE_ARTIFACT_SHA256: artifactDigest,
  };
  return { root, environment };
}

function passed(actor = 'tester') {
  return {
    status: 'passed',
    verifiedAt: '2026-09-01T00:00:00.000Z',
    actor,
  };
}

function approved(actor) {
  return {
    status: 'approved',
    verifiedAt: '2026-09-01T00:00:00.000Z',
    actor,
  };
}

function makeProductionRecord(candidate) {
  const record = structuredClone(candidate);
  record.recordState = 'published';
  record.sourceTag = 'v1.0.0';
  for (const name of [
    'downloadedEvidence',
    'p0p1',
    'preLaunchReport',
    'internalTesting',
    'closedTesting',
  ]) {
    record.verification[name] = passed();
  }
  record.legalPublication = passed('release-owner');
  record.approvals.technical = approved('technical-reviewer');
  record.approvals.release = approved('release-owner');
  record.publication = {
    track: 'production',
    status: 'completed',
    googlePlayReleaseId: 'play-release-1',
    submittedAt: '2026-09-01T01:00:00.000Z',
    publishedAt: '2026-09-02T01:00:00.000Z',
    rolloutPercent: 100,
    events: [
      {
        track: 'internal',
        status: 'completed',
        googlePlayReleaseId: 'play-internal-1',
        occurredAt: '2026-09-01T02:00:00.000Z',
        rolloutPercent: 100,
      },
      {
        track: 'closed',
        status: 'completed',
        googlePlayReleaseId: 'play-closed-1',
        occurredAt: '2026-09-01T12:00:00.000Z',
        rolloutPercent: 100,
      },
      {
        track: 'production',
        status: 'completed',
        googlePlayReleaseId: 'play-release-1',
        occurredAt: '2026-09-02T01:00:00.000Z',
        rolloutPercent: 100,
      },
    ],
  };
  return record;
}

test('creates a candidate record from Release evidence and Actions identity', () => {
  const fixture = createFixture();
  try {
    const result = createReleaseRecord({
      root: fixture.root,
      environment: fixture.environment,
      now: new Date('2026-08-31T00:02:00.000Z'),
    });
    assert.equal(result.record.recordState, 'candidate');
    assert.equal(result.record.gitCommit, commit);
    assert.equal(result.record.build.evidenceArtifact.sha256, artifactDigest);
    assert.equal(result.record.build.uploadCertificateSha256[0], certificate);
    assert.equal(validateReleaseRecord(result.record, 'candidate').stage, 'candidate');
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});

test('accepts a complete Production record', () => {
  const fixture = createFixture();
  try {
    const candidate = createReleaseRecord({ root: fixture.root, environment: fixture.environment }).record;
    const production = makeProductionRecord(candidate);
    const result = validateReleaseRecord(production, 'production');
    assert.equal(result.versionName, '1.0.0');
    assert.equal(result.versionCode, 1);
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});

test('rejects an artifact URL that does not match the Actions run', () => {
  const fixture = createFixture();
  try {
    fixture.environment.MATA_RELEASE_EVIDENCE_ARTIFACT_URL =
      'https://github.com/Sakemotti/MATA/actions/runs/999/artifacts/987654';
    assert.throws(
      () => createReleaseRecord({ root: fixture.root, environment: fixture.environment }),
      /artifact URL does not match/,
    );
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});

test('rejects a candidate that is presented as a completed stage', () => {
  const fixture = createFixture();
  try {
    const candidate = createReleaseRecord({ root: fixture.root, environment: fixture.environment }).record;
    assert.throws(
      () => validateReleaseRecord(candidate, 'production'),
      /recordState must be published/,
    );
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});

test('rejects secret-like data and forbidden operational identifiers', () => {
  const fixture = createFixture();
  try {
    const candidate = createReleaseRecord({ root: fixture.root, environment: fixture.environment }).record;
    candidate.notes.push('ghp_abcdefghijklmnopqrstuvwxyz1234567890');
    assert.throws(() => validateReleaseRecord(candidate, 'candidate'), /secret-like text/);

    const second = createReleaseRecord({ root: fixture.root, environment: fixture.environment }).record;
    second.incidents.push({ testerEmail: 'person@example.com' });
    assert.throws(() => validateReleaseRecord(second, 'candidate'), /forbidden field/);
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});

test('rejects evidence identity that differs from Release metadata', () => {
  const fixture = createFixture();
  try {
    writeJson(fixture.root, 'app/build/outputs/release-candidate/evidence-manifest.json', {
      schemaVersion: 1,
      generatedAt: '2026-08-31T00:01:00.000Z',
      applicationId: 'com.mochisofts.mata',
      versionName: '1.0.1',
      versionCode: 1,
      gitCommit: commit,
      buildTimestamp: '2026-08-31T00:00:00.000Z',
      signingCertificateSha256: [certificate],
      files: [{ path: 'fixture', bytes: 1, sha256: 'd'.repeat(64) }],
    });
    assert.throws(
      () => createReleaseRecord({ root: fixture.root, environment: fixture.environment }),
      /versionName does not match/,
    );
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});
