import Constants from 'expo-constants';
import { Platform } from 'react-native';
import { Storage } from './storage';

const DEVICE_KEY = 'streamdek-mobile-device-id';

let sessionId = createClientId();
let cachedDeviceId: string | null = null;

export async function getMobileClientIdentityHeaders(): Promise<Record<string, string>> {
  const deviceId = await getOrCreateDeviceId();

  return {
    'x-client-session-id': sessionId,
    'x-client-device-id': deviceId,
    'x-client-name': 'StreamDek Mobile',
    'x-client-platform': Platform.OS,
    'x-device-name': getDeviceName(),
    'x-device-type': 'mobile',
    'x-app-version': String(Constants.expoConfig?.version ?? '0.0.1'),
  };
}

async function getOrCreateDeviceId(): Promise<string> {
  if (cachedDeviceId) return cachedDeviceId;

  const current = await Storage.getItem(DEVICE_KEY);
  if (current && current.trim()) {
    cachedDeviceId = current;
    return current;
  }

  const next = createClientId();
  await Storage.setItem(DEVICE_KEY, next);
  cachedDeviceId = next;
  return next;
}

function getDeviceName(): string {
  return (
    Constants.deviceName
    ?? Constants.expoConfig?.name
    ?? `StreamDek ${Platform.OS}`
  );
}

function createClientId(): string {
  const runtimeCrypto = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto;
  if (runtimeCrypto?.randomUUID) {
    return runtimeCrypto.randomUUID();
  }
  return `streamdek-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
}
