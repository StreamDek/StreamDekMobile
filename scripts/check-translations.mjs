#!/usr/bin/env node
/**
 * Keeps the localisation resources honest.
 *
 * Three things can drift apart once a language exists in more than one place - the language list in
 * `AppLanguage.kt`, the `locale-config` the platform reads, and the `values-<tag>` folders that
 * actually hold the strings - and every way they can drift produces a silent failure rather than a
 * loud one: a language offered in Settings that has no strings behind it, a translation folder no
 * build ever selects, or a placeholder that crashes only on the one screen nobody opened in Polish.
 *
 * What this does NOT do is demand that every locale translate every key. `values/` is English and is
 * the platform's fallback for all of them, so a missing key resolves to readable English rather than
 * to a blank label - which is what makes it safe to ship a language before it is finished. Untranslated
 * keys are reported as coverage, not as failures.
 *
 *   node scripts/check-translations.mjs           report and fail on real problems
 *   node scripts/check-translations.mjs --verbose  also list every untranslated key
 */
import { readFileSync, existsSync, readdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const res = join(root, "android", "app", "src", "main", "res");
const verbose = process.argv.includes("--verbose");

const problems = [];
const fail = (message) => problems.push(message);

// --- the three lists that must agree -------------------------------------------------------------

const appLanguageKt = (() => {
  const roots = [join(root, "android", "app", "src", "main", "java")];
  const stack = [...roots];
  while (stack.length) {
    const dir = stack.pop();
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name);
      if (entry.isDirectory()) stack.push(path);
      else if (entry.name === "AppLanguage.kt") return path;
    }
  }
  return null;
})();

if (!appLanguageKt) {
  fail("AppLanguage.kt not found - the language list has no source of truth");
}

/** Tags declared by the enum, in declaration order (which is the order the selector shows). */
const enumTags = appLanguageKt
  ? [...readFileSync(appLanguageKt, "utf8").matchAll(/^\s{4}[A-Z][A-Za-z]*\("([a-z]{2})",/gm)].map((m) => m[1])
  : [];

const localeConfigPath = join(res, "xml", "locales_config.xml");
const configTags = existsSync(localeConfigPath)
  ? [...readFileSync(localeConfigPath, "utf8").matchAll(/android:name="([a-zA-Z-]+)"/g)].map((m) => m[1])
  : (fail("res/xml/locales_config.xml is missing"), []);

const folderTags = readdirSync(res)
  .filter((name) => name === "values" || /^values-[a-z]{2}$/.test(name))
  .map((name) => (name === "values" ? "en" : name.slice("values-".length)));

const missingFolders = enumTags.filter((tag) => !folderTags.includes(tag));
if (missingFolders.length) {
  fail(`AppLanguage.kt offers ${missingFolders.join(", ")} but there is no values-<tag>/strings.xml for them`);
}
const missingFromConfig = enumTags.filter((tag) => !configTags.includes(tag));
if (missingFromConfig.length) {
  fail(`AppLanguage.kt offers ${missingFromConfig.join(", ")} but locales_config.xml does not list them`);
}
const strayInConfig = configTags.filter((tag) => !enumTags.includes(tag));
if (strayInConfig.length) {
  fail(`locales_config.xml lists ${strayInConfig.join(", ")}, which AppLanguage.kt does not offer`);
}

// --- the strings themselves ----------------------------------------------------------------------

/** Positional placeholders, which must survive translation exactly - order may change, the set may not. */
const placeholders = (text) => [...text.matchAll(/%(\d+\$)?[sdf]/g)].map((m) => m[0]).sort();

function readStrings(tag) {
  const path = join(res, tag === "en" ? "values" : `values-${tag}`, "strings.xml");
  if (!existsSync(path)) return null;
  const xml = readFileSync(path, "utf8");
  const strings = new Map();
  for (const m of xml.matchAll(/<string name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g)) {
    strings.set(m[1], { text: m[3], attrs: m[2] });
  }
  const plurals = new Map();
  for (const m of xml.matchAll(/<plurals name="([^"]+)">([\s\S]*?)<\/plurals>/g)) {
    const items = new Map();
    for (const item of m[2].matchAll(/<item quantity="([a-z]+)">([\s\S]*?)<\/item>/g)) {
      items.set(item[1], item[2]);
    }
    plurals.set(m[1], items);
  }
  return { path, strings, plurals };
}

const english = readStrings("en");
if (!english) {
  fail("res/values/strings.xml is missing - there is no fallback for anything");
}

// Anything a translation must never carry: an unescaped apostrophe or double quote is an aapt error,
// and is very easy to introduce when the English source had none.
const unescaped = /(?<!\\)['"]/;

const coverage = [];
for (const tag of enumTags) {
  if (tag === "en" || !english) continue;
  const translated = readStrings(tag);
  if (!translated) {
    fail(`values-${tag}/strings.xml is missing`);
    continue;
  }

  for (const [key, { text }] of translated.strings) {
    if (!english.strings.has(key)) {
      fail(`${tag}: "${key}" is translated but does not exist in values/strings.xml - a renamed or deleted key`);
      continue;
    }
    if (unescaped.test(text)) {
      fail(`${tag}: "${key}" contains an unescaped apostrophe or quote: ${text}`);
    }
    const want = placeholders(english.strings.get(key).text).join(",");
    const got = placeholders(text).join(",");
    if (want !== got) {
      fail(`${tag}: "${key}" has placeholders [${got}] where English has [${want}]`);
    }
  }

  for (const [key, items] of translated.plurals) {
    if (!english.plurals.has(key)) {
      fail(`${tag}: plural "${key}" does not exist in values/strings.xml`);
      continue;
    }
    // "other" is the category the platform falls back to; without it a count with no matching
    // category resolves to nothing at all.
    if (!items.has("other")) {
      fail(`${tag}: plural "${key}" has no <item quantity="other">`);
    }
    for (const [quantity, text] of items) {
      if (unescaped.test(text)) {
        fail(`${tag}: plural "${key}" (${quantity}) contains an unescaped apostrophe or quote`);
      }
    }
  }

  // translatable="false" is a statement that the string is the same in every language - a brand
  // name, a protocol token - so it is not missing coverage and must not be counted as such.
  const untranslated = [...english.strings.keys()].filter(
    (key) => !translated.strings.has(key) && !/translatable="false"/.test(english.strings.get(key).attrs),
  );
  const untranslatedPlurals = [...english.plurals.keys()].filter((key) => !translated.plurals.has(key));
  coverage.push({ tag, untranslated, untranslatedPlurals, total: english.strings.size + english.plurals.size });
}

// --- report ---------------------------------------------------------------------------------------

if (english) {
  console.log(`English source: ${english.strings.size} strings, ${english.plurals.size} plurals\n`);
  for (const { tag, untranslated, untranslatedPlurals, total } of coverage) {
    const done = total - untranslated.length - untranslatedPlurals.length;
    const percent = total ? Math.round((done / total) * 100) : 100;
    console.log(`  ${tag}  ${String(percent).padStart(3)}%  ${done}/${total} translated`);
    if (verbose && (untranslated.length || untranslatedPlurals.length)) {
      for (const key of [...untranslated, ...untranslatedPlurals]) console.log(`        - ${key}`);
    }
  }
  console.log("");
}

if (problems.length) {
  console.error(`${problems.length} problem(s):\n`);
  for (const problem of problems) console.error(`  ${problem}`);
  process.exit(1);
}
console.log("Localisation resources are consistent.");
