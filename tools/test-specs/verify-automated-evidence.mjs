import { existsSync, readFileSync } from 'node:fs';
import { dirname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const toolRoot = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(toolRoot, '../..');
const assignmentsPath = resolve(repositoryRoot, '.agents/test-specs/initial-release-assignments.tsv');
const resultsPath = resolve(repositoryRoot, '.agents/test-specs/initial-release-results.tsv');
const evidencePath = resolve(repositoryRoot, '.agents/test-specs/automated-test-evidence.tsv');
const evidenceHeader = ['id', 'testFile', 'testMethod', 'gradleTask'];
const testSourceTasks = new Map([
  ['app/src/test/', ':app:testDebugUnitTest'],
  ['app/src/androidTest/', ':app:connectedDebugAndroidTest'],
]);

function readTsv(path) {
  const lines = readFileSync(path, 'utf8').replace(/\r\n/g, '\n').trimEnd().split('\n');
  const header = lines.shift()?.split('\t') ?? [];
  return {
    header,
    rows: lines.map((line, index) => ({
      lineNumber: index + 2,
      values: line.split('\t'),
    })),
  };
}

function rowsById(path) {
  const { header, rows } = readTsv(path);
  const idIndex = header.indexOf('id');
  if (idIndex < 0) {
    throw new Error(`${relative(repositoryRoot, path)} does not contain an id column.`);
  }
  return {
    header,
    rows: new Map(rows.map(({ values }) => [values[idIndex], values])),
  };
}

function insideRepository(path) {
  const relativePath = relative(repositoryRoot, path);
  return relativePath !== '' && relativePath !== '..' && !relativePath.startsWith(`..${sep}`);
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function validate() {
  const errors = [];
  const assignments = rowsById(assignmentsPath);
  const results = rowsById(resultsPath);
  const assignmentColumns = Object.fromEntries(assignments.header.map((name, index) => [name, index]));
  const resultColumns = Object.fromEntries(results.header.map((name, index) => [name, index]));
  const { header, rows } = readTsv(evidencePath);

  if (header.join('\t') !== evidenceHeader.join('\t')) {
    throw new Error(`Invalid evidence TSV header. Expected: ${evidenceHeader.join('\t')}`);
  }

  const mappedIds = new Set();
  const mappedMethods = new Set();
  for (const { lineNumber, values } of rows) {
    if (values.length !== evidenceHeader.length) {
      errors.push(`Line ${lineNumber}: expected ${evidenceHeader.length} columns, got ${values.length}.`);
      continue;
    }
    const [id, testFile, testMethod, gradleTask] = values;
    const assignment = assignments.rows.get(id);
    const result = results.rows.get(id);
    const normalizedId = id.toLowerCase().replace('-', '');

    if (mappedIds.has(id)) errors.push(`Line ${lineNumber}: duplicate test ID ${id}.`);
    mappedIds.add(id);
    if (assignment === undefined) {
      errors.push(`Line ${lineNumber}: unknown test ID ${id}.`);
      continue;
    }
    if (assignment[assignmentColumns.lane] !== 'DEV_AUTO') {
      errors.push(`Line ${lineNumber}: ${id} must be assigned to DEV_AUTO.`);
    }
    if (!testMethod.startsWith(`${normalizedId}_`)) {
      errors.push(`Line ${lineNumber}: ${testMethod} must start with ${normalizedId}_.`);
    }

    const absoluteTestFile = resolve(repositoryRoot, testFile);
    const sourceEntry = [...testSourceTasks.entries()].find(([prefix]) => testFile.startsWith(prefix));
    if (!insideRepository(absoluteTestFile) || sourceEntry === undefined || !testFile.endsWith('.kt')) {
      errors.push(`Line ${lineNumber}: invalid automated test path ${testFile}.`);
      continue;
    }
    const [sourcePrefix, expectedTask] = sourceEntry;
    const expectedType = sourcePrefix === 'app/src/test/' ? 'UNIT' : 'INT';
    if (!assignment[assignmentColumns.type].split('/').includes(expectedType)) {
      errors.push(`Line ${lineNumber}: ${id} must include ${expectedType} in its test type.`);
    }
    if (gradleTask !== expectedTask) {
      errors.push(`Line ${lineNumber}: ${id} must use ${expectedTask}.`);
    }
    if (!existsSync(absoluteTestFile)) {
      errors.push(`Line ${lineNumber}: missing test file ${testFile}.`);
      continue;
    }
    const methodKey = `${testFile}#${testMethod}`;
    if (mappedMethods.has(methodKey)) errors.push(`Line ${lineNumber}: duplicate test method ${methodKey}.`);
    mappedMethods.add(methodKey);
    const source = readFileSync(absoluteTestFile, 'utf8');
    const methodPattern = new RegExp(`@Test\\s+fun\\s+${escapeRegex(testMethod)}\\s*\\(`, 'g');
    const matches = source.match(methodPattern) ?? [];
    if (matches.length !== 1) {
      errors.push(`Line ${lineNumber}: expected exactly one @Test method ${methodKey}, found ${matches.length}.`);
    }

    if (result === undefined || result[resultColumns.result] !== '合格') {
      errors.push(`Line ${lineNumber}: ${id} must be recorded as 合格 in initial-release-results.tsv.`);
    } else if (result[resultColumns.testerId] !== 'AUTO') {
      errors.push(`Line ${lineNumber}: ${id} testerId must be AUTO.`);
    }
  }

  for (const [id, assignment] of assignments.rows) {
    const result = results.rows.get(id);
    if (
      assignment[assignmentColumns.lane] === 'DEV_AUTO'
      && result?.[resultColumns.result] === '合格'
      && result?.[resultColumns.testerId] === 'AUTO'
      && !mappedIds.has(id)
    ) {
      errors.push(`${id} is an AUTO-passed DEV_AUTO item without automated test evidence.`);
    }
  }

  if (errors.length > 0) throw new Error(errors.join('\n'));
  console.log(`Verified ${rows.length} one-to-one automated test evidence rows.`);
}

try {
  validate();
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
