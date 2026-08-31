import { createHash } from 'node:crypto';
import { existsSync, lstatSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const releaseMetadataPath = 'app/build/outputs/release-metadata/release-metadata.json';
const evidenceManifestPath = 'app/build/outputs/release-candidate/evidence-manifest.json';
const defaultRecordPath = 'app/build/outputs/release-candidate/release-record-draft.json';
const sha256Pattern = /^[0-9a-f]{64}$/;
const commitPattern = /^[0-9a-f]{40}$/;
const versionNamePattern = /^\d+\.\d+\.\d+$/;
const stages = new Set(['candidate', 'internal', 'closed', 'production']);
const checkNames = [
  'automatedReleaseGate',
  'downloadedEvidence',
  'p0p1',
  'preLaunchReport',
  'internalTesting',
  'closedTesting',
];

function fail(message) {
  throw new Error(message);
}

function requireObject(value, label) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    fail(`${label} must be an object.`);
  }
  return value;
}

function requireString(value, label) {
  if (typeof value !== 'string' || value.trim() === '') fail(`${label} must be a non-empty string.`);
  return value;
}

function requirePositiveInteger(value, label) {
  if (!Number.isInteger(value) || value < 1) fail(`${label} must be a positive integer.`);
  return value;
}

function requireIsoTimestamp(value, label, { nullable = false } = {}) {
  if (nullable && value === null) return null;
  if (typeof value !== 'string' || Number.isNaN(Date.parse(value))) {
    fail(`${label} must be an ISO-8601 timestamp${nullable ? ' or null' : ''}.`);
  }
  return value;
}

function normalizedRelativePath(value, label) {
  if (typeof value !== 'string' || value === '' || isAbsolute(value) || value.includes('\\')) {
    fail(`${label} must be a forward-slash relative path.`);
  }
  if (value.split('/').some((segment) => segment === '' || segment === '.' || segment === '..')) {
    fail(`${label} contains an unsafe segment.`);
  }
  return value;
}

function resolveInside(root, value, label) {
  const normalized = normalizedRelativePath(value, label);
  const path = resolve(root, ...normalized.split('/'));
  const fromRoot = relative(root, path);
  if (fromRoot === '' || fromRoot === '..' || fromRoot.startsWith(`..${sep}`) || isAbsolute(fromRoot)) {
    fail(`${label} escapes the root directory.`);
  }
  return path;
}

function readJson(root, value, label) {
  const path = resolveInside(root, value, label);
  if (!existsSync(path) || lstatSync(path).isSymbolicLink() || !lstatSync(path).isFile()) {
    fail(`${label} is missing or is not a regular file: ${value}`);
  }
  try {
    return { path, value: JSON.parse(readFileSync(path, 'utf8')) };
  } catch (error) {
    fail(`${label} is not valid JSON: ${error.message}`);
  }
}

function sha256(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex');
}

function statusEntry(status = 'pending', verifiedAt = null, actor = null) {
  return { status, verifiedAt, actor };
}

function requiredEnvironment(environment, name) {
  const value = environment[name];
  if (typeof value !== 'string' || value.trim() === '') fail(`Required environment variable is missing: ${name}`);
  return value.trim();
}

function parseEnvironmentInteger(environment, name) {
  const value = requiredEnvironment(environment, name);
  if (!/^\d+$/.test(value)) fail(`${name} must be a positive integer.`);
  return requirePositiveInteger(Number(value), name);
}

function validateSourceEvidence(root) {
  const metadataResult = readJson(root, releaseMetadataPath, 'Release metadata');
  const evidenceResult = readJson(root, evidenceManifestPath, 'Evidence manifest');
  const metadata = requireObject(metadataResult.value, 'Release metadata');
  const evidence = requireObject(evidenceResult.value, 'Evidence manifest');
  if (
    metadata.schemaVersion !== 3 ||
    metadata.publishable !== true ||
    metadata.applicationId !== 'com.mochisofts.mata' ||
    !versionNamePattern.test(metadata.versionName) ||
    !Number.isInteger(metadata.versionCode) ||
    metadata.versionCode < 1 ||
    !commitPattern.test(metadata.gitCommit) ||
    Number.isNaN(Date.parse(metadata.buildTimestamp)) ||
    metadata.signing?.method !== 'uploadKey'
  ) {
    fail('Release metadata is not a publishable MATA Release candidate.');
  }
  const certificates = metadata.signing?.certificateSha256;
  if (
    !Array.isArray(certificates) ||
    certificates.length === 0 ||
    certificates.some((value) => typeof value !== 'string' || !sha256Pattern.test(value)) ||
    new Set(certificates).size !== certificates.length
  ) {
    fail('Release metadata signing certificates are invalid.');
  }
  for (const field of ['applicationId', 'versionName', 'versionCode', 'gitCommit', 'buildTimestamp']) {
    if (evidence[field] !== metadata[field]) fail(`Evidence manifest ${field} does not match Release metadata.`);
  }
  if (
    evidence.schemaVersion !== 1 ||
    !Array.isArray(evidence.files) ||
    evidence.files.length === 0 ||
    JSON.stringify(evidence.signingCertificateSha256) !== JSON.stringify([...certificates].sort())
  ) {
    fail('Evidence manifest identity or signing certificates are invalid.');
  }
  return { metadata, evidence, evidenceManifestSha256: sha256(evidenceResult.path) };
}

