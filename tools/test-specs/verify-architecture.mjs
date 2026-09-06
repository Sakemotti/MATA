import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDirectory, "../..");
const mainRoot = path.join(root, "app/src/main/java/com/mochisofts/mata");
const failures = [];

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), "utf8");
}

function filesBelow(directory, suffix = ".kt") {
  const result = [];
  const visit = (current) => {
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const target = path.join(current, entry.name);
      if (entry.isDirectory()) visit(target);
      else if (entry.name.endsWith(suffix)) result.push(target);
    }
  };
  visit(directory);
  return result;
}

function relative(file) {
  return path.relative(root, file).replaceAll("\\", "/");
}

function check(condition, message) {
  if (!condition) failures.push(message);
}

function matchingImports(file, pattern) {
  return fs.readFileSync(file, "utf8")
    .split("\n")
    .filter((line) => line.startsWith("import ") && pattern.test(line));
}

const versions = read("gradle/libs.versions.toml");
const appBuild = read("app/build.gradle");
const settings = read("settings.gradle");
check(/^agp = "9\./m.test(versions), "Android Gradle Plugin must remain on major version 9.");
check(!/org\.jetbrains\.kotlin\.android|kotlin-android/.test(appBuild), "Use AGP 9 built-in Kotlin, not the Android Kotlin plugin.");
check(appBuild.includes("alias(libs.plugins.compose.compiler)"), "Compose compiler plugin is missing.");
check(appBuild.includes("libs.androidx.compose.material3"), "Material 3 dependency is missing.");
check(appBuild.includes("libs.androidx.activity.compose"), "Compose Activity dependency is missing.");
check(appBuild.includes("kotlinx.coroutines"), "Coroutines dependency is missing.");
check(settings.includes("include ':app'"), "Application module is missing.");

const mainFiles = filesBelow(mainRoot);
const activities = mainFiles.flatMap((file) => {
  const source = fs.readFileSync(file, "utf8");
  return [...source.matchAll(/class\s+(\w+)\s*:\s*ComponentActivity\(\)/g)]
    .map((match) => `${relative(file)}:${match[1]}`);
});
const expectedActivities = [
  "app/src/main/java/com/mochisofts/mata/app/MainActivity.kt:MainActivity",
  "app/src/main/java/com/mochisofts/mata/widget/WidgetTodoActionActivity.kt:WidgetTodoActionActivity",
];
check(
  JSON.stringify(activities.sort()) === JSON.stringify(expectedActivities.sort()),
  `Activity hosts changed: ${activities.join(", ")}`,
);
const manifest = read("app/src/main/AndroidManifest.xml");
check(
  /android:name="\.widget\.WidgetTodoActionActivity"[\s\S]*?android:excludeFromRecents="true"[\s\S]*?android:exported="false"/.test(manifest),
  "The widget action auxiliary Activity must remain non-exported and excluded from recents.",
);

const domainFiles = filesBelow(path.join(mainRoot, "domain"));
for (const file of domainFiles) {
  const forbidden = matchingImports(
    file,
    /^import (?:android\.|androidx\.(?!paging\.)|com\.google\.|com\.mochisofts\.mata\.(?:app|data|ui|widget)\.)/,
  );
  check(forbidden.length === 0, `${relative(file)} has forbidden domain imports: ${forbidden.join(", ")}`);
}

const dataFiles = filesBelow(path.join(mainRoot, "data"));
for (const file of dataFiles) {
  const forbidden = matchingImports(file, /^import com\.mochisofts\.mata\.ui\./);
  check(forbidden.length === 0, `${relative(file)} depends on UI: ${forbidden.join(", ")}`);
}

const uiFiles = filesBelow(path.join(mainRoot, "ui"));
for (const file of uiFiles) {
  const forbidden = matchingImports(file, /^import com\.mochisofts\.mata\.(?:app|data)\./);
  check(forbidden.length === 0, `${relative(file)} bypasses core/domain: ${forbidden.join(", ")}`);
}

const screenFiles = uiFiles.filter((file) => /Screens?\.kt$/.test(file));
for (const file of screenFiles) {
  const source = fs.readFileSync(file, "utf8");
  const forbidden = matchingImports(
    file,
    /^import (?:androidx\.(?:room|datastore)\.|com\.mochisofts\.mata\.domain\.repository\.)/,
  );
  check(forbidden.length === 0, `${relative(file)} performs or imports data access: ${forbidden.join(", ")}`);
  check(!/\bText\s*\(\s*"[^"\n]+"/.test(source), `${relative(file)} contains a hard-coded Text string.`);
  check(!/contentDescription\s*=\s*"[^"\n]+"/.test(source), `${relative(file)} contains a hard-coded content description.`);
  check(!/\bColor\s*\(\s*0x[0-9a-f]+/i.test(source), `${relative(file)} contains a literal UI color.`);
  if (source.includes("hiltViewModel")) {
    check(source.includes("collectAsStateWithLifecycle"), `${relative(file)} does not collect ViewModel state with lifecycle awareness.`);
  }
}

const uiStateBlocks = uiFiles.flatMap((file) => {
  const source = fs.readFileSync(file, "utf8");
  return [...source.matchAll(/data class\s+\w+UiState\s*\([\s\S]*?\n\)/g)]
    .map((match) => ({ file, body: match[0] }));
});
check(uiStateBlocks.length >= 8, `Expected major immutable UiState definitions, found ${uiStateBlocks.length}.`);
for (const state of uiStateBlocks) {
  check(!/\bvar\s+/.test(state.body), `${relative(state.file)} exposes mutable UiState properties.`);
}

const scheduleCalculator = read("app/src/main/java/com/mochisofts/mata/domain/model/TodoScheduleCalculator.kt");
for (const operation of ["logicalDate", "deadlineAt", "occursOn", "recurrencePeriod", "effectiveDueSortMinutes"]) {
  check(scheduleCalculator.includes(`fun ${operation}`) || scheduleCalculator.includes(`.${operation}`), `Domain schedule operation ${operation} is missing.`);
}
check(
  read("app/src/main/java/com/mochisofts/mata/ui/todolist/TodoListViewModel.kt")
    .includes("effectiveDueSortMinutes"),
  "TODO deadline ordering must delegate to the domain logical-day calculation.",
);
check(
  read("app/src/test/java/com/mochisofts/mata/domain/model/TodoScheduleCalculatorTest.kt")
    .includes("effectiveDueSortMinutes_ordersDeadlinesWithinLogicalDay"),
  "The logical-day deadline ordering boundary test is missing.",
);

const editorConfig = read(".editorconfig");
const attributes = read(".gitattributes");
check(editorConfig.includes("charset = utf-8"), ".editorconfig must require UTF-8.");
check(editorConfig.includes("end_of_line = lf"), ".editorconfig must require LF.");
check(editorConfig.includes("indent_size = 4"), ".editorconfig must require four-space Kotlin indentation.");
check(attributes.includes("* text=auto eol=lf"), ".gitattributes must normalize text files to LF.");
const formatFiles = [
  ...filesBelow(path.join(root, "app/src")),
  path.join(root, "app/build.gradle"),
  path.join(root, "build.gradle"),
  path.join(root, "settings.gradle"),
];
for (const file of formatFiles) {
  const source = fs.readFileSync(file, "utf8");
  check(!source.includes("\r"), `${relative(file)} contains CRLF line endings.`);
  check(source.endsWith("\n"), `${relative(file)} has no final newline.`);
  check(!source.split("\n").some((line) => /^\t+\S/.test(line)), `${relative(file)} uses tab indentation.`);
}

if (failures.length > 0) {
  console.error("Architecture verification failed:");
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log("Architecture verification passed.");
console.log(`- Build stack: AGP 9 built-in Kotlin, Compose, Material 3, Coroutines`);
console.log(`- Activity hosts: ${activities.length} (one navigation host and one non-exported widget dialog host)`);
console.log(`- Layer sources: domain=${domainFiles.length}, data=${dataFiles.length}, ui=${uiFiles.length}`);
console.log(`- Immutable UiState definitions checked: ${uiStateBlocks.length}`);
console.log(`- Formatting and resource guards checked: ${formatFiles.length} source/build files`);
