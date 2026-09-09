import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDirectory, "../..");
const failures = [];

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), "utf8");
}

function filesBelow(relativeDirectory, suffix) {
  const result = [];
  const visit = (directory) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const target = path.join(directory, entry.name);
      if (entry.isDirectory()) visit(target);
      else if (entry.name.endsWith(suffix)) result.push(target);
    }
  };
  visit(path.join(root, relativeDirectory));
  return result;
}

function relative(file) {
  return path.relative(root, file).replaceAll("\\", "/");
}

function check(condition, message) {
  if (!condition) failures.push(message);
}

function checkIncludes(source, fragment, message) {
  check(source.includes(fragment), message);
}

function sameMembers(actual, expected) {
  return JSON.stringify([...actual].sort()) === JSON.stringify([...expected].sort());
}

const appBuild = read("app/build.gradle");
const versions = read("gradle/libs.versions.toml");
const manifest = read("app/src/main/AndroidManifest.xml");
const networkSecurity = read("app/src/main/res/xml/network_security_config.xml");
const holidayClient = read(
  "app/src/main/java/com/mochisofts/mata/data/holiday/HolidaysJpApi.kt",
);
const holidayPrivacyTest = read(
  "app/src/androidTest/java/com/mochisofts/mata/data/holiday/HolidayHttpRequestPrivacyTest.kt",
);
const adsConsent = read(
  "app/src/main/java/com/mochisofts/mata/data/ads/GoogleAdsConsentRepository.kt",
);
const bannerAd = read("app/src/main/java/com/mochisofts/mata/ui/ads/MataBannerAd.kt");
const diagnostics = read(
  "app/src/main/java/com/mochisofts/mata/core/observability/Diagnostics.kt",
);
const legalLinks = read(
  "app/src/main/java/com/mochisofts/mata/ui/settings/LegalDocumentLinks.kt",
);

const expectedRuntimeLibraries = [
  "aboutlibraries.core",
  "androidx.activity.compose",
  "androidx.compose.icons.extended",
  "androidx.compose.material3",
  "androidx.compose.ui.tooling.preview",
  "androidx.core.ktx",
  "androidx.core.splashscreen",
  "androidx.datastore.preferences",
  "androidx.glance.appwidget",
  "androidx.glance.material3",
  "androidx.hilt.lifecycle.viewmodel.compose",
  "androidx.lifecycle.runtime.compose",
  "androidx.lifecycle.viewmodel.compose",
  "androidx.navigation.compose",
  "androidx.paging.compose",
  "androidx.paging.runtime",
  "androidx.profileinstaller",
  "androidx.room.ktx",
  "androidx.room.paging",
  "androidx.room.runtime",
  "androidx.window",
  "androidx.work.runtime",
  "google.mobile.ads.next.gen",
  "google.user.messaging.platform",
  "hilt.android",
  "kotlinx.coroutines.core",
  "kotlinx.serialization.core",
  "kotlinx.serialization.json",
  "material",
];
const actualRuntimeLibraries = [...appBuild.matchAll(/^\s*implementation libs\.([a-z0-9.]+)\s*$/gm)]
  .map((match) => match[1]);
check(
  sameMembers(actualRuntimeLibraries, expectedRuntimeLibraries),
  `Direct runtime dependencies changed. Review their data behavior and update the contract. Actual: ${actualRuntimeLibraries.join(", ")}`,
);
check(
  /^\s*implementation composeBom\s*$/m.test(appBuild),
  "The Compose BOM runtime platform declaration is missing.",
);

check(/^gmaNextGen = "1\.4\.0"$/m.test(versions), "Reviewed GMA Next-Gen version must remain 1.4.0.");
check(/^ump = "4\.0\.0"$/m.test(versions), "Reviewed UMP version must remain 4.0.0.");
checkIncludes(
  versions,
  'group = "com.google.android.libraries.ads.mobile.sdk", name = "ads-mobile-sdk"',
  "The reviewed GMA Next-Gen artifact changed.",
);
checkIncludes(
  versions,
  'group = "com.google.android.ump", name = "user-messaging-platform"',
  "The reviewed UMP artifact changed.",
);

const sourcePermissions = [...manifest.matchAll(/<uses-permission android:name="([^"]+)"\s*\/>/g)]
  .map((match) => match[1]);
const expectedSourcePermissions = [
  "android.permission.ACCESS_NETWORK_STATE",
  "android.permission.INTERNET",
  "android.permission.POST_NOTIFICATIONS",
  "android.permission.RECEIVE_BOOT_COMPLETED",
  "android.permission.SCHEDULE_EXACT_ALARM",
];
check(
  sameMembers(sourcePermissions, expectedSourcePermissions),
  `Application permissions changed. Review Play declarations and legal documents. Actual: ${sourcePermissions.join(", ")}`,
);
checkIncludes(networkSecurity, 'cleartextTrafficPermitted="false"', "Cleartext network traffic must remain disabled.");