export function createReleaseRecord({
  root = repositoryRoot,
  output = defaultRecordPath,
  environment = process.env,
  now = new Date(),
} = {}) {
  const absoluteRoot = resolve(root);
  const { metadata, evidence, evidenceManifestSha256 } = validateSourceEvidence(absoluteRoot);
  const repository = requiredEnvironment(environment, 'GITHUB_REPOSITORY');
  if (!/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repository)) fail('GITHUB_REPOSITORY is invalid.');
  const runId = parseEnvironmentInteger(environment, 'GITHUB_RUN_ID');
  const runAttempt = parseEnvironmentInteger(environment, 'GITHUB_RUN_ATTEMPT');
  const artifactId = parseEnvironmentInteger(environment, 'MATA_RELEASE_EVIDENCE_ARTIFACT_ID');
  const artifactUrl = requiredEnvironment(environment, 'MATA_RELEASE_EVIDENCE_ARTIFACT_URL');
  const expectedUrl = `https://github.com/${repository}/actions/runs/${runId}/artifacts/${artifactId}`;
  if (artifactUrl !== expectedUrl) fail('Release evidence artifact URL does not match the run and artifact ID.');
  const artifactSha256 = requiredEnvironment(
    environment,
    'MATA_RELEASE_EVIDENCE_ARTIFACT_SHA256',
  ).toLowerCase();
  if (!sha256Pattern.test(artifactSha256)) fail('Release evidence artifact SHA-256 is invalid.');

  const generatedAt = now.toISOString();
  const record = {
    schemaVersion: 1,
    recordState: 'candidate',
    applicationId: metadata.applicationId,
    versionName: metadata.versionName,
    versionCode: metadata.versionCode,
    gitCommit: metadata.gitCommit,
    sourceTag: null,
    build: {
      builtAt: metadata.buildTimestamp,
      recordGeneratedAt: generatedAt,
      githubRepository: repository,
      githubWorkflow: 'Release candidate',
      githubRunId: runId,
      githubRunAttempt: runAttempt,
      evidenceArtifact: {
        id: artifactId,
        url: artifactUrl,
        sha256: artifactSha256,
      },
      evidenceManifestSha256,
      uploadCertificateSha256: [...metadata.signing.certificateSha256].sort(),
    },
    verification: {
      automatedReleaseGate: statusEntry('passed', evidence.generatedAt, 'github-actions'),
      downloadedEvidence: statusEntry(),
      p0p1: statusEntry(),
      preLaunchReport: statusEntry(),
      internalTesting: statusEntry(),
      closedTesting: statusEntry(),
    },
    legalPublication: statusEntry(),
    publication: {
      track: 'not_uploaded',
      status: 'pending',
      googlePlayReleaseId: null,
      submittedAt: null,
      publishedAt: null,
      rolloutPercent: 0,
      events: [],
    },
    approvals: {
      technical: statusEntry(),
      release: statusEntry(),
    },
    incidents: [],
    notes: [],
  };
  validateReleaseRecord(record, 'candidate');

  const outputPath = resolveInside(absoluteRoot, output, 'Release record output');
  if (existsSync(outputPath) && lstatSync(outputPath).isSymbolicLink()) {
    fail('Release record output must not be a symbolic link.');
  }
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, `${JSON.stringify(record, null, 2)}\n`, 'utf8');
  return { record, path: outputPath, relativePath: output };
}

