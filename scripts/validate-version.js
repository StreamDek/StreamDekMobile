const fs = require('fs');
const path = require('path');

const expected = process.argv[2]?.replace(/^v/, '') || null;
const pkg = JSON.parse(fs.readFileSync(path.resolve(__dirname, '..', 'package.json'), 'utf8'));
if (!pkg.version) throw new Error('package.json is missing version');
if (expected && expected !== pkg.version) {
  throw new Error('release version ' + expected + ' does not match package.json ' + pkg.version);
}
if (!/^\d+\.\d+\.\d+$/.test(pkg.version)) {
  throw new Error('version ' + pkg.version + ' is not semantic X.Y.Z');
}
console.log('[validate-version] mobile version ' + pkg.version + ' is aligned.');