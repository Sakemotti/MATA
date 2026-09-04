import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const toolRoot = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(toolRoot, '../..');
const testSpecsRoot = resolve(repositoryRoot, '.agents/test-specs');
const defaultAssignmentsPath = '.agents/test-specs/initial-release-assignments.tsv';
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
const header = ['id', 'priority', 'specFile', 'type', 'lane', 'owner', 'testPack'];
const testerPools = {
  APP: ['T01', 'T10', 'T11', 'T12'],
  TL: ['T01', 'T03', 'T08', 'T10'],
  TE: ['T02', 'T03', 'T08'],
  CH: ['T04', 'T09', 'T10'],
  CM: ['T05', 'T07', 'T11'],
  AT: ['T02', 'T04', 'T06', 'T12'],
  ST: ['T05', 'T06', 'T07', 'T11'],
  CTL: ['T01', 'T12'],
  DAT: ['T07', 'T12'],
  NTF: ['T08'],
  WGT: ['T09'],
  RPT: ['T03'],
  STA: ['T04'],
};
const testPacks = {
  APP: '基本・互換性',
  DAY: '論理日',
  RPT: '繰り返し',
  STA: '状態・期限',
  NTF: '通知',
  WGT: 'ウィジェット',
  DAT: 'データ保全',
  REL: 'リリース',
  TL: 'TODO一覧',
  TE: 'TODO登録・編集',
  CH: 'カレンダー・履歴',
  CM: 'カテゴリ管理',
  AT: 'アーカイブ',
  ST: '設定',
  CTL: 'カテゴリ別TODO',
};
const lanes = new Set(['CLOSED_TESTER', 'DEV_AUTO', 'RELEASE_OWNER']);

function usage() {
  console.error(
    'Usage: node tools/test-specs/verify-assignments.mjs [--initialize] [assignments.tsv]',
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
    throw new Error('The assignments file must be inside the repository.');
  }
  return path;
}

function readCatalog() {
  const rows = [];
  const ids = new Set();
  for (const specFile of specFiles) {
    const path = resolve(testSpecsRoot, specFile);
    for (const line of readLines(path)) {
      const match = /^\| ([A-Z]+-(?:D)?[0-9]+) \| (P[0-2]) \| ([^|]+) \|/.exec(line);
      if (match === null) {
        continue;
      }
      const [, id, priority, rawType] = match;
      if (ids.has(id)) {
        throw new Error(`Duplicate test ID in specifications: ${id}`);
      }
      ids.add(id);
      rows.push({ id, priority, specFile, type: rawType.trim(), prefix: id.split('-')[0] });
    }
  }
  return rows;
}

function testerSuitable(row) {
  return row.prefix !== 'REL' && /(?:^|\/)(?:UI|MANUAL|E2E)(?:\/|$)/.test(row.type);
}

function expectedLane(row) {
  if (row.prefix === 'REL') {
    return 'RELEASE_OWNER';
  }
  return testerSuitable(row) ? 'CLOSED_TESTER' : 'DEV_AUTO';
}

function initialize(path, catalog) {
  if (existsSync(path)) {
    throw new Error(`Refusing to overwrite an existing assignments file: ${relative(repositoryRoot, path)}`);
  }
  const poolIndexes = {};
  const rows = catalog.map((row) => {
    const lane = expectedLane(row);
    let owner;
    if (lane === 'CLOSED_TESTER') {
      const pool = testerPools[row.prefix];
      if (pool === undefined) {
        throw new Error(`No tester pool is configured for ${row.prefix}.`);
      }
      const poolIndex = poolIndexes[row.prefix] ?? 0;
      owner = pool[poolIndex % pool.length];
      poolIndexes[row.prefix] = poolIndex + 1;
    } else {
      owner = lane === 'RELEASE_OWNER' ? 'OWNER' : 'DEV';
    }
    const testPack = testPacks[row.prefix];
    if (testPack === undefined) {
      throw new Error(`No test pack is configured for ${row.prefix}.`);
    }
    return [row.id, row.priority, row.specFile, row.type, lane, owner, testPack].join('\t');
  });
  writeFileSync(path, `${[header.join('\t'), ...rows].join('\n')}\n`, 'utf8');
  console.log(`Initialized ${catalog.length} assignments: ${relative(repositoryRoot, path)}`);
}

