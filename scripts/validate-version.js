const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const expected = process.argv[2]?.replace(/^v/, '') || null;

function readJson(relativePath) {
  return JSON.parse(fs.readFileSync(path.join(root, relativePath), 'utf8'));
}

function fail(message) {
  console.error(`[validate-version] ${message}`);
  process.exit(1);
}

const pkg = readJson('package.json');
const lock = readJson('package-lock.json');
const appJson = readJson('app.json');
const expoConfig = require(path.join(root, 'app.config.js'))().expo;

const packageVersion = pkg.version;
const lockVersion = lock.version;
const lockRootVersion = lock.packages?.['']?.version;
const expoVersion = expoConfig.version;
const staticExpoVersion = appJson.expo?.version;

if (!packageVersion) fail('package.json is missing version.');
if (lockVersion !== packageVersion) {
  fail(`package-lock.json version ${lockVersion} does not match package.json ${packageVersion}.`);
}
if (lockRootVersion !== packageVersion) {
  fail(`package-lock root version ${lockRootVersion} does not match package.json ${packageVersion}.`);
}
if (expoVersion !== packageVersion) {
  fail(`app.config.js resolved Expo version ${expoVersion} does not match package.json ${packageVersion}.`);
}
if (staticExpoVersion && staticExpoVersion !== packageVersion) {
  fail(`app.json static Expo version ${staticExpoVersion} does not match package.json ${packageVersion}.`);
}
if (expected && expected !== packageVersion) {
  fail(`release tag version ${expected} does not match package.json ${packageVersion}.`);
}

console.log(`[validate-version] mobile version ${packageVersion} is aligned.`);