function validateStatusEntry(
  value,
  label,
  { requiredStatus, allowedStatuses = ['pending', 'passed', 'failed'] } = {},
) {
  const entry = requireObject(value, label);
  const allowed = new Set(allowedStatuses);
  if (!allowed.has(entry.status)) fail(`${label}.status is invalid.`);
  if (requiredStatus !== undefined && entry.status !== requiredStatus) {
    fail(`${label}.status must be ${requiredStatus}.`);
  }
  requireIsoTimestamp(entry.verifiedAt, `${label}.verifiedAt`, { nullable: true });
  if (entry.actor !== null && (typeof entry.actor !== 'string' || !/^@?[A-Za-z0-9_.-]{1,100}$/.test(entry.actor))) {
    fail(`${label}.actor is invalid.`);
  }
  if (entry.status === 'pending') {
    if (entry.verifiedAt !== null || entry.actor !== null) fail(`${label} pending fields must be null.`);
  } else if (entry.verifiedAt === null || entry.actor === null) {
    fail(`${label} must record verifiedAt and actor.`);
  }
}

function rejectSecretLikeData(value, path = 'record') {
  if (Array.isArray(value)) {
    value.forEach((item, index) => rejectSecretLikeData(item, `${path}[${index}]`));
    return;
  }
  if (value !== null && typeof value === 'object') {
    for (const [key, item] of Object.entries(value)) {
      if (/(password|passphrase|private.?key|keystore|purchase.?token|order.?id|tester.?email)/i.test(key)) {
        fail(`Release record contains a forbidden field: ${path}.${key}`);
      }
      rejectSecretLikeData(item, `${path}.${key}`);
    }
    return;
  }
  if (
    typeof value === 'string' &&
    (/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/.test(value) ||
      /\bgh[pousr]_[A-Za-z0-9]{30,}\b/.test(value) ||
      /\bAIza[0-9A-Za-z_-]{35}\b/.test(value))
  ) {
    fail(`Release record contains secret-like text at ${path}.`);
  }
}

