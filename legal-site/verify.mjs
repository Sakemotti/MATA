import {
  existsSync,
  readFileSync,
  readdirSync,
  statSync,
} from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const siteRoot = dirname(fileURLToPath(import.meta.url));
const errors = [];
const requiredPages = [
  "index.html",
  "mata/privacy/index.html",
  "mata/terms/index.html",
  "mata/commercial-transactions/index.html",
  "mata/external-transmission/index.html",
];
const requiredRoutes = [
  "/mata/privacy",
  "/mata/terms",
  "/mata/commercial-transactions",
  "/mata/external-transmission",
];
const requiredUrls = [
  "https://mochisofts.com/",
  ...requiredRoutes.map((route) => `https://mochisofts.com${route}`),
];

function collectHtmlFiles(directory) {
  return readdirSync(directory).flatMap((name) => {
    const path = join(directory, name);
    return statSync(path).isDirectory()
      ? collectHtmlFiles(path)
      : path.endsWith(".html")
        ? [path]
        : [];
  });
}

function resolveSiteReference(reference) {
  const path = resolve(siteRoot, reference.replace(/^\//, ""));
  if (existsSync(path) && statSync(path).isDirectory()) {
    return join(path, "index.html");
  }
  return path;
}

for (const relativePath of requiredPages) {
  if (!existsSync(join(siteRoot, relativePath))) {
    errors.push(`Required page is missing: ${relativePath}`);
  }
}

if (existsSync(join(siteRoot, "app-ads.txt"))) {
  errors.push("app-ads.txt must not be published before the AdMob publisher ID is set.");
}

for (const file of collectHtmlFiles(siteRoot)) {
  const relativePath = file.slice(siteRoot.length + 1).replaceAll("\\", "/");
  const html = readFileSync(file, "utf8");

  if (!/<html\s+lang="ja">/i.test(html)) {
    errors.push(`${relativePath}: html lang must be ja.`);
  }
  if (!/<meta\s+name="viewport"/i.test(html)) {
    errors.push(`${relativePath}: viewport metadata is missing.`);
  }
  if (!/Content-Security-Policy/i.test(html)) {
    errors.push(`${relativePath}: Content-Security-Policy metadata is missing.`);
  }
  if ((html.match(/<h1(?:\s|>)/gi) ?? []).length !== 1) {
    errors.push(`${relativePath}: exactly one h1 is required.`);
  }

  for (const route of requiredRoutes) {
    if (!html.includes(`href="${route}"`)) {
      errors.push(`${relativePath}: navigation link is missing: ${route}`);
    }
  }

  for (const match of html.matchAll(/(?:href|src)="([^"]+)"/g)) {
    const reference = match[1];
    if (!reference.startsWith("/")) continue;
    if (!existsSync(resolveSiteReference(reference))) {
      errors.push(`${relativePath}: internal reference is missing: ${reference}`);
    }
  }
}

const sitemap = readFileSync(join(siteRoot, "sitemap.xml"), "utf8");
for (const url of requiredUrls) {
  if (!sitemap.includes(`<loc>${url}</loc>`)) {
    errors.push(`sitemap.xml: URL is missing: ${url}`);
  }
}
if ((sitemap.match(/<loc>/g) ?? []).length !== requiredUrls.length) {
  errors.push(`sitemap.xml: expected ${requiredUrls.length} URLs.`);
}

const searchableFiles = [
  ...requiredPages.map((path) => join(siteRoot, path)),
  join(siteRoot, "README.md"),
];
for (const file of searchableFiles) {
  const text = readFileSync(file, "utf8");
  if (text.includes("support@mochisofts.com")) {
    errors.push(`${file}: obsolete contact address remains.`);
  }
  if (/\[(初回公開日|公開前に確定)[^\]]*\]/.test(text)) {
    errors.push(`${file}: publication placeholder remains.`);
  }
}

for (const relativePath of requiredPages.slice(1)) {
  const html = readFileSync(join(siteRoot, relativePath), "utf8");
  if (!html.includes("com.mochisofts@gmail.com")) {
    errors.push(`${relativePath}: contact address is missing.`);
  }
}

if (errors.length > 0) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log(`Verified ${requiredPages.length} legal pages and ${requiredUrls.length} sitemap URLs.`);
}
