const fs = require('fs');
const path = require('path');
const pkg = require('./package.json');

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
    return {};
  }

  const values = {};
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

    values[key] = value;
  }

  return values;
}

module.exports = () => {
  const projectRoot = __dirname;
  const rootEnv = loadDotenvFile(path.join(projectRoot, '..', '.env'));
  const localEnv = loadDotenvFile(path.join(projectRoot, '.env'));
  const localOverrideEnv = loadDotenvFile(path.join(projectRoot, '.env.local'));
  const mergedEnv = { ...rootEnv, ...localEnv, ...localOverrideEnv };
  const apiBaseUrl = mergedEnv.EXPO_PUBLIC_API_BASE_URL || mergedEnv.STREAMDEK_API_URL;

  return {
    expo: {
      name: 'StreamDek',
      slug: 'streamdek',
      scheme: 'streamdek',
      version: pkg.version,
      orientation: 'portrait',
      icon: './assets/app-logo.png',
      userInterfaceStyle: 'automatic',
      backgroundColor: '#0d0d1a',
      splash: {
        image: './assets/app-logo.png',
        resizeMode: 'contain',
        backgroundColor: '#0d0d1a',
      },
      ios: {
        supportsTablet: true,
      },
      android: {
        adaptiveIcon: {
          backgroundColor: '#0d0d1a',
          foregroundImage: './assets/app-logo.png',
        },
        predictiveBackGestureEnabled: false,
        package: 'com.anonymous.streamdekmobile',
      },
      web: {
        favicon: './assets/app-logo.png',
      },
      plugins: [
        [
          'expo-video',
          {
            supportsPictureInPicture: true,
          },
        ],
        'expo-image',
        'expo-camera',
        'react-native-google-cast',
        'expo-web-browser',
      ],
      extra: {
        ...mergedEnv,
        streamdekApiUrl: apiBaseUrl,
      },
    },
  };
};