function validate(path, catalog) {
  if (!existsSync(path)) {
    throw new Error(`Assignments file is missing: ${relative(repositoryRoot, path)}`);
  }
  const lines = readLines(path);
  const actualHeader = lines.shift()?.split('\t') ?? [];
  if (actualHeader.join('\t') !== header.join('\t')) {
    throw new Error(`Invalid TSV header. Expected: ${header.join('\t')}`);
  }

  const expectedById = new Map(catalog.map((row) => [row.id, row]));
  const seen = new Set();
  const errors = [];
  const laneCounts = Object.fromEntries([...lanes].map((lane) => [lane, 0]));
  const ownerCounts = {};

  lines.forEach((line, index) => {
    const lineNumber = index + 2;
    const columns = line.split('\t');
    if (columns.length !== header.length) {
      errors.push(`Line ${lineNumber}: expected ${header.length} tab-separated columns, got ${columns.length}.`);
      return;
    }
    const [id, priority, specFile, type, lane, owner, testPack] = columns;
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
    if (priority !== expected.priority || specFile !== expected.specFile || type !== expected.type) {
      errors.push(`Line ${lineNumber}: ${id} metadata does not match ${expected.specFile}.`);
    }
    const requiredLane = expectedLane(expected);
    if (!lanes.has(lane) || lane !== requiredLane) {
      errors.push(`Line ${lineNumber}: ${id} lane must be ${requiredLane}, got ${lane}.`);
      return;
    }
    if (lane === 'CLOSED_TESTER' && !/^T(?:0[1-9]|1[0-2])$/.test(owner)) {
      errors.push(`Line ${lineNumber}: ${id} must be assigned to T01-T12.`);
    }
    if (lane === 'DEV_AUTO' && owner !== 'DEV') {
      errors.push(`Line ${lineNumber}: ${id} DEV_AUTO owner must be DEV.`);
    }
    if (lane === 'RELEASE_OWNER' && owner !== 'OWNER') {
      errors.push(`Line ${lineNumber}: ${id} RELEASE_OWNER owner must be OWNER.`);
    }
    if (testPack !== testPacks[expected.prefix]) {
      errors.push(`Line ${lineNumber}: ${id} testPack must be ${testPacks[expected.prefix]}.`);
    }
    laneCounts[lane] += 1;
    ownerCounts[owner] = (ownerCounts[owner] ?? 0) + 1;
  });

  for (const { id } of catalog) {
    if (!seen.has(id)) {
      errors.push(`Missing test ID: ${id}`);
    }
  }
  if (lines.length !== catalog.length) {
    errors.push(`Expected ${catalog.length} assignment rows, got ${lines.length}.`);
  }
  if (errors.length > 0) {
    throw new Error(errors.join('\n'));
  }

  console.log(`Verified ${catalog.length} assignments: ${relative(repositoryRoot, path)}`);
  console.log([...lanes].map((lane) => `${lane}=${laneCounts[lane]}`).join(', '));
  console.log(
    Object.entries(ownerCounts)
      .sort(([left], [right]) => left.localeCompare(right, 'en'))
      .map(([owner, count]) => `${owner}=${count}`)
      .join(', '),
  );
}

try {
  const args = process.argv.slice(2);
  const initializeRequested = args[0] === '--initialize';
  const pathArgument = initializeRequested ? args[1] : args[0];
  if (args.length > (initializeRequested ? 2 : 1)) {
    usage();
    process.exit(2);
  }
  const path = repositoryPath(pathArgument ?? defaultAssignmentsPath);
  const catalog = readCatalog();
  if (initializeRequested) {
    initialize(path, catalog);
  }
  validate(path, catalog);
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