export function validateReleaseRecord(recordValue, stage = 'candidate') {
  if (!stages.has(stage)) fail(`Unsupported Release record stage: ${stage}`);
  const record = requireObject(recordValue, 'Release record');
  rejectSecretLikeData(record);
  if (record.schemaVersion !== 1) fail('Release record schemaVersion must be 1.');
  if (record.applicationId !== 'com.mochisofts.mata') fail('Release record applicationId is invalid.');
  if (!versionNamePattern.test(record.versionName)) fail('Release record versionName is invalid.');
  requirePositiveInteger(record.versionCode, 'Release record versionCode');
  if (!commitPattern.test(record.gitCommit)) fail('Release record gitCommit is invalid.');
  if (record.sourceTag !== null && record.sourceTag !== `v${record.versionName}`) {
    fail('Release record sourceTag must be null or v<versionName>.');
  }

  const build = requireObject(record.build, 'Release record build');
  requireIsoTimestamp(build.builtAt, 'build.builtAt');
  requireIsoTimestamp(build.recordGeneratedAt, 'build.recordGeneratedAt');
  const repository = requireString(build.githubRepository, 'build.githubRepository');
  if (!/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repository)) fail('build.githubRepository is invalid.');
  if (build.githubWorkflow !== 'Release candidate') fail('build.githubWorkflow is invalid.');
  requirePositiveInteger(build.githubRunId, 'build.githubRunId');
  requirePositiveInteger(build.githubRunAttempt, 'build.githubRunAttempt');
  const artifact = requireObject(build.evidenceArtifact, 'build.evidenceArtifact');
  requirePositiveInteger(artifact.id, 'build.evidenceArtifact.id');
  const expectedUrl = `https://github.com/${repository}/actions/runs/${build.githubRunId}/artifacts/${artifact.id}`;
  if (artifact.url !== expectedUrl) fail('build.evidenceArtifact.url is invalid.');
  if (!sha256Pattern.test(artifact.sha256)) fail('build.evidenceArtifact.sha256 is invalid.');
  if (!sha256Pattern.test(build.evidenceManifestSha256)) fail('build.evidenceManifestSha256 is invalid.');
  if (
    !Array.isArray(build.uploadCertificateSha256) ||
    build.uploadCertificateSha256.length === 0 ||
    build.uploadCertificateSha256.some((value) => !sha256Pattern.test(value)) ||
    new Set(build.uploadCertificateSha256).size !== build.uploadCertificateSha256.length
  ) {
    fail('build.uploadCertificateSha256 is invalid.');
  }

  const verification = requireObject(record.verification, 'Release record verification');
  for (const name of checkNames) {
    validateStatusEntry(verification[name], `verification.${name}`);
  }
  validateStatusEntry(verification.automatedReleaseGate, 'verification.automatedReleaseGate', {
    requiredStatus: 'passed',
  });
  validateStatusEntry(record.legalPublication, 'legalPublication');
  const approvals = requireObject(record.approvals, 'Release record approvals');
  validateStatusEntry(approvals.technical, 'approvals.technical', {
    allowedStatuses: ['pending', 'approved', 'rejected'],
  });
  validateStatusEntry(approvals.release, 'approvals.release', {
    allowedStatuses: ['pending', 'approved', 'rejected'],
  });

  const publication = requireObject(record.publication, 'Release record publication');
  if (!new Set(['not_uploaded', 'internal', 'closed', 'open', 'production']).has(publication.track)) {
    fail('publication.track is invalid.');
  }
  if (!new Set(['pending', 'active', 'halted', 'completed']).has(publication.status)) {
    fail('publication.status is invalid.');
  }
  if (publication.googlePlayReleaseId !== null) {
    requireString(publication.googlePlayReleaseId, 'publication.googlePlayReleaseId');
  }
  requireIsoTimestamp(publication.submittedAt, 'publication.submittedAt', { nullable: true });
  requireIsoTimestamp(publication.publishedAt, 'publication.publishedAt', { nullable: true });
  if (
    typeof publication.rolloutPercent !== 'number' ||
    !Number.isFinite(publication.rolloutPercent) ||
    publication.rolloutPercent < 0 ||
    publication.rolloutPercent > 100
  ) {
    fail('publication.rolloutPercent must be between 0 and 100.');
  }
  if (!Array.isArray(publication.events)) fail('publication.events must be an array.');
  for (const [index, eventValue] of publication.events.entries()) {
    const event = requireObject(eventValue, `publication.events[${index}]`);
    if (!new Set(['internal', 'closed', 'open', 'production']).has(event.track)) {
      fail(`publication.events[${index}].track is invalid.`);
    }
    if (!new Set(['submitted', 'active', 'halted', 'completed']).has(event.status)) {
      fail(`publication.events[${index}].status is invalid.`);
    }
    requireString(event.googlePlayReleaseId, `publication.events[${index}].googlePlayReleaseId`);
    requireIsoTimestamp(event.occurredAt, `publication.events[${index}].occurredAt`);
    if (
      typeof event.rolloutPercent !== 'number' ||
      !Number.isFinite(event.rolloutPercent) ||
      event.rolloutPercent < 0 ||
      event.rolloutPercent > 100
    ) {
      fail(`publication.events[${index}].rolloutPercent must be between 0 and 100.`);
    }
  }
  if (!Array.isArray(record.incidents) || !Array.isArray(record.notes)) {
    fail('Release record incidents and notes must be arrays.');
  }
  for (const [index, incidentValue] of record.incidents.entries()) {
    const incident = requireObject(incidentValue, `incidents[${index}]`);
    requireString(incident.id, `incidents[${index}].id`);
    if (!new Set(['S0', 'S1', 'S2', 'S3']).has(incident.severity)) {
      fail(`incidents[${index}].severity is invalid.`);
    }
    if (!new Set(['open', 'mitigated', 'resolved', 'accepted']).has(incident.status)) {
      fail(`incidents[${index}].status is invalid.`);
    }
    const summary = requireString(incident.summary, `incidents[${index}].summary`);
    if (summary.length > 500) fail(`incidents[${index}].summary exceeds 500 characters.`);
  }
  if (record.notes.some((note) => typeof note !== 'string' || note.trim() === '')) {
    fail('Release record notes must be non-empty strings.');
  }

  const requirements = {
    candidate: {
      recordState: 'candidate',
      passedChecks: [],
      track: 'not_uploaded',
      publicationStatus: 'pending',
      legal: null,
      technicalApproval: null,
      releaseApproval: null,
      requiredTracks: [],
    },
    internal: {
      recordState: 'internal_verified',
      passedChecks: ['downloadedEvidence', 'p0p1', 'preLaunchReport', 'internalTesting'],
      track: 'internal',
      publicationStatus: 'completed',
      legal: 'passed',
      technicalApproval: 'approved',
      releaseApproval: null,
      requiredTracks: ['internal'],
    },
    closed: {
      recordState: 'closed_verified',
      passedChecks: [
        'downloadedEvidence',
        'p0p1',
        'preLaunchReport',
        'internalTesting',
        'closedTesting',
      ],
      track: 'closed',
      publicationStatus: 'completed',
      legal: 'passed',
      technicalApproval: 'approved',
      releaseApproval: null,
      requiredTracks: ['internal', 'closed'],
    },
    production: {
      recordState: 'published',
      passedChecks: [
        'downloadedEvidence',
        'p0p1',
        'preLaunchReport',
        'internalTesting',
        'closedTesting',
      ],
      track: 'production',
      publicationStatus: 'completed',
      legal: 'passed',
      technicalApproval: 'approved',
      releaseApproval: 'approved',
      requiredTracks: ['internal', 'closed', 'production'],
    },
  }[stage];
  if (record.recordState !== requirements.recordState) {
    fail(`recordState must be ${requirements.recordState} for ${stage} verification.`);
  }
  for (const name of requirements.passedChecks) {
    if (verification[name].status !== 'passed') fail(`verification.${name}.status must be passed.`);
  }
  if (publication.track !== requirements.track || publication.status !== requirements.publicationStatus) {
    fail(`Publication must be ${requirements.track}/${requirements.publicationStatus} for ${stage}.`);
  }
  if (requirements.legal !== null && record.legalPublication.status !== requirements.legal) {
    fail(`legalPublication.status must be ${requirements.legal}.`);
  }
  if (requirements.technicalApproval !== null && approvals.technical.status !== requirements.technicalApproval) {
    fail(`approvals.technical.status must be ${requirements.technicalApproval}.`);
  }
  if (requirements.releaseApproval !== null && approvals.release.status !== requirements.releaseApproval) {
    fail(`approvals.release.status must be ${requirements.releaseApproval}.`);
  }
  if (
    Object.values(verification).some((entry) => entry.status === 'failed') ||
    record.legalPublication.status === 'failed' ||
    Object.values(approvals).some((entry) => entry.status === 'rejected')
  ) {
    fail(`${stage} record contains a failed or rejected gate.`);
  }
  if (stage === 'candidate') {
    if (
      publication.rolloutPercent !== 0 ||
      publication.submittedAt !== null ||
      publication.publishedAt !== null ||
      publication.events.length !== 0
    ) {
      fail('Candidate publication fields must remain empty.');
    }
  } else if (publication.submittedAt === null || publication.googlePlayReleaseId === null) {
    fail(`${stage} record must contain Google Play submission identity and time.`);
  } else {
    for (const track of requirements.requiredTracks) {
      const event = publication.events.find(
        (candidate) => candidate.track === track && candidate.status === 'completed',
      );
      if (event === undefined) fail(`${stage} record must contain a completed ${track} event.`);
      if (track === requirements.track && event.googlePlayReleaseId !== publication.googlePlayReleaseId) {
        fail(`${stage} current release ID does not match its completed track event.`);
      }
    }
  }
  if (stage === 'production') {
    if (record.sourceTag !== `v${record.versionName}`) fail('Production record must contain the source tag.');
    if (publication.publishedAt === null || publication.rolloutPercent !== 100) {
      fail('Production record must contain its publication time and 100% rollout.');
    }
    if (record.incidents.some((incident) => !['resolved', 'accepted'].includes(incident.status))) {
      fail('Production record contains an unresolved incident.');
    }
  }
  return {
    stage,
    versionName: record.versionName,
    versionCode: record.versionCode,
    gitCommit: record.gitCommit,
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
  const allowed = new Set(['--root', '--output', '--record', '--stage']);
  for (const name of Object.keys(options)) if (!allowed.has(name)) fail(`Unknown argument: ${name}`);
  return { command, options };
}

