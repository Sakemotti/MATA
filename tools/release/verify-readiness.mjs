import { createHash } from 'node:crypto';
import {
  existsSync,
  mkdirSync,
  readFileSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const toolsRoot = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(toolsRoot, '../..');
const argumentsSet = new Set(process.argv.slice(2));
const supportedArguments = new Set(['--artifacts', '--release']);
const unknownArguments = [...argumentsSet].filter((argument) => !supportedArguments.has(argument));

if (unknownArguments.length > 0 || (argumentsSet.has('--artifacts') && argumentsSet.has('--release'))) {
  console.error('Usage: node tools/release/verify-readiness.mjs [--artifacts | --release]');
  process.exit(2);
}

const mode = argumentsSet.has('--release')
  ? 'release'
  : argumentsSet.has('--artifacts')
    ? 'artifacts'
    : 'draft';
const checks = [];

function addCheck(id, status, detail) {
  checks.push({ id, status, detail });
}

function run(command, args) {
  return spawnSync(command, args, {
    cwd: repositoryRoot,
    encoding: 'utf8',
    windowsHide: true,
  });
}

function commandOutput(result) {
  return `${result.stdout ?? ''}\n${result.stderr ?? ''}`.trim();
}

function conciseFailure(output) {
  const lines = output.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  return lines.slice(-20).join(' | ');
}

function verifyCommand(id, command, args) {
  const result = run(command, args);
  const output = commandOutput(result);
  if (result.error !== undefined) {
    addCheck(id, 'failed', result.error.message);
  } else if (result.status !== 0) {
    addCheck(id, 'failed', conciseFailure(output));
  } else {
    const lines = output.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
    addCheck(id, 'passed', lines.at(-1) ?? 'Command completed successfully.');
  }
}

function readText(path) {
  return readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
}

function sha256(path) {
  const hash = createHash('sha256');
  hash.update(readFileSync(path));
  return hash.digest('hex');
}

function git(...args) {
  const result = run('git', args);
  if (result.status !== 0) {
    throw new Error(conciseFailure(commandOutput(result)) || `git ${args.join(' ')} failed.`);
  }
  return result.stdout.trim();
}

function verifyBuildConfiguration() {
  const buildPath = resolve(repositoryRoot, 'app/build.gradle');
  if (!existsSync(buildPath)) {
    addCheck('build_configuration', 'failed', 'app/build.gradle is missing.');
    return;
  }
  const build = readText(buildPath);
  const versionCodeMatch = /def\s+releaseVersionCode\s*=\s*(\d+)\b/.exec(build);
  const versionNameMatch = /def\s+releaseVersionName\s*=\s*['"]([^'"]+)['"]/.exec(build);
  const requirements = [
    ['namespace', /namespace\s*=\s*['"]com\.mochisofts\.mata['"]/],
    ['applicationId', /applicationId\s+['"]com\.mochisofts\.mata['"]/],
    ['minSdk 26', /minSdk\s+26\b/],
    ['targetSdk 36', /targetSdk\s+36\b/],
    ['release optimization', /release\s*\{[\s\S]*?optimization\s*\{\s*enable\s*=\s*true\s*\}/],
    ['Baseline Profile', /baselineProfile\s+project\(['"]:benchmark['"]\)/],
    ['release AdMob properties', /gradleProperty\(['"]MATA_ADMOB_APP_ID['"]\)/],
    ['release legal URL properties', /gradleProperty\(['"]MATA_PRIVACY_POLICY_URL['"]\)/],
    ['Upload Key properties', /gradleProperty\(['"]MATA_UPLOAD_STORE_FILE['"]\)/],
    ['production signing gate', /gradleProperty\(['"]MATA_REQUIRE_UPLOAD_SIGNING['"]\)/],
    ['CycloneDX Release SBOM', /cyclonedxDirectBom[\s\S]*?releaseRuntimeClasspath/],
  ];
  const missing = requirements.filter(([, pattern]) => !pattern.test(build)).map(([label]) => label);
  if (versionCodeMatch === null || Number(versionCodeMatch[1]) < 1) {
    missing.push('positive releaseVersionCode');
  }
  if (versionNameMatch === null || !/^\d+\.\d+\.\d+$/.test(versionNameMatch[1])) {
    missing.push('semantic releaseVersionName');
  }
  if (missing.length > 0) {
    addCheck('build_configuration', 'failed', `Missing required configuration: ${missing.join(', ')}`);
  } else {
    addCheck(
      'build_configuration',
      'passed',
      `Application ID, SDK, version ${versionNameMatch[1]} (${versionCodeMatch[1]}), optimization, Baseline Profile, ads, and legal URL inputs match the release specification.`,
    );
  }
}

function verifyReleaseWorkflow() {
  const workflowPath = resolve(repositoryRoot, '.github/workflows/release-candidate.yml');
  if (!existsSync(workflowPath)) {
    addCheck('release_workflow', 'failed', 'The manual Release candidate workflow is missing.');
    return;
  }

  const workflow = readText(workflowPath);
  const triggerSection = workflow.split(/^permissions:/m)[0];
  const requirements = [
    ['manual trigger', /^  workflow_dispatch:/m],
    ['explicit confirmation', /inputs:\n      confirm:[\s\S]*?type: boolean/],
    ['main-only gate', /if: github\.ref == 'refs\/heads\/main' && inputs\.confirm/],
    ['protected environment', /environment:\n      name: release-candidate\n      deployment: false/],
    ['Upload Key secret', /secrets\.MATA_UPLOAD_KEYSTORE_BASE64/],
    ['Upload Key passwords', /secrets\.MATA_UPLOAD_STORE_PASSWORD[\s\S]*secrets\.MATA_UPLOAD_KEY_PASSWORD/],
    ['Upload Key alias', /vars\.MATA_UPLOAD_KEY_ALIAS/],
    ['production AdMob variables', /vars\.MATA_ADMOB_APP_ID[\s\S]*vars\.MATA_ADMOB_BANNER_AD_UNIT_ID/],
    ['signing requirement', /ORG_GRADLE_PROJECT_MATA_REQUIRE_UPLOAD_SIGNING: "true"/],
    ['configuration cache disabled', /--no-configuration-cache/],
    ['unit tests', /:app:testDebugUnitTest/],
    ['publishable verification', /verify-readiness\.mjs --release/],
    ['Play Store verification', /verify-play-store\.mjs --release/],
    ['legal-site packaging', /legal-site\/package-release\.ps1/],
    ['Release evidence upload', /actions\/upload-artifact@v7[\s\S]*app-release\.aab/],
    ['store evidence upload', /actions\/upload-artifact@v7[\s\S]*fastlane\/metadata\/android\/ja-JP/],
    ['legal-site evidence upload', /actions\/upload-artifact@v7[\s\S]*release-candidate\/legal-site/],
    ['artifact retention', /retention-days: 365/],
    ['temporary key cleanup', /if: always\(\)[\s\S]*rm -f "\$RUNNER_TEMP\/mata-upload\.jks"/],
  ];
  const missing = requirements.filter(([, pattern]) => !pattern.test(workflow)).map(([label]) => label);
  if (/^  (push|pull_request|schedule):/m.test(triggerSection)) {
    missing.push('manual-only trigger');
  }
  if (!/^permissions:\n  contents: read\n\nconcurrency:/m.test(workflow)) {
    missing.push('read-only workflow permissions');
  }
  if (/\b(upload_to_play_store|supply|publishBundle|publishReleaseBundle)\b/.test(workflow)) {
    missing.push('no Google Play publishing command');
  }

  if (missing.length > 0) {
    addCheck('release_workflow', 'failed', `Missing or unsafe workflow configuration: ${missing.join(', ')}`);
  } else {
    addCheck(
      'release_workflow',
      'passed',
      'Manual main-only workflow requires protected signing inputs, verifies all Release gates, and uploads evidence without publishing to Google Play.',
    );
  }
}

function verifyGitState(releaseMode) {
  try {
    const head = git('rev-parse', 'HEAD');
    const branch = git('branch', '--show-current');
    addCheck('git_identity', 'passed', `${branch || '(detached)'} @ ${head}`);
    if (!releaseMode) {
      return { head, branch };
    }

    const status = git('status', '--porcelain=v1', '--untracked-files=all');
    if (branch !== 'main') {
      addCheck('release_branch', 'failed', `Release candidates must be built from main, not ${branch || 'detached HEAD'}.`);
    } else {
      addCheck('release_branch', 'passed', 'Release candidate is on main.');
    }
    if (status !== '') {
      addCheck('clean_worktree', 'failed', 'Release candidate worktree contains tracked or untracked changes.');
    } else {
      addCheck('clean_worktree', 'passed', 'Release candidate worktree is clean.');
    }
    return { head, branch };
  } catch (error) {
    addCheck('git_identity', 'failed', error.message);
    return { head: null, branch: null };
  }
}

function verifyReleaseArtifacts(expectedGitCommit, requirePublishable) {
  const metadataPath = resolve(
    repositoryRoot,
    'app/build/outputs/release-metadata/release-metadata.json',
  );
  if (!existsSync(metadataPath)) {
    addCheck(
      'release_artifacts',
      'failed',
      'Release metadata is missing. Run :app:generateReleaseArtifactMetadata first.',
    );
    return;
  }

  try {
    const metadata = JSON.parse(readText(metadataPath));
    const expectedTypes = new Set([
      'androidAppBundle',
      'r8Mapping',
      'openSourceLicenses',
      'mergedManifest',
      'cycloneDxSbom',
    ]);
    const problems = [];
    let sbomSummary = null;
    if (metadata.schemaVersion !== 3) problems.push('schemaVersion must be 3');
    if (metadata.applicationId !== 'com.mochisofts.mata') problems.push('applicationId is invalid');
    if (!/^\d+\.\d+\.\d+$/.test(metadata.versionName)) problems.push('versionName is invalid');
    if (!Number.isInteger(metadata.versionCode) || metadata.versionCode < 1) {
      problems.push('versionCode is invalid');
    }
    if (Number.isNaN(Date.parse(metadata.buildTimestamp))) problems.push('buildTimestamp is invalid');
    if (expectedGitCommit !== null && metadata.gitCommit !== expectedGitCommit.toLowerCase()) {
      problems.push('gitCommit does not match HEAD');
    }
    if (!Array.isArray(metadata.artifacts)) {
      problems.push('artifacts must be an array');
    } else {
      const actualTypes = new Set(metadata.artifacts.map((artifact) => artifact.type));
      if (actualTypes.size !== metadata.artifacts.length) {
        problems.push('artifact types must be unique');
      }
      for (const expectedType of expectedTypes) {
        if (!actualTypes.has(expectedType)) problems.push(`missing artifact type ${expectedType}`);
      }
      for (const artifact of metadata.artifacts) {
        const path = resolve(repositoryRoot, artifact.path);
        if (path !== repositoryRoot && !path.startsWith(`${repositoryRoot}\\`) && !path.startsWith(`${repositoryRoot}/`)) {
          problems.push(`${artifact.type} path escapes repository`);
          continue;
        }
        if (!existsSync(path)) {
          problems.push(`${artifact.type} file is missing`);
          continue;
        }
        if (statSync(path).size !== artifact.bytes) problems.push(`${artifact.type} byte size changed`);
        if (sha256(path) !== artifact.sha256) problems.push(`${artifact.type} SHA-256 changed`);
        if (artifact.type === 'cycloneDxSbom') {
          try {
            const sbom = JSON.parse(readText(path));
            if (sbom.bomFormat !== 'CycloneDX') problems.push('SBOM format must be CycloneDX');
            if (sbom.specVersion !== '1.6') problems.push('SBOM specVersion must be 1.6');
            if (sbom.metadata?.timestamp !== metadata.buildTimestamp) {
              problems.push('SBOM timestamp does not match release metadata');
            }
            const rootComponent = sbom.metadata?.component;
            if (
              rootComponent?.type !== 'application' ||
              rootComponent?.group !== 'com.mochisofts' ||
              rootComponent?.name !== 'MATA' ||
              rootComponent?.version !== metadata.versionName
            ) {
              problems.push('SBOM root component identity is invalid');
            }
            if (!Array.isArray(sbom.components) || sbom.components.length === 0) {
              problems.push('SBOM components must be a non-empty array');
            } else {
              const componentRefs = new Set();
              for (const component of sbom.components) {
                if (typeof component['bom-ref'] !== 'string' || component['bom-ref'] === '') {
                  problems.push('SBOM component has no bom-ref');
                  continue;
                }
                if (componentRefs.has(component['bom-ref'])) {
                  problems.push(`SBOM component bom-ref is duplicated: ${component['bom-ref']}`);
                }
                componentRefs.add(component['bom-ref']);
              }
              const requiredRuntimeComponents = [
                ['com.android.billingclient', 'billing-ktx'],
                ['com.google.android.libraries.ads.mobile.sdk', 'ads-mobile-sdk'],
                ['com.google.android.ump', 'user-messaging-platform'],
              ];
              for (const [group, name] of requiredRuntimeComponents) {
                if (!sbom.components.some((component) => component.group === group && component.name === name)) {
                  problems.push(`SBOM is missing required runtime component ${group}:${name}`);
                }
              }
              sbomSummary = `${sbom.components.length} runtime components`;
            }
            if (!Array.isArray(sbom.dependencies) || sbom.dependencies.length === 0) {
              problems.push('SBOM dependency graph must be a non-empty array');
            }
          } catch (error) {
            problems.push(`CycloneDX SBOM is invalid: ${error.message}`);
          }
        }
      }
    }
    const signerFingerprints = metadata.signing?.certificateSha256;
    if (metadata.publishable === true) {
      if (metadata.signing?.method !== 'uploadKey') {
        problems.push('publishable AAB must use Upload Key signing');
      }
      if (!Array.isArray(signerFingerprints) || signerFingerprints.length < 1) {
        problems.push('publishable AAB has no signing certificate SHA-256');
      }
    } else if (
      metadata.publishable !== false ||
      metadata.signing?.method !== 'none' ||
      !Array.isArray(signerFingerprints) ||
      signerFingerprints.length !== 0
    ) {
      problems.push('verification AAB signing metadata is inconsistent');
    }
    if (requirePublishable && metadata.publishable !== true) {
      problems.push('AAB is a CI verification artifact and is not signed with the Upload Key');
    }

    if (problems.length > 0) {
      addCheck('release_artifacts', 'failed', problems.join('; '));
    } else {
      addCheck(
        'release_artifacts',
        'passed',
        `${metadata.versionName} (${metadata.versionCode}); ${metadata.artifacts.length} artifact hashes match; ${sbomSummary}; signing=${metadata.signing.method}.`,
      );
    }
  } catch (error) {
    addCheck('release_artifacts', 'failed', error.message);
  }
}

verifyBuildConfiguration();
verifyReleaseWorkflow();
const gitState = verifyGitState(mode === 'release');

const powershell = process.platform === 'win32' ? 'powershell' : 'pwsh';
const legalArguments = [
  '-NoProfile',
  '-ExecutionPolicy',
  'Bypass',
  '-File',
  resolve(repositoryRoot, 'legal-site/verify.ps1'),
];
if (mode === 'release') legalArguments.push('-ForRelease');
verifyCommand('legal_site', powershell, legalArguments);

const storeArguments = [resolve(repositoryRoot, 'fastlane/verify-play-store.mjs')];
if (mode === 'release') storeArguments.push('--release');
verifyCommand('play_store', process.execPath, storeArguments);

if (mode === 'artifacts' || mode === 'release') {
  verifyReleaseArtifacts(gitState.head, mode === 'release');
}

const failedChecks = checks.filter((check) => check.status === 'failed');
const report = {
  schemaVersion: 1,
  mode,
  generatedAt: new Date().toISOString(),
  status: failedChecks.length === 0 ? 'passed' : 'failed',
  checks,
};
const reportPath = resolve(
  repositoryRoot,
  'app/build/outputs/release-metadata/release-readiness.json',
);
mkdirSync(dirname(reportPath), { recursive: true });
writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');

for (const check of checks) {
  const marker = check.status === 'passed' ? 'PASS' : 'FAIL';
  console.log(`[${marker}] ${check.id}: ${check.detail}`);
}
console.log(`Report: ${relative(repositoryRoot, reportPath).replaceAll('\\', '/')}`);

if (failedChecks.length > 0) {
  console.error(`Release readiness ${mode} verification failed: ${failedChecks.length} check(s).`);
  process.exitCode = 1;
} else {
  console.log(`Release readiness ${mode} verification passed.`);
}
