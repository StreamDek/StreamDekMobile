import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AppState, Platform } from 'react-native';
import { Storage } from '../utils/storage';
import type { CurrentVersionInfo, DownloadProgress, UpdateManifest } from '../types/update';
import { checkForAndroidUpdate } from '../services/update/updateChecker';
import { ensureInstallPermission } from '../services/update/permissionHandler';
import { downloadAndValidateRelease } from '../services/update/downloader';
import { launchUpdateInstaller } from '../services/update/installer';
import { validateApk } from '../services/update/nativeBridge';

const AUTO_UPDATE_CHECKS_KEY = 'streamdek_auto_update_checks';
const FOREGROUND_CHECK_INTERVAL_MS = 1000 * 60 * 60 * 6;

type AppUpdateContextValue = {
  autoCheckEnabled: boolean;
  setAutoCheckEnabled: (value: boolean) => Promise<void>;
  currentVersion: CurrentVersionInfo | null;
  availableRelease: UpdateManifest | null;
  isMandatory: boolean;
  isChecking: boolean;
  isDownloading: boolean;
  promptVisible: boolean;
  progress: DownloadProgress | null;
  statusMessage: string | null;
  errorMessage: string | null;
  unsupported: boolean;
  checkNow: () => Promise<void>;
  dismissPrompt: () => void;
  startUpdate: () => Promise<void>;
};

const AppUpdateContext = createContext<AppUpdateContextValue>({
  autoCheckEnabled: true,
  setAutoCheckEnabled: async () => {},
  currentVersion: null,
  availableRelease: null,
  isMandatory: false,
  isChecking: false,
  isDownloading: false,
  promptVisible: false,
  progress: null,
  statusMessage: null,
  errorMessage: null,
  unsupported: false,
  checkNow: async () => {},
  dismissPrompt: () => {},
  startUpdate: async () => {},
});

function describeUpdateError(error: unknown): string {
  const code = typeof error === 'object' && error && 'code' in error ? String((error as { code?: string }).code ?? '') : '';
  switch (code) {
    case 'DOWNLOAD_NO_INTERNET':
      return 'No internet connection. Reconnect and try the update again.';
    case 'DOWNLOAD_TIMEOUT':
      return 'The update download timed out. Try again on a stronger connection.';
    case 'DOWNLOAD_INSUFFICIENT_STORAGE':
      return 'Not enough free storage is available for this update.';
    case 'INSTALLER_UNAVAILABLE':
      return 'This device does not expose a package installer for manual APK updates.';
    case 'APK_INVALID':
    case 'APK_VALIDATION_FAILED':
      return 'The downloaded APK was invalid and was not installed.';
    default:
      return error instanceof Error ? error.message : 'The update could not be completed.';
  }
}

function describeUpdateCheckError(message: string): string {
  if (
    message === 'UPDATE_CHECK_UNAVAILABLE'
    || /update_manifest/i.test(message)
    || /release endpoint/i.test(message)
    || /update server returned/i.test(message)
  ) {
    return 'Unable to check for updates right now. Please try again in a little while.';
  }

  if (/network request failed/i.test(message) || /fetch/i.test(message)) {
    return 'Unable to reach the update service right now. Check your connection and try again.';
  }

  return 'Unable to check for updates right now. Please try again in a little while.';
}