function usage() {
  return [
    'Usage:',
    '  node tools/release/release-record.mjs create [--root <directory>] [--output <relative-path>]',
    '  node tools/release/release-record.mjs verify --record <json-file> --stage <candidate|internal|closed|production>',
  ].join('\n');
}

const isMain = process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  try {
    const { command, options } = parseArguments(process.argv.slice(2));
    if (command === 'create') {
      const result = createReleaseRecord({
        root: resolve(options['--root'] ?? repositoryRoot),
        output: options['--output'] ?? defaultRecordPath,
      });
      console.log(`Release record draft created: ${result.relativePath}`);
      console.log(`${result.record.versionName} (${result.record.versionCode}) @ ${result.record.gitCommit}`);
    } else {
      if (options['--record'] === undefined || options['--stage'] === undefined) fail(usage());
      const recordPath = resolve(options['--record']);
      if (!existsSync(recordPath) || lstatSync(recordPath).isSymbolicLink()) {
        fail(`Release record is missing or unsafe: ${recordPath}`);
      }
      const record = JSON.parse(readFileSync(recordPath, 'utf8'));
      const result = validateReleaseRecord(record, options['--stage']);
      console.log(
        `Release record verified for ${result.stage}: ${result.versionName} (${result.versionCode}) @ ${result.gitCommit}`,
      );
    }
  } catch (error) {
    console.error(`Release record verification failed: ${error.message}`);
    process.exitCode = 1;
  }
}
