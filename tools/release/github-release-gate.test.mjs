import assert from 'node:assert/strict';
import test from 'node:test';
import {
  assessGitHubReleaseGate,
  normalizeWorkflowName,
} from './github-release-gate.mjs';

function healthySnapshot() {
  const head = '1234567890abcdef1234567890abcdef12345678';
  return {
    git: { branch: 'main', clean: true, head, remoteHead: head },
    openIssueCount: 0,
    openPullRequestCount: 0,
    repository: {
      security_and_analysis: {
        dependabot_security_updates: { status: 'enabled' },
        secret_scanning: { status: 'enabled' },
        secret_scanning_push_protection: { status: 'enabled' },
      },
    },
    branchProtection: {
      required_status_checks: {
        strict: true,
        checks: [
          { context: 'Test, lint, and build', app_id: 15368 },
          { context: 'CodeQL', app_id: 57789 },
        ],
      },
      enforce_admins: { enabled: true },
      required_pull_request_reviews: { required_approving_review_count: 0 },
      required_conversation_resolution: { enabled: true },
      allow_force_pushes: { enabled: false },
      allow_deletions: { enabled: false },
    },
    codeScanningDefaultSetup: {
      state: 'configured',
      languages: ['actions', 'javascript-typescript'],
    },
    featureAvailability: {
      dependabotAlerts: true,
      codeScanning: true,
      secretScanning: true,
    },
    openAlertCounts: {
      dependabot: 0,
      code_scanning: 0,
      secret_scanning: 0,
    },
    workflowRuns: [
      { name: 'Android CI', status: 'completed', conclusion: 'success', html_url: 'https://example.test/android' },
      { name: 'CodeQL', status: 'completed', conclusion: 'success', html_url: 'https://example.test/codeql' },
    ],
  };
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

test('accepts the protected and alert-free release state with a Kotlin coverage warning', () => {
  const result = assessGitHubReleaseGate(healthySnapshot());

  assert.equal(result.status, 'passed');
  assert.equal(result.checks.filter(({ status }) => status === 'failed').length, 0);
  assert.deepEqual(
    result.checks.filter(({ status }) => status === 'warning').map(({ id }) => id),
    ['codeql_java_kotlin_coverage'],
  );
});

test('normalizes the dynamic default-setup CodeQL workflow name', () => {
  assert.equal(
    normalizeWorkflowName({
      name: 'Push on main',
      path: 'dynamic/github-code-scanning/codeql',
    }),
    'CodeQL',
  );
  assert.equal(
    normalizeWorkflowName({ name: 'Android CI', path: '.github/workflows/android-ci.yml' }),
    'Android CI',
  );
});

test('accepts Java/Kotlin coverage when the CodeQL extractor supports it', () => {
  const snapshot = healthySnapshot();
  snapshot.codeScanningDefaultSetup.languages.push('java-kotlin');

  const result = assessGitHubReleaseGate(snapshot);

  assert.equal(result.status, 'passed');
  assert.equal(result.checks.filter(({ status }) => status === 'warning').length, 0);
});

test('rejects stale, unprotected, unreviewed, alerted, or unverified release state', () => {
  const snapshot = clone(healthySnapshot());
  snapshot.git.branch = 'feature/example';
  snapshot.git.clean = false;
  snapshot.git.remoteHead = 'abcdefabcdefabcdefabcdefabcdefabcdefabcd';
  snapshot.openIssueCount = 1;
  snapshot.openPullRequestCount = 1;
  snapshot.branchProtection.required_status_checks.strict = false;
  snapshot.branchProtection.required_status_checks.checks = [];
  snapshot.branchProtection.enforce_admins.enabled = false;
  snapshot.branchProtection.required_pull_request_reviews = null;
  snapshot.branchProtection.required_conversation_resolution.enabled = false;
  snapshot.branchProtection.allow_force_pushes.enabled = true;
  snapshot.repository.security_and_analysis.dependabot_security_updates.status = 'disabled';
  snapshot.repository.security_and_analysis.secret_scanning.status = 'disabled';
  snapshot.featureAvailability.dependabotAlerts = false;
  snapshot.codeScanningDefaultSetup.state = 'not-configured';
  snapshot.openAlertCounts.dependabot = 2;
  snapshot.openAlertCounts.code_scanning = 1;
  snapshot.openAlertCounts.secret_scanning = 1;
  snapshot.workflowRuns = [];

  const result = assessGitHubReleaseGate(snapshot);
  const failedIds = new Set(
    result.checks.filter(({ status }) => status === 'failed').map(({ id }) => id),
  );

  assert.equal(result.status, 'failed');
  for (const expected of [
    'git_main',
    'git_clean',
    'git_remote_head',
    'open_issues',
    'open_pull_requests',
    'branch_protection_strict',
    'required_check_test_lint_and_build',
    'required_check_codeql',
    'branch_protection_admins',
    'pull_request_required',
    'conversation_resolution',
    'destructive_branch_updates',
    'dependabot_alerts',
    'dependabot_security_updates',
    'secret_scanning',
    'codeql_default_setup',
    'open_alerts_dependabot',
    'open_alerts_code_scanning',
    'open_alerts_secret_scanning',
    'workflow_android_ci',
    'workflow_codeql',
  ]) {
    assert.ok(failedIds.has(expected), `Expected ${expected} to fail.`);
  }
});