const kotlinUrls = filesBelow("app/src/main/java", ".kt").flatMap((file) => {
  const source = fs.readFileSync(file, "utf8");
  return [...source.matchAll(/https?:\/\/[^"'\s)]+/g)]
    .map((match) => `${relative(file)}:${match[0]}`);
});
const expectedKotlinUrls = [
  "app/src/main/java/com/mochisofts/mata/data/holiday/HolidaysJpApi.kt:https://holidays-jp.github.io/api/v1/date.json",
];
check(
  sameMembers(kotlinUrls, expectedKotlinUrls),
  `Fixed URLs in application Kotlin changed. Review the external transmission declaration. Actual: ${kotlinUrls.join(", ")}`,
);
checkIncludes(appBuild, 'def debugPrivacyPolicyUrl = "https://mochisofts.com/mata/privacy"', "Debug privacy URL changed.");
checkIncludes(appBuild, 'def debugTermsUrl = "https://mochisofts.com/mata/terms"', "Debug terms URL changed.");
checkIncludes(legalLinks, 'uri.host == "mochisofts.com"', "Legal links must remain restricted to mochisofts.com.");
checkIncludes(legalLinks, 'uri.scheme == "https"', "Legal links must require HTTPS.");

for (const fragment of [
  'const val API_URL = "https://holidays-jp.github.io/api/v1/date.json"',
  'method = "GET"',
  'put("User-Agent", "MATA")',
  "hasBody = false",
  'put("If-None-Match", it)',
  'put("If-Modified-Since", it)',
]) {
  checkIncludes(holidayClient, fragment, `Holidays JP request contract changed: ${fragment}`);
}
for (const fragment of [
  'assertEquals("holidays-jp.github.io", request.target.host)',
  "assertFalse(request.hasBody)",
  '"private todo title"',
  '"private category"',
  '"private history"',
]) {
  checkIncludes(holidayPrivacyTest, fragment, `Holidays JP privacy coverage changed: ${fragment}`);
}

for (const fragment of [
  "requestConsentInfoUpdate",
  "loadAndShowConsentFormIfRequired",
  "canRequestAds()",
  "showPrivacyOptionsForm",
  "MobileAds.initialize",
]) {
  checkIncludes(adsConsent, fragment, `Ads consent gate changed: ${fragment}`);
}
checkIncludes(bannerAd, "BannerAdRequest.Builder", "Banner ads must use the reviewed request path.");
for (const targetingApi of [
  "addKeyword",
  "setContentUrl",
  "setPublisherProvidedId",
  "customTargeting",
  "networkExtras",
  "neighboringContentUrls",
]) {
  check(!bannerAd.includes(targetingApi), `Banner request added unreviewed targeting data: ${targetingApi}`);
}

const transmissionSources = [
  ...filesBelow("app/src/main/java/com/mochisofts/mata/data/ads", ".kt"),
  ...filesBelow("app/src/main/java/com/mochisofts/mata/data/holiday", ".kt"),
].map((file) => fs.readFileSync(file, "utf8")).join("\n");
check(
  !/import com\.mochisofts\.mata\.domain\.model\.(?:Todo|Category|Archived)/.test(transmissionSources),
  "An external transmission implementation imports user TODO/category data.",
);
check(
  !/\b(?:todo|category)\.(?:title|description|name)\b/i.test(transmissionSources),
  "An external transmission implementation accesses user-entered TODO/category text.",
);
checkIncludes(diagnostics, "Fixed event codes are the only descriptions", "Diagnostic logging must remain fixed-field only.");
check(!/data class DiagnosticEvent\([\s\S]*?val (?:message|description|throwable):/.test(diagnostics), "Diagnostic events gained free-form content.");

const documents = {
  dataSafety: read(".agents/non-functional-specs/release-specs/data-safety-declaration.md"),
  playSubmission: read(".agents/non-functional-specs/release-specs/play-console-submission.md"),
  storeListing: read(".agents/non-functional-specs/release-specs/store-listing-copy-and-assets.md"),
  externalSpec: read(".agents/non-functional-specs/legal-specs/external-transmission.md"),
  privacyHtml: read("legal-site/mata/privacy/index.html"),
  externalHtml: read("legal-site/mata/external-transmission/index.html"),
};
for (const [name, document] of Object.entries(documents)) {
  checkIncludes(document, "Google", `${name} no longer discloses Google processing.`);
  checkIncludes(document, "広告", `${name} no longer discloses advertising.`);
}
for (const [name, document] of Object.entries({
  externalSpec: documents.externalSpec,
  privacyHtml: documents.privacyHtml,
  externalHtml: documents.externalHtml,
})) {
  for (const service of ["Google Mobile Ads", "User Messaging Platform", "Holidays JP", "Google Play", "GitHub Pages"]) {
    checkIncludes(document, service, `${name} no longer discloses ${service}.`);
  }
  checkIncludes(document, "TODO", `${name} no longer explains TODO data handling.`);
}
for (const dataType of [
  "おおよその位置情報",
  "アプリの操作",
  "診断情報",
  "デバイスまたはその他のID",
]) {
  checkIncludes(documents.dataSafety, dataType, `Data safety declaration is missing ${dataType}.`);
}
checkIncludes(documents.dataSafety, "GMA Next-Gen SDK 1.4.0", "Data safety GMA version is stale.");
checkIncludes(documents.dataSafety, "UMP 4.0.0", "Data safety UMP version is stale.");
checkIncludes(documents.playSubmission, "| 広告 | はい、広告を含む |", "Play ads declaration changed.");
checkIncludes(documents.playSubmission, "| 広告ID | はい |", "Play advertising ID declaration changed.");
checkIncludes(documents.storeListing, "| アプリ内購入 | なし |", "Store purchase declaration changed.");
checkIncludes(documents.storeListing, "バナー広告が表示されます", "Store advertising disclosure changed.");

if (failures.length > 0) {
  console.error("External data contract verification failed:");
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log("External data contract verification passed.");
console.log(`- Direct runtime dependencies reviewed: ${actualRuntimeLibraries.length}`);
console.log(`- Source permissions reviewed: ${sourcePermissions.length}`);
console.log(`- Fixed application network endpoints reviewed: ${kotlinUrls.length}`);
console.log("- External SDKs: GMA Next-Gen 1.4.0 and UMP 4.0.0");
console.log("- User-entered TODO/category text is not connected to ads, holiday, or diagnostic paths");
