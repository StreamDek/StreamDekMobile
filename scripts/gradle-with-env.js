const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const projectRoot = path.resolve(__dirname, '..');
const androidDir = path.join(projectRoot, 'android');
const dotenvFiles = ['../.env', '.env', '.env.local'];

function stripWrappingQuotes(value) {
  if (
    (value.startsWith('"') && value.endsWith('"')) ||
    (value.startsWith("'") && value.endsWith("'"))
  ) {
    return value.slice(1, -1);
  }

  return value;
}

function loadDotenvFile(filePath) {
  if (!fs.existsSync(filePath)) {
    return;
  }

  const contents = fs.readFileSync(filePath, 'utf8');

  for (const rawLine of contents.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;

    const separatorIndex = line.indexOf('=');
    if (separatorIndex === -1) continue;

    const key = line.slice(0, separatorIndex).trim();
    const value = stripWrappingQuotes(line.slice(separatorIndex + 1).trim());

    if (!key) continue;
    process.env[key] = value;
  }
}

for (const dotenvFile of dotenvFiles) {
  loadDotenvFile(path.join(projectRoot, dotenvFile));
}

if (process.env.HOST_IP && !process.env.EXPO_PUBLIC_HOST_IP) {
  process.env.EXPO_PUBLIC_HOST_IP = process.env.HOST_IP;
}
if (process.env.BACKEND_PORT && !process.env.EXPO_PUBLIC_BACKEND_PORT) {
  process.env.EXPO_PUBLIC_BACKEND_PORT = process.env.BACKEND_PORT;
}
if (process.env.STREAMDEK_MOBILE_BACKEND_URL && !process.env.EXPO_PUBLIC_API_BASE_URL) {
  process.env.EXPO_PUBLIC_API_BASE_URL = process.env.STREAMDEK_MOBILE_BACKEND_URL;
}

const [, , ...cliArgs] = process.argv;
const [firstArg = 'clean', ...restArgs] = cliArgs;
const gradleExecutable = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

const child = spawn(gradleExecutable, [firstArg, ...restArgs], {
  cwd: androidDir,
  env: process.env,
  stdio: 'inherit',
  shell: process.platform === 'win32',
});

child.on('exit', code => {
  process.exit(code ?? 0);
});

child.on('error', error => {
  console.error('[gradle-with-env] Failed to start Gradle.');
  console.error(error);
  process.exit(1);
});
