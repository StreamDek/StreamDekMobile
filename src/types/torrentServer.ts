export type TorrentProfile = 'default' | 'soft' | 'fast' | 'ultra_fast';

export type TorrentCacheSize = 0 | 2 | 5 | 10 | 20;

export type StreamingMode = 'server' | 'regular_http';

export interface TorrentServerConfig {
  streamingMode: StreamingMode;
  profile: TorrentProfile;
  cacheSizeGb: TorrentCacheSize;
  port: number;
  runAsForegroundService: boolean;
}

export interface TorrentServerStatus {
  isOnline: boolean;
  isForeground: boolean;
  requestedForeground: boolean;
  port: number;
  url: string;
  cacheDirectory: string;
  torrentStoreDirectory: string;
  cacheUsageBytes: number;
  profile: TorrentProfile;
  cacheSizeGb: TorrentCacheSize;
  recoveryMode: 'idle' | 'starting' | 'running' | 'recovering';
  lastStartupError: string;
  foregroundDowngradeReason: string;
  lifecycleState: string;
}

export const TORRENT_SERVER_SETTINGS_KEY = 'streamdek_torrent_server_settings';

export const DEFAULT_TORRENT_SERVER_CONFIG: TorrentServerConfig = {
  streamingMode: 'regular_http',
  profile: 'default',
  cacheSizeGb: 5,
  port: 11100,
  runAsForegroundService: false,
};

export const TORRENT_PROFILE_OPTIONS: Array<{ value: TorrentProfile; label: string }> = [
  { value: 'default', label: 'Default' },
  { value: 'soft', label: 'Soft' },
  { value: 'fast', label: 'Fast' },
  { value: 'ultra_fast', label: 'Ultra Fast' },
];

export const TORRENT_CACHE_OPTIONS: Array<{ value: TorrentCacheSize; label: string }> = [
  { value: 0, label: 'No caching' },
  { value: 2, label: '2GB' },
  { value: 5, label: '5GB' },
  { value: 10, label: '10GB' },
  { value: 20, label: '20GB' },
];

export function normalizeTorrentServerConfig(
  raw?: Partial<TorrentServerConfig> | null,
): TorrentServerConfig {
  const streamingMode = ['server', 'regular_http'].includes(raw?.streamingMode ?? '')
    ? (raw?.streamingMode as StreamingMode)
    : DEFAULT_TORRENT_SERVER_CONFIG.streamingMode;
  const port = typeof raw?.port === 'number' && raw.port > 0 ? raw.port : DEFAULT_TORRENT_SERVER_CONFIG.port;
  const cacheSizeGb = [0, 2, 5, 10, 20].includes(raw?.cacheSizeGb as number)
    ? (raw?.cacheSizeGb as TorrentCacheSize)
    : DEFAULT_TORRENT_SERVER_CONFIG.cacheSizeGb;
  const profile = ['default', 'soft', 'fast', 'ultra_fast'].includes(raw?.profile ?? '')
    ? (raw?.profile as TorrentProfile)
    : DEFAULT_TORRENT_SERVER_CONFIG.profile;

  return {
    streamingMode,
    profile,
    cacheSizeGb,
    port,
    runAsForegroundService: raw?.runAsForegroundService ?? DEFAULT_TORRENT_SERVER_CONFIG.runAsForegroundService,
  };
}

export function buildTorrentServerUrl(port: number): string {
  return `http://127.0.0.1:${port}`;
}
