import { NativeEventEmitter, NativeModules, Platform } from 'react-native';
import type {
  ApkValidationResult,
  CurrentVersionInfo,
  DownloadProgress,
  DownloadResult,
  InstallResult,
} from '../../types/update';

type AppUpdateNativeModule = {
  getCurrentVersionInfo: () => Promise<CurrentVersionInfo>;
  canRequestPackageInstalls: () => Promise<boolean>;
  openUnknownAppSourcesSettings: () => Promise<void>;
  downloadApk: (options: { url: string; fileName: string; expectedSha256?: string | null }) => Promise<DownloadResult>;
  validateApk: (filePath: string, expectedPackageName: string, minimumVersionCode: number) => Promise<ApkValidationResult>;
  installApk: (filePath: string) => Promise<InstallResult>;
};

const nativeModule = NativeModules.AppUpdateModule as AppUpdateNativeModule | undefined;
const nativeEmitter = nativeModule ? new NativeEventEmitter(NativeModules.AppUpdateModule) : null;

function assertAndroid() {
  if (Platform.OS !== 'android' || !nativeModule) {
    throw new Error('In-app updates are only supported on Android devices.');
  }
}

export function isAndroidUpdateSupported() {
  return Platform.OS === 'android' && !!nativeModule;
}

export async function getCurrentVersionInfo() {
  assertAndroid();
  return nativeModule!.getCurrentVersionInfo();
}

export async function canRequestPackageInstalls() {
  assertAndroid();
  return nativeModule!.canRequestPackageInstalls();
}

export async function openUnknownAppSourcesSettings() {
  assertAndroid();
  return nativeModule!.openUnknownAppSourcesSettings();
}

export async function downloadApk(options: { url: string; fileName: string; expectedSha256?: string | null }) {
  assertAndroid();
  return nativeModule!.downloadApk(options);
}

export async function validateApk(filePath: string, expectedPackageName: string, minimumVersionCode: number) {
  assertAndroid();
  return nativeModule!.validateApk(filePath, expectedPackageName, minimumVersionCode);
}

export async function installApk(filePath: string) {
  assertAndroid();
  return nativeModule!.installApk(filePath);
}

export function subscribeToDownloadProgress(listener: (progress: DownloadProgress) => void) {
  if (!nativeEmitter) return () => {};
  const subscription = nativeEmitter.addListener('appUpdateDownloadProgress', listener);
  return () => subscription.remove();
}