export function AppUpdateProvider({ children }: { children: React.ReactNode }) {
  const [autoCheckEnabled, setAutoCheckEnabledState] = useState(true);
  const [settingsReady, setSettingsReady] = useState(false);
  const [currentVersion, setCurrentVersion] = useState<CurrentVersionInfo | null>(null);
  const [availableRelease, setAvailableRelease] = useState<UpdateManifest | null>(null);
  const [isMandatory, setIsMandatory] = useState(false);
  const [isChecking, setIsChecking] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [promptVisible, setPromptVisible] = useState(false);
  const [progress, setProgress] = useState<DownloadProgress | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [unsupported, setUnsupported] = useState(Platform.OS !== 'android');
  const awaitingPermissionResumeRef = useRef(false);
  const lastForegroundCheckAtRef = useRef(0);
  const checkingRef = useRef(false);
  const mountedRef = useRef(true);
  // Re-entry guard: tapping "Update Now" while a download/install is already
  // in flight must not start a second download.
  const updateInFlightRef = useRef(false);
  // The validated APK from a previous attempt â€” reused when the installer was
  // cancelled or the user retries, instead of downloading the file again.
  const downloadedApkRef = useRef<{ versionCode: number; filePath: string } | null>(null);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    void Storage.getItem(AUTO_UPDATE_CHECKS_KEY)
      .then((raw) => {
        if (cancelled) return;
        setAutoCheckEnabledState(raw == null ? true : raw !== 'false');
      })
      .finally(() => {
        if (!cancelled) {
          setSettingsReady(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const performCheck = useCallback(async (options?: { manual?: boolean; essentialOnly?: boolean }) => {
    if (checkingRef.current) return;
    checkingRef.current = true;
    setIsChecking(true);
    if (options?.manual) {
      setErrorMessage(null);
      setStatusMessage('Checking for updates...');
    }

    try {
      const outcome = await checkForAndroidUpdate();
      if (!mountedRef.current) return;

      if (outcome.status === 'unsupported-device') {
        setUnsupported(true);
        setStatusMessage('In-app updates are only available on Android devices.');
        setAvailableRelease(null);
        setPromptVisible(false);
        return;
      }

      if (outcome.status === 'error') {
        setErrorMessage(describeUpdateCheckError(outcome.message));
        if (options?.manual) {
          setStatusMessage(null);
        }
        return;
      }

      setUnsupported(false);
      setCurrentVersion(outcome.current);

      if (outcome.status === 'up-to-date') {
        setAvailableRelease(null);
        setIsMandatory(false);
        setPromptVisible(false);
        setErrorMessage(null);
        setStatusMessage(options?.manual ? 'You are already on the latest version.' : null);
        lastForegroundCheckAtRef.current = Date.now();
        return;
      }

      const shouldShowOptionalPrompt = options?.manual || (!options?.essentialOnly && autoCheckEnabled);
      setAvailableRelease(outcome.release);
      setIsMandatory(outcome.isMandatory);
      setErrorMessage(null);
      setStatusMessage(`Version ${outcome.release.versionName} is available.`);
      setPromptVisible(outcome.isMandatory || shouldShowOptionalPrompt);
      lastForegroundCheckAtRef.current = Date.now();
    } finally {
      checkingRef.current = false;
      if (mountedRef.current) {
        setIsChecking(false);
      }
    }
  }, [autoCheckEnabled]);

  useEffect(() => {
    if (!settingsReady) return;
    void performCheck({ essentialOnly: !autoCheckEnabled });
  }, [autoCheckEnabled, performCheck, settingsReady]);

  const setAutoCheckEnabled = useCallback(async (value: boolean) => {
    setAutoCheckEnabledState(value);
    void Storage.setItem(AUTO_UPDATE_CHECKS_KEY, value ? 'true' : 'false').catch(() => {});
  }, []);

  const dismissPrompt = useCallback(() => {
    if (isMandatory) return;
    setPromptVisible(false);
  }, [isMandatory]);

  const checkNow = useCallback(async () => {
    await performCheck({ manual: true, essentialOnly: false });
  }, [performCheck]);

  const startUpdate = useCallback(async () => {
    if (!availableRelease || Platform.OS !== 'android') return;
    if (updateInFlightRef.current) return;
    updateInFlightRef.current = true;

    setErrorMessage(null);
    setPromptVisible(true);
    setStatusMessage('Preparing the update...');

    try {
      const permission = await ensureInstallPermission();
      if (!permission.granted) {
        awaitingPermissionResumeRef.current = true;
        setStatusMessage('Allow installs from StreamDek, then return here to continue.');
        return;
      }

      // Reuse the APK from a previous attempt when it's still valid â€” the user
      // shouldn't download the same release twice just because the installer
      // was dismissed.
      let apkFilePath: string | null = null;
      const cachedApk = downloadedApkRef.current;
      if (cachedApk && cachedApk.versionCode === availableRelease.versionCode) {
        const validation = await validateApk(
          cachedApk.filePath,
          availableRelease.packageName,
          availableRelease.versionCode,
        ).catch(() => null);
        if (validation?.isValid) {
          apkFilePath = cachedApk.filePath;
        } else {
          downloadedApkRef.current = null;
        }
      }

      if (apkFilePath) {
        setStatusMessage('Update already downloaded. Opening the installer...');
      } else {
        setIsDownloading(true);
        setProgress(null);
        setStatusMessage(`Downloading version ${availableRelease.versionName}...`);

        const downloadResult = await downloadAndValidateRelease(availableRelease, setProgress);
        apkFilePath = downloadResult.filePath;
        downloadedApkRef.current = { versionCode: availableRelease.versionCode, filePath: apkFilePath };
        setStatusMessage('Download complete. Opening the installer...');
      }

      const installResult = await launchUpdateInstaller(apkFilePath);
      setIsDownloading(false);
      setProgress(null);

      if (installResult.status === 'installed') {
        downloadedApkRef.current = null;
        setStatusMessage(`StreamDek ${availableRelease.versionName} was installed.`);
        setPromptVisible(false);
        return;
      }

      if (installResult.status === 'cancelled') {
        setErrorMessage('The update install was canceled.');
        setStatusMessage(null);
        setPromptVisible(true);
        return;
      }

      setErrorMessage('The Android package installer could not complete the update.');
      setStatusMessage(null);
      setPromptVisible(true);
    } catch (error) {
      setIsDownloading(false);
      setProgress(null);
      setStatusMessage(null);
      setErrorMessage(describeUpdateError(error));
      setPromptVisible(true);
    } finally {
      updateInFlightRef.current = false;
    }
  }, [availableRelease]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      if (nextState !== 'active') return;

      if (awaitingPermissionResumeRef.current) {
        awaitingPermissionResumeRef.current = false;
        void startUpdate();
        return;
      }

      if (!settingsReady) return;
      const shouldCheck = Date.now() - lastForegroundCheckAtRef.current >= FOREGROUND_CHECK_INTERVAL_MS;
      if (shouldCheck) {
        void performCheck({ essentialOnly: !autoCheckEnabled });
      }
    });
    return () => subscription.remove();
  }, [autoCheckEnabled, performCheck, settingsReady, startUpdate]);

  const value = useMemo<AppUpdateContextValue>(() => ({
    autoCheckEnabled,
    setAutoCheckEnabled,
    currentVersion,
    availableRelease,
    isMandatory,
    isChecking,
    isDownloading,
    promptVisible,
    progress,
    statusMessage,
    errorMessage,
    unsupported,
    checkNow,
    dismissPrompt,
    startUpdate,
  }), [
    autoCheckEnabled,
    setAutoCheckEnabled,
    currentVersion,
    availableRelease,
    isMandatory,
    isChecking,
    isDownloading,
    promptVisible,
    progress,
    statusMessage,
    errorMessage,
    unsupported,
    checkNow,
    dismissPrompt,
    startUpdate,
  ]);

  return (
    <AppUpdateContext.Provider value={value}>
      {children}
    </AppUpdateContext.Provider>
  );
}

export function useAppUpdate() {
  return useContext(AppUpdateContext);
}
