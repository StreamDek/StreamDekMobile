import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { AppState, NativeModules, Platform } from 'react-native';
import { Storage } from '../utils/storage';
import {
  buildTorrentServerUrl,
  DEFAULT_TORRENT_SERVER_CONFIG,
  normalizeTorrentServerConfig,
  StreamingMode,
  TORRENT_SERVER_SETTINGS_KEY,
  TorrentServerConfig,
  TorrentServerStatus,
} from '../types/torrentServer';

type NativeTorrentServerStatus = {
  isOnline?: boolean;
  isForeground?: boolean;
  requestedForeground?: boolean;
  port?: number;
  streamingMode?: StreamingMode;
  url?: string;
  cacheDirectory?: string;
  torrentStoreDirectory?: string;
  cacheUsageBytes?: number;
  profile?: TorrentServerConfig['profile'];
  cacheSizeGb?: TorrentServerConfig['cacheSizeGb'];
  recoveryMode?: TorrentServerStatus['recoveryMode'];
  lastStartupError?: string;
  foregroundDowngradeReason?: string;
  lifecycleState?: string;
};

type NativeTorrentServerModuleType = {
  ensureStarted: (config: TorrentServerConfig) => Promise<NativeTorrentServerStatus>;
  updateConfig: (config: TorrentServerConfig) => Promise<NativeTorrentServerStatus>;
  getStatus: () => Promise<NativeTorrentServerStatus>;
  stopServer?: () => Promise<NativeTorrentServerStatus>;
};

const NativeTorrentServerModule = NativeModules.TorrentServerModule as NativeTorrentServerModuleType | undefined;

interface TorrentServerContextValue {
  config: TorrentServerConfig;
  status: TorrentServerStatus;
  isReady: boolean;
  updateConfig: (next: Partial<TorrentServerConfig>) => Promise<void>;
  refreshStatus: () => Promise<void>;
  ensureOnline: () => Promise<void>;
}

const initialStatus: TorrentServerStatus = {
  isOnline: false,
  isForeground: false,
  requestedForeground: false,
  port: DEFAULT_TORRENT_SERVER_CONFIG.port,
  url: buildTorrentServerUrl(DEFAULT_TORRENT_SERVER_CONFIG.port),
  cacheDirectory: '',
  torrentStoreDirectory: '',
  cacheUsageBytes: 0,
  profile: DEFAULT_TORRENT_SERVER_CONFIG.profile,
  cacheSizeGb: DEFAULT_TORRENT_SERVER_CONFIG.cacheSizeGb,
  recoveryMode: 'idle',
  lastStartupError: '',
  foregroundDowngradeReason: '',
  lifecycleState: 'idle',
};

const TorrentServerContext = createContext<TorrentServerContextValue>({
  config: DEFAULT_TORRENT_SERVER_CONFIG,
  status: initialStatus,
  isReady: false,
  updateConfig: async () => {},
  refreshStatus: async () => {},
  ensureOnline: async () => {},
});

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function mergeStatus(
  currentConfig: TorrentServerConfig,
  nativeStatus?: NativeTorrentServerStatus | null,
): TorrentServerStatus {
  const port = nativeStatus?.port ?? currentConfig.port;
  return {
    isOnline: !!nativeStatus?.isOnline,
    isForeground: nativeStatus?.isForeground ?? currentConfig.runAsForegroundService,
    requestedForeground: nativeStatus?.requestedForeground ?? currentConfig.runAsForegroundService,
    port,
    url: nativeStatus?.url ?? buildTorrentServerUrl(port),
    cacheDirectory: nativeStatus?.cacheDirectory ?? '',
    torrentStoreDirectory: nativeStatus?.torrentStoreDirectory ?? '',
    cacheUsageBytes: nativeStatus?.cacheUsageBytes ?? 0,
    profile: nativeStatus?.profile ?? currentConfig.profile,
    cacheSizeGb: nativeStatus?.cacheSizeGb ?? currentConfig.cacheSizeGb,
    recoveryMode: nativeStatus?.recoveryMode ?? (nativeStatus?.isOnline ? 'running' : 'idle'),
    lastStartupError: nativeStatus?.lastStartupError ?? '',
    foregroundDowngradeReason: nativeStatus?.foregroundDowngradeReason ?? '',
    lifecycleState: nativeStatus?.lifecycleState ?? 'idle',
  };
}

