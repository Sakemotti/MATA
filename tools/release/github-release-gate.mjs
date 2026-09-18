export const EXPECTED_REQUIRED_CHECKS = Object.freeze([
  Object.freeze({ context: 'Test, lint, and build', appId: 15368 }),
  Object.freeze({ context: 'CodeQL', appId: 57789 }),
]);

export function normalizeWorkflowName(workflowRun) {
  return workflowRun.path === 'dynamic/github-code-scanning/codeql'
    ? 'CodeQL'
    : workflowRun.name;
}

function addCheck(checks, id, status, detail) {
  checks.push({ id, status, detail });
}

function enabled(feature) {
  return feature?.status === 'enabled';
}

function requiredCheckMatches(actualChecks, expected) {
  return actualChecks.some((actual) => (
    actual.context === expected.context && actual.app_id === expected.appId
  ));
}

export function assessGitHubReleaseGate(snapshot) {
  const checks = [];
  const git = snapshot.git ?? {};
  const repo = snapshot.repository ?? {};
  const protection = snapshot.branchProtection ?? {};
  const statusChecks = protection.required_status_checks ?? {};
  const actualRequiredChecks = statusChecks.checks ?? [];
  const security = repo.security_and_analysis ?? {};
  const defaultSetup = snapshot.codeScanningDefaultSetup ?? {};
  const languages = new Set(defaultSetup.languages ?? []);
  const alertCounts = snapshot.openAlertCounts ?? {};
  const workflowRuns = snapshot.workflowRuns ?? [];

  addCheck(
    checks,
    'git_main',
    git.branch === 'main' ? 'passed' : 'failed',
    git.branch === 'main' ? `main @ ${git.head}` : `Expected main, found ${git.branch || '(detached)'}.`,
  );
  addCheck(
    checks,
    'git_clean',
    git.clean ? 'passed' : 'failed',
    git.clean ? 'Worktree is clean.' : 'Worktree contains tracked or untracked changes.',
  );
  addCheck(
    checks,
    'git_remote_head',
    git.head !== undefined && git.head === git.remoteHead ? 'passed' : 'failed',
    git.head === git.remoteHead
      ? 'Local HEAD matches the GitHub main ref.'
      : `Local HEAD ${git.head ?? '(unknown)'} does not match GitHub main ${git.remoteHead ?? '(unknown)'}.`,
  );

  addCheck(
    checks,
    'open_issues',
    snapshot.openIssueCount === 0 ? 'passed' : 'failed',
    `${snapshot.openIssueCount ?? 'unknown'} open issue(s).`,
  );
  addCheck(
    checks,
    'open_pull_requests',
    snapshot.openPullRequestCount === 0 ? 'passed' : 'failed',
    `${snapshot.openPullRequestCount ?? 'unknown'} open pull request(s).`,
  );

  addCheck(
    checks,
    'branch_protection_strict',
    statusChecks.strict === true ? 'passed' : 'failed',
    statusChecks.strict === true
      ? 'Required status checks are strict.'
      : 'Required status checks are missing or do not require an up-to-date branch.',
  );
  for (const expected of EXPECTED_REQUIRED_CHECKS) {
    const matches = requiredCheckMatches(actualRequiredChecks, expected);
    addCheck(
      checks,
      `required_check_${expected.context.toLowerCase().replaceAll(/[^a-z0-9]+/g, '_')}`,
      matches ? 'passed' : 'failed',
      matches
        ? `${expected.context} is required from GitHub App ${expected.appId}.`
        : `${expected.context} from GitHub App ${expected.appId} is not required.`,
    );
  }
  addCheck(
    checks,
    'branch_protection_admins',
    protection.enforce_admins?.enabled === true ? 'passed' : 'failed',
    protection.enforce_admins?.enabled === true
      ? 'Branch protection applies to administrators.'
      : 'Branch protection does not apply to administrators.',
  );
  addCheck(
    checks,
    'pull_request_required',
    protection.required_pull_request_reviews !== null
      && protection.required_pull_request_reviews !== undefined
      && protection.required_pull_request_reviews.required_approving_review_count === 0
      ? 'passed'
      : 'failed',
    protection.required_pull_request_reviews?.required_approving_review_count === 0
      ? 'Pull requests are required with zero approvals for the single-maintainer workflow.'
      : 'Pull request protection is missing or its approval count differs from zero.',
  );
  addCheck(
    checks,
    'conversation_resolution',
    protection.required_conversation_resolution?.enabled === true ? 'passed' : 'failed',
    protection.required_conversation_resolution?.enabled === true
      ? 'Review conversation resolution is required.'
      : 'Review conversation resolution is not required.',
  );
  addCheck(
    checks,
    'destructive_branch_updates',
    protection.allow_force_pushes?.enabled === false
      && protection.allow_deletions?.enabled === false
      ? 'passed'
      : 'failed',
    protection.allow_force_pushes?.enabled === false
      && protection.allow_deletions?.enabled === false
      ? 'Force pushes and branch deletion are disabled.'
      : 'Force pushes or branch deletion are allowed.',
  );

  addCheck(
    checks,
    'dependabot_alerts',
    snapshot.featureAvailability?.dependabotAlerts === true ? 'passed' : 'failed',
    snapshot.featureAvailability?.dependabotAlerts === true
      ? 'Dependabot alerts are enabled and queryable.'
      : 'Dependabot alerts are disabled or unavailable.',
  );
  addCheck(
    checks,
    'dependabot_security_updates',
    enabled(security.dependabot_security_updates) ? 'passed' : 'failed',
    enabled(security.dependabot_security_updates)
      ? 'Dependabot security updates are enabled.'
      : 'Dependabot security updates are disabled.',
  );
  addCheck(
    checks,
    'secret_scanning',
    enabled(security.secret_scanning)
      && enabled(security.secret_scanning_push_protection)
      && snapshot.featureAvailability?.secretScanning === true
      ? 'passed'
      : 'failed',
    enabled(security.secret_scanning)
      && enabled(security.secret_scanning_push_protection)
      && snapshot.featureAvailability?.secretScanning === true
      ? 'Secret scanning and push protection are enabled and queryable.'
      : 'Secret scanning or push protection is disabled or unavailable.',
  );
  addCheck(
    checks,
    'codeql_default_setup',
    defaultSetup.state === 'configured'
      && languages.has('actions')
      && languages.has('javascript-typescript')
      && snapshot.featureAvailability?.codeScanning === true
      ? 'passed'
      : 'failed',
    defaultSetup.state === 'configured'
      && languages.has('actions')
      && languages.has('javascript-typescript')
      && snapshot.featureAvailability?.codeScanning === true
      ? 'CodeQL default setup covers Actions and JavaScript/TypeScript.'
      : 'CodeQL default setup is missing required language coverage or is unavailable.',
  );
  addCheck(
    checks,
    'codeql_java_kotlin_coverage',
    languages.has('java-kotlin') ? 'passed' : 'warning',
    languages.has('java-kotlin')
      ? 'CodeQL covers Java/Kotlin.'
      : 'CodeQL Java/Kotlin coverage is unavailable; retain Android CI and recheck extractor support.',
  );

  for (const [name, id] of [
    ['Dependabot', 'dependabot'],
    ['Code scanning', 'code_scanning'],
    ['Secret scanning', 'secret_scanning'],
  ]) {
    const count = alertCounts[id];
    addCheck(
      checks,
      `open_alerts_${id}`,
      count === 0 ? 'passed' : 'failed',
      count === 0 ? `${name} has no open alerts.` : `${name} has ${count ?? 'unknown'} open alert(s).`,
    );
  }

  for (const workflowName of ['Android CI', 'CodeQL']) {
    const run = workflowRuns.find((candidate) => candidate.name === workflowName);
    const passed = run?.status === 'completed' && run?.conclusion === 'success';
    addCheck(
      checks,
      `workflow_${workflowName.toLowerCase().replaceAll(/[^a-z0-9]+/g, '_')}`,
      passed ? 'passed' : 'failed',
      passed
        ? `${workflowName} succeeded for ${git.head}: ${run.html_url}`
        : `${workflowName} has no successful completed run for ${git.head}.`,
    );
  }

  return {
    status: checks.some(({ status }) => status === 'failed') ? 'failed' : 'passed',
    checks,
  };
}
