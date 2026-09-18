import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import {
  assessGitHubReleaseGate,
  normalizeWorkflowName,
} from './github-release-gate.mjs';

const toolsRoot = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(toolsRoot, '../..');
const args = process.argv.slice(2);
let repository = 'Sakemotti/MATA';

if (args.length > 0) {
  if (args.length !== 2 || args[0] !== '--repo' || !/^[^/\s]+\/[^/\s]+$/.test(args[1])) {
    console.error('Usage: node tools/release/verify-github-release-gates.mjs [--repo OWNER/REPO]');
    process.exit(2);
  }
  repository = args[1];
}

function run(command, commandArgs) {
  return spawnSync(command, commandArgs, {
    cwd: repositoryRoot,
    encoding: 'utf8',
    windowsHide: true,
  });
}

function output(result) {
  return `${result.stdout ?? ''}\n${result.stderr ?? ''}`.trim();
}

function conciseFailure(value) {
  return value.split(/\r?\n/).map((line) => line.trim()).filter(Boolean).slice(-10).join(' | ');
}

function successful(command, commandArgs) {
  const result = run(command, commandArgs);
  if (result.error !== undefined || result.status !== 0) {
    throw new Error(conciseFailure(output(result)) || `${command} failed.`);
  }
  return result.stdout.trim();
}

function git(...gitArgs) {
  return successful('git', gitArgs);
}

function ghApi(path, { json = true } = {}) {
  const value = successful('gh', [
    'api',
    '-H',
    'Accept: application/vnd.github+json',
    '-H',
    'X-GitHub-Api-Version: 2022-11-28',
    path,
  ]);
  if (!json) return null;
  try {
    return JSON.parse(value);
  } catch (error) {
    throw new Error(`GitHub API returned invalid JSON for ${path}: ${error.message}`);
  }
}

function collectSnapshot() {
  const head = git('rev-parse', 'HEAD');
  const branch = git('branch', '--show-current');
  const clean = git('status', '--porcelain=v1', '--untracked-files=all') === '';
  const remoteRef = ghApi(`repos/${repository}/git/ref/heads/main`);
  const repositoryState = ghApi(`repos/${repository}`);
  const branchProtection = ghApi(`repos/${repository}/branches/main/protection`);
  const codeScanningDefaultSetup = ghApi(`repos/${repository}/code-scanning/default-setup`);
  const issues = ghApi(`repos/${repository}/issues?state=open&per_page=100`);
  const pulls = ghApi(`repos/${repository}/pulls?state=open&per_page=100`);

  ghApi(`repos/${repository}/vulnerability-alerts`, { json: false });
  const dependabotAlerts = ghApi(`repos/${repository}/dependabot/alerts?state=open&per_page=100`);
  const codeScanningAlerts = ghApi(`repos/${repository}/code-scanning/alerts?state=open&per_page=100`);
  const secretScanningAlerts = ghApi(`repos/${repository}/secret-scanning/alerts?state=open&per_page=100`);
  const workflowResponse = ghApi(
    `repos/${repository}/actions/runs?branch=main&head_sha=${encodeURIComponent(head)}&per_page=100`,
  );

  return {
    git: {
      branch,
      clean,
      head,
      remoteHead: remoteRef.object?.sha,
    },
    openIssueCount: issues.filter((issue) => issue.pull_request === undefined).length,
    openPullRequestCount: pulls.length,
    repository: repositoryState,
    branchProtection,
    codeScanningDefaultSetup,
    featureAvailability: {
      dependabotAlerts: true,
      codeScanning: true,
      secretScanning: true,
    },
    openAlertCounts: {
      dependabot: dependabotAlerts.length,
      code_scanning: codeScanningAlerts.length,
      secret_scanning: secretScanningAlerts.length,
    },
    workflowRuns: (workflowResponse.workflow_runs ?? []).map((runState) => ({
      name: normalizeWorkflowName(runState),
      status: runState.status,
      conclusion: runState.conclusion,
      html_url: runState.html_url,
    })),
  };
}

let assessment;
let head = null;
try {
  const snapshot = collectSnapshot();
  head = snapshot.git.head;
  assessment = assessGitHubReleaseGate(snapshot);
} catch (error) {
  assessment = {
    status: 'failed',
    checks: [{ id: 'github_snapshot', status: 'failed', detail: error.message }],
  };
}

const report = {
  schemaVersion: 1,
  repository,
  gitCommit: head,
  generatedAt: new Date().toISOString(),
  status: assessment.status,
  checks: assessment.checks,
};
const reportPath = resolve(
  repositoryRoot,
  'app/build/outputs/release-metadata/github-release-gates.json',
);
mkdirSync(dirname(reportPath), { recursive: true });
writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');

for (const check of assessment.checks) {
  const marker = check.status === 'passed' ? 'PASS' : check.status === 'warning' ? 'WARN' : 'FAIL';
  console.log(`[${marker}] ${check.id}: ${check.detail}`);
}
console.log(`Report: ${relative(repositoryRoot, reportPath).replaceAll('\\', '/')}`);

if (assessment.status === 'failed') {
  console.error('GitHub release gate verification failed.');
  process.exitCode = 1;
} else {
  console.log('GitHub release gate verification passed.');
}