export const TorrentServerProvider = ({ children }: { children: React.ReactNode }) => {
  const [config, setConfig] = useState<TorrentServerConfig>(DEFAULT_TORRENT_SERVER_CONFIG);
  const [status, setStatus] = useState<TorrentServerStatus>(initialStatus);
  const [isReady, setIsReady] = useState(false);
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const refreshStatus = useCallback(async () => {
    if (Platform.OS !== 'android' || !NativeTorrentServerModule?.getStatus) {
      setStatus(prev => ({
        ...prev,
        isOnline: false,
        recoveryMode: 'idle',
        port: config.port,
        url: buildTorrentServerUrl(config.port),
        cacheDirectory: '',
        torrentStoreDirectory: '',
        cacheUsageBytes: 0,
        profile: config.profile,
        cacheSizeGb: config.cacheSizeGb,
        isForeground: config.runAsForegroundService,
        requestedForeground: config.runAsForegroundService,
        lastStartupError: '',
        foregroundDowngradeReason: '',
        lifecycleState: 'idle',
      }));
      return;
    }

    try {
      const nextStatus = await NativeTorrentServerModule.getStatus();
      setStatus(mergeStatus(config, nextStatus));
    } catch {
      setStatus(prev => ({
        ...prev,
        isOnline: false,
        recoveryMode: 'idle',
        port: config.port,
        url: buildTorrentServerUrl(config.port),
        cacheDirectory: '',
        torrentStoreDirectory: '',
        cacheUsageBytes: 0,
        profile: config.profile,
        cacheSizeGb: config.cacheSizeGb,
        isForeground: config.runAsForegroundService,
        requestedForeground: config.runAsForegroundService,
        lastStartupError: '',
        foregroundDowngradeReason: '',
        lifecycleState: 'idle',
      }));
    }
  }, [config]);

  const syncAfterStart = useCallback(async (nextConfig: TorrentServerConfig) => {
    for (let attempt = 0; attempt < 6; attempt++) {
      await delay(attempt === 0 ? 150 : 300);

      try {
        if (Platform.OS !== 'android' || !NativeTorrentServerModule?.getStatus) {
          return;
        }

        const nextStatus = await NativeTorrentServerModule.getStatus();
        const merged = mergeStatus(nextConfig, nextStatus);
        setStatus(merged);

        if (merged.isOnline) return;
      } catch {
        // Keep retrying a few times while the service finishes booting.
      }
    }
  }, []);

  const ensureStarted = useCallback(async (nextConfig: TorrentServerConfig) => {
    if (nextConfig.streamingMode !== 'server') {
      if (Platform.OS === 'android' && NativeTorrentServerModule?.stopServer) {
        try {
          const nextStatus = await NativeTorrentServerModule.stopServer();
          setStatus({
            ...mergeStatus(nextConfig, nextStatus),
            isOnline: false,
            recoveryMode: 'idle',
            lifecycleState: nextStatus.lifecycleState ?? 'stopped',
          });
          return;
        } catch {
          // Fall through to offline state below.
        }
      }

      setStatus({
        isOnline: false,
        isForeground: false,
        requestedForeground: nextConfig.runAsForegroundService,
        port: nextConfig.port,
        url: buildTorrentServerUrl(nextConfig.port),
        cacheDirectory: status.cacheDirectory,
        torrentStoreDirectory: status.torrentStoreDirectory,
        cacheUsageBytes: status.cacheUsageBytes,
        profile: nextConfig.profile,
        cacheSizeGb: nextConfig.cacheSizeGb,
        recoveryMode: 'idle',
        lastStartupError: '',
        foregroundDowngradeReason: '',
        lifecycleState: 'stopped',
      });
      return;
    }

    if (Platform.OS !== 'android' || !NativeTorrentServerModule?.ensureStarted) {
      setStatus({
        isOnline: false,
        isForeground: nextConfig.runAsForegroundService,
        requestedForeground: nextConfig.runAsForegroundService,
        port: nextConfig.port,
        url: buildTorrentServerUrl(nextConfig.port),
        cacheDirectory: '',
        torrentStoreDirectory: '',
        cacheUsageBytes: 0,
        profile: nextConfig.profile,
        cacheSizeGb: nextConfig.cacheSizeGb,
        recoveryMode: 'idle',
        lastStartupError: '',
        foregroundDowngradeReason: '',
        lifecycleState: 'idle',
      });
      return;
    }

    setStatus({
      isOnline: true,
      isForeground: nextConfig.runAsForegroundService,
      requestedForeground: nextConfig.runAsForegroundService,
      port: nextConfig.port,
      url: buildTorrentServerUrl(nextConfig.port),
      cacheDirectory: '',
      torrentStoreDirectory: '',
      cacheUsageBytes: 0,
      profile: nextConfig.profile,
      cacheSizeGb: nextConfig.cacheSizeGb,
      recoveryMode: 'starting',
      lastStartupError: '',
      foregroundDowngradeReason: '',
      lifecycleState: 'starting',
    });

    const nextStatus = await NativeTorrentServerModule.ensureStarted(nextConfig);
    setStatus(mergeStatus(nextConfig, nextStatus));
    await syncAfterStart(nextConfig);
  }, [status.cacheDirectory, status.cacheUsageBytes, status.torrentStoreDirectory, syncAfterStart]);

  const updateConfig = useCallback(async (next: Partial<TorrentServerConfig>) => {
    const merged = normalizeTorrentServerConfig({ ...config, ...next });
    setConfig(merged);
    // Persist server preferences in the background; the settings UI reads from config state immediately.
    void Storage.setItem(TORRENT_SERVER_SETTINGS_KEY, JSON.stringify(merged)).catch(() => {});

    if (merged.streamingMode !== 'server') {
      if (Platform.OS === 'android' && NativeTorrentServerModule?.stopServer) {
        try {
          const nextStatus = await NativeTorrentServerModule.stopServer();
          setStatus({
            ...mergeStatus(merged, nextStatus),
            isOnline: false,
            isForeground: false,
            recoveryMode: 'idle',
            lifecycleState: nextStatus.lifecycleState ?? 'stopped',
          });
          return;
        } catch {
          // Fall through to offline state below.
        }
      }

      setStatus(prev => ({
        ...prev,
        isOnline: false,
        isForeground: false,
        requestedForeground: merged.runAsForegroundService,
        recoveryMode: 'idle',
        port: merged.port,
        url: buildTorrentServerUrl(merged.port),
        profile: merged.profile,
        cacheSizeGb: merged.cacheSizeGb,
        lastStartupError: '',
        foregroundDowngradeReason: '',
        lifecycleState: 'stopped',
      }));
      return;
    }

    if (Platform.OS === 'android' && NativeTorrentServerModule?.updateConfig) {
      try {
        setStatus({
          isOnline: true,
          isForeground: merged.runAsForegroundService,
          requestedForeground: merged.runAsForegroundService,
          port: merged.port,
          url: buildTorrentServerUrl(merged.port),
          cacheDirectory: '',
          torrentStoreDirectory: '',
          cacheUsageBytes: 0,
          profile: merged.profile,
          cacheSizeGb: merged.cacheSizeGb,
          recoveryMode: 'starting',
          lastStartupError: '',
          foregroundDowngradeReason: '',
          lifecycleState: 'starting',
        });
        const nextStatus = await NativeTorrentServerModule.updateConfig(merged);
        setStatus(mergeStatus(merged, nextStatus));
        await syncAfterStart(merged);
        return;
      } catch {
        // Fall through to status refresh with the latest persisted config.
      }
    }

    setStatus(prev => ({
      ...prev,
      isOnline: false,
      recoveryMode: 'idle',
      port: merged.port,
      url: buildTorrentServerUrl(merged.port),
      cacheDirectory: '',
      torrentStoreDirectory: '',
      cacheUsageBytes: 0,
      profile: merged.profile,
      cacheSizeGb: merged.cacheSizeGb,
      isForeground: merged.runAsForegroundService,
      requestedForeground: merged.runAsForegroundService,
      lastStartupError: '',
      foregroundDowngradeReason: '',
      lifecycleState: 'idle',
    }));
  }, [config]);

  useEffect(() => {
    let cancelled = false;

    const bootstrap = async () => {
      try {
        const storedConfig = await Storage.getItem(TORRENT_SERVER_SETTINGS_KEY);
        const parsed = storedConfig ? JSON.parse(storedConfig) : null;
        const nextConfig = normalizeTorrentServerConfig(parsed);
        if (cancelled) return;
        setConfig(nextConfig);
        await ensureStarted(nextConfig);
      } finally {
        if (!cancelled) setIsReady(true);
      }
    };

    bootstrap();

    return () => {
      cancelled = true;
    };
  }, [ensureStarted]);

  useEffect(() => {
    if (!isReady) return;

    pollTimerRef.current = setInterval(() => {
      refreshStatus();
    }, 5000);

    const sub = AppState.addEventListener('change', nextState => {
      if (nextState === 'active') {
        if (config.streamingMode !== 'server') {
          refreshStatus();
        } else if (!status.isOnline) {
          ensureStarted(config);
        } else {
          refreshStatus();
        }
      }
    });

    return () => {
      sub.remove();
      if (pollTimerRef.current) clearInterval(pollTimerRef.current);
    };
  }, [isReady, refreshStatus, ensureStarted, config, status.isOnline]);

  const ensureOnline = useCallback(async () => {
    await ensureStarted(config);
  }, [ensureStarted, config]);

  const value = useMemo(() => ({
    config,
    status,
    isReady,
    updateConfig,
    refreshStatus,
    ensureOnline,
  }), [config, status, isReady, updateConfig, refreshStatus, ensureOnline]);

  return (
    <TorrentServerContext.Provider value={value}>
      {children}
    </TorrentServerContext.Provider>
  );
};

export const useTorrentServer = () => useContext(TorrentServerContext);
