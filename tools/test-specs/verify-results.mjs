import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const toolRoot = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(toolRoot, '../..');
const testSpecsRoot = resolve(repositoryRoot, '.agents/test-specs');
const defaultResultsPath = '.agents/test-specs/initial-release-results.tsv';
const specFiles = [
  'app-core.md',
  'todo-list.md',
  'todo-editor.md',
  'calendar-history.md',
  'category-management.md',
  'archived-todos.md',
  'settings.md',
  'category-todo-list.md',
];
const header = [
  'id',
  'priority',
  'specFile',
  'result',
  'executedAt',
  'testerId',
  'versionCode',
  'environment',
  'evidence',
];
const allowedResults = new Set(['未実施', '合格', '不合格', '保留', '対象外']);

function usage() {
  console.error(
    'Usage: node tools/test-specs/verify-results.mjs [--initialize] [results.tsv]',
  );
}

function readLines(path) {
  const lines = readFileSync(path, 'utf8').replace(/\r\n/g, '\n').split('\n');
  if (lines.at(-1) === '') {
    lines.pop();
  }
  return lines;
}

function repositoryPath(input) {
  const path = resolve(repositoryRoot, input);
  const relativePath = relative(repositoryRoot, path);
  if (relativePath === '..' || relativePath.startsWith(`..${sep}`) || relativePath === '') {
    throw new Error('The results file must be inside the repository.');
  }
  return path;
}

function readCatalog() {
  const rows = [];
  const ids = new Set();
  for (const specFile of specFiles) {
    const path = resolve(testSpecsRoot, specFile);
    for (const line of readLines(path)) {
      const match = /^\| ([A-Z]+-(?:D)?[0-9]+) \| (P[0-2]) \|/.exec(line);
      if (match === null) {
        continue;
      }
      const [, id, priority] = match;
      if (ids.has(id)) {
        throw new Error(`Duplicate test ID in specifications: ${id}`);
      }
      ids.add(id);
      rows.push({ id, priority, specFile });
    }
  }
  return rows;
}

function initialize(path, catalog) {
  if (existsSync(path)) {
    throw new Error(`Refusing to overwrite an existing results file: ${relative(repositoryRoot, path)}`);
  }
  const lines = [
    header.join('\t'),
    ...catalog.map(({ id, priority, specFile }) =>
      [id, priority, specFile, '未実施', '-', '-', '-', '-', '-'].join('\t'),
    ),
  ];
  writeFileSync(path, `${lines.join('\n')}\n`, 'utf8');
  console.log(`Initialized ${catalog.length} results: ${relative(repositoryRoot, path)}`);
}

function validate(path, catalog) {
  if (!existsSync(path)) {
    throw new Error(`Results file is missing: ${relative(repositoryRoot, path)}`);
  }
  const lines = readLines(path);
  const actualHeader = lines.shift()?.split('\t') ?? [];
  if (actualHeader.join('\t') !== header.join('\t')) {
    throw new Error(`Invalid TSV header. Expected: ${header.join('\t')}`);
  }

  const expectedById = new Map(catalog.map((row) => [row.id, row]));
  const seen = new Set();
  const errors = [];
  const counts = Object.fromEntries(
    ['P0', 'P1', 'P2'].map((priority) => [
      priority,
      Object.fromEntries([...allowedResults].map((result) => [result, 0])),
    ]),
  );

  lines.forEach((line, index) => {
    const lineNumber = index + 2;
    const columns = line.split('\t');
    if (columns.length !== header.length) {
      errors.push(`Line ${lineNumber}: expected ${header.length} tab-separated columns, got ${columns.length}.`);
      return;
    }
    const [id, priority, specFile, result, executedAt, testerId, versionCode, environment, evidence] = columns;
    const expected = expectedById.get(id);
    if (expected === undefined) {
      errors.push(`Line ${lineNumber}: unknown test ID ${id || '(empty)'}.`);
      return;
    }
    if (seen.has(id)) {
      errors.push(`Line ${lineNumber}: duplicate test ID ${id}.`);
      return;
    }
    seen.add(id);
    if (priority !== expected.priority) {
      errors.push(`Line ${lineNumber}: ${id} priority must be ${expected.priority}, got ${priority}.`);
    }
    if (specFile !== expected.specFile) {
      errors.push(`Line ${lineNumber}: ${id} specFile must be ${expected.specFile}, got ${specFile}.`);
    }
    if (!allowedResults.has(result)) {
      errors.push(`Line ${lineNumber}: ${id} has invalid result ${result || '(empty)'}.`);
      return;
    }
    counts[expected.priority][result] += 1;

    const executionFields = [executedAt, testerId, versionCode, environment, evidence];
    if (result === '未実施') {
      if (executionFields.some((value) => value !== '-')) {
        errors.push(`Line ${lineNumber}: ${id} is unexecuted but does not use '-' placeholders.`);
      }
      return;
    }
    if (!/^\d{4}-\d{2}-\d{2}(?:T\d{2}:\d{2}(?::\d{2})?(?:Z|[+-]\d{2}:\d{2})?)?$/.test(executedAt)) {
      errors.push(`Line ${lineNumber}: ${id} executedAt must be an ISO date or date-time.`);
    }
    if (testerId.length === 0) {
      errors.push(`Line ${lineNumber}: ${id} testerId is required.`);
    }
    if (!/^[1-9][0-9]*$/.test(versionCode)) {
      errors.push(`Line ${lineNumber}: ${id} versionCode must be a positive integer.`);
    }
    if (environment.length === 0) {
      errors.push(`Line ${lineNumber}: ${id} environment is required.`);
    }
    if (evidence.length === 0) {
      errors.push(`Line ${lineNumber}: ${id} evidence is required.`);
    }
  });

  for (const { id } of catalog) {
    if (!seen.has(id)) {
      errors.push(`Missing test ID: ${id}`);
    }
  }
  if (lines.length !== catalog.length) {
    errors.push(`Expected ${catalog.length} result rows, got ${lines.length}.`);
  }
  if (errors.length > 0) {
    throw new Error(errors.join('\n'));
  }

  const totals = Object.fromEntries([...allowedResults].map((result) => [result, 0]));
  for (const priority of ['P0', 'P1', 'P2']) {
    for (const result of allowedResults) {
      totals[result] += counts[priority][result];
    }
  }
  const requiredPassed = counts.P0['合格'] + counts.P1['合格'];
  const requiredTotal = Object.values(counts.P0).reduce((sum, value) => sum + value, 0)
    + Object.values(counts.P1).reduce((sum, value) => sum + value, 0);

  console.log(`Verified ${catalog.length} test result rows: ${relative(repositoryRoot, path)}`);
  for (const priority of ['P0', 'P1', 'P2']) {
    console.log(
      `${priority}: ${[...allowedResults].map((result) => `${result}=${counts[priority][result]}`).join(', ')}`,
    );
  }
  console.log(`All: ${[...allowedResults].map((result) => `${result}=${totals[result]}`).join(', ')}`);
  console.log(`Required P0/P1 passed: ${requiredPassed}/${requiredTotal}`);
}

try {
  const args = process.argv.slice(2);
  const initializeRequested = args[0] === '--initialize';
  const pathArgument = initializeRequested ? args[1] : args[0];
  if (args.length > (initializeRequested ? 2 : 1)) {
    usage();
    process.exit(2);
  }
  const path = repositoryPath(pathArgument ?? defaultResultsPath);
  const catalog = readCatalog();
  if (initializeRequested) {
    initialize(path, catalog);
  }
  validate(path, catalog);
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
