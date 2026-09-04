import { createHash } from 'node:crypto';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { resolve, sep } from 'node:path';

const expectedArtifactTypes = new Set([
  'androidAppBundle',
  'r8Mapping',
  'openSourceLicenses',
  'mergedManifest',
  'cycloneDxSbom',
]);

function readText(path) {
  return readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
}

function sha256(path) {
  const hash = createHash('sha256');
  hash.update(readFileSync(path));
  return hash.digest('hex');
}

export function inspectReleaseArtifacts({
  metadataPath,
  repositoryRoot,
  expectedGitCommit = null,
  requirePublishable = false,
}) {
  if (!existsSync(metadataPath)) {
    return {
      problems: ['Release metadata is missing. Run :app:generateReleaseArtifactMetadata first.'],
      summary: null,
    };
  }

  try {
    const metadata = JSON.parse(readText(metadataPath));
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
      const actualTypes = new Set(metadata.artifacts.map((artifact) => artifact?.type));
      if (actualTypes.size !== metadata.artifacts.length) {
        problems.push('artifact types must be unique');
      }
      for (const expectedType of expectedArtifactTypes) {
        const count = metadata.artifacts.filter((artifact) => artifact?.type === expectedType).length;
        if (count !== 1) problems.push(`artifact type ${expectedType} appears ${count} times`);
      }

      for (const artifact of metadata.artifacts) {
        if (artifact === null || typeof artifact !== 'object' || Array.isArray(artifact)) {
          problems.push('artifact entry must be an object');
          continue;
        }
        if (!expectedArtifactTypes.has(artifact.type)) {
          problems.push(`unexpected artifact type ${artifact.type}`);
        }
        if (typeof artifact.path !== 'string' || artifact.path.trim() === '') {
          problems.push(`${artifact.type} path is invalid`);
          continue;
        }
        const path = resolve(repositoryRoot, artifact.path);
        if (path !== repositoryRoot && !path.startsWith(`${repositoryRoot}${sep}`)) {
          problems.push(`${artifact.type} path escapes repository`);
          continue;
        }
        if (!existsSync(path)) {
          problems.push(`${artifact.type} file is missing`);
          continue;
        }
        if (!statSync(path).isFile()) {
          problems.push(`${artifact.type} path is not a file`);
          continue;
        }
        if (!Number.isInteger(artifact.bytes) || artifact.bytes < 0) {
          problems.push(`${artifact.type} byte size is invalid`);
        }
        if (typeof artifact.sha256 !== 'string' || !/^[0-9a-f]{64}$/.test(artifact.sha256)) {
          problems.push(`${artifact.type} SHA-256 is invalid`);
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
                ['com.google.android.libraries.ads.mobile.sdk', 'ads-mobile-sdk'],
                ['com.google.android.ump', 'user-messaging-platform'],
              ];
              for (const [group, name] of requiredRuntimeComponents) {
                if (!sbom.components.some((component) => component.group === group && component.name === name)) {
                  problems.push(`SBOM is missing required runtime component ${group}:${name}`);
                }
              }
              if (sbom.components.some((component) => component.group === 'com.android.billingclient')) {
                problems.push('SBOM must not contain Google Play Billing components');
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
    if (!Array.isArray(signerFingerprints)) {
      problems.push('signing certificate SHA-256 must be an array');
    } else {
      if (signerFingerprints.some((value) => !/^[0-9a-f]{64}$/.test(value))) {
        problems.push('signing certificate SHA-256 contains an invalid fingerprint');
      }
      if (new Set(signerFingerprints).size !== signerFingerprints.length) {
        problems.push('signing certificate SHA-256 contains duplicates');
      }
      if ([...signerFingerprints].sort().join() !== signerFingerprints.join()) {
        problems.push('signing certificate SHA-256 must use canonical sorted order');
      }
    }
    if (metadata.publishable === true) {
      if (metadata.signing?.method !== 'uploadKey') {
        problems.push('publishable AAB must use Upload Key signing');
      }
      if (!Array.isArray(signerFingerprints) || signerFingerprints.length !== 1) {
        problems.push('publishable AAB must have exactly one signing certificate SHA-256');
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

    return {
      problems,
      summary: problems.length === 0
        ? `${metadata.versionName} (${metadata.versionCode}); ${metadata.artifacts.length} artifact hashes match; ${sbomSummary}; signing=${metadata.signing.method}.`
        : null,
    };
  } catch (error) {
    return { problems: [error.message], summary: null };
  }
}
