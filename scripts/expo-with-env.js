const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const projectRoot = path.resolve(__dirname, '..');
const dotenvFiles = ['.env', '.env.local', '../.env'];
const validModes = new Set(['development', 'production', 'test']);

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

    if (!line || line.startsWith('#')) {
      continue;
    }

    const separatorIndex = line.indexOf('=');

    if (separatorIndex === -1) {
      continue;
    }

    const key = line.slice(0, separatorIndex).trim();
    const value = stripWrappingQuotes(line.slice(separatorIndex + 1).trim());

    if (!key) {
      continue;
    }

    process.env[key] = value;
  }
}

function extractMode(args) {
  const nextArgs = [];
  let mode;

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];

    if (arg === '--mode') {
      mode = args[index + 1];
      index += 1;
      continue;
    }

    if (arg.startsWith('--mode=')) {
      mode = arg.slice('--mode='.length);
      continue;
    }

    nextArgs.push(arg);
  }

  return { mode, nextArgs };
}

for (const dotenvFile of dotenvFiles) {
  loadDotenvFile(path.join(projectRoot, dotenvFile));
}

const rootApiUrl = process.env.STREAMDEK_API_URL;

const [, , ...cliArgs] = process.argv;
const { mode: requestedMode, nextArgs } = extractMode(cliArgs);
const [expoCommand = 'start', ...expoArgs] = nextArgs;
const inferredMode =
  requestedMode ||
  process.env.NODE_ENV ||
  process.env.EXPO_PUBLIC_APP_ENV ||
  'development';

const mode = validModes.has(inferredMode) ? inferredMode : 'development';

process.env.NODE_ENV = mode;
process.env.EXPO_PUBLIC_APP_ENV = requestedMode
  ? mode
  : process.env.EXPO_PUBLIC_APP_ENV || mode;

if (rootApiUrl) {
  process.env.EXPO_PUBLIC_API_BASE_URL = rootApiUrl;
} else {
  console.error(
    '[expo-with-env] Missing STREAMDEK_API_URL. Add it to .env or .env.local in the project root (e.g. STREAMDEK_API_URL=http://localhost:3000).'
  );
  process.exit(1);
}

const expoCliPath = require.resolve('expo/bin/cli', { paths: [projectRoot] });

console.log(
  `[expo-with-env] Starting Expo with NODE_ENV=${process.env.NODE_ENV} and EXPO_PUBLIC_APP_ENV=${process.env.EXPO_PUBLIC_APP_ENV}`
);

const child = spawn(process.execPath, [expoCliPath, expoCommand, ...expoArgs], {
  cwd: projectRoot,
  env: process.env,
  stdio: 'inherit',
});

child.on('exit', code => {
  process.exit(code ?? 0);
});

child.on('error', error => {
  console.error('[expo-with-env] Failed to start Expo CLI.');
  console.error(error);
  process.exit(1);
});
