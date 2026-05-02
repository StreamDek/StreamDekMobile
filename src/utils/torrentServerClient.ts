import { NativeModules, Platform } from 'react-native';

type NativeTorrentServerModuleType = {
  createProxySession?: (upstreamUrl: string, headers?: Record<string, string>) => Promise<string>;
  createTorrentSession?: (infoHash: string, magnetLink: string, preferredFilename?: string | null) => Promise<string>;
};

const NativeTorrentServerModule = NativeModules.TorrentServerModule as NativeTorrentServerModuleType | undefined;
const LOCAL_TORRENT_SESSION_TIMEOUT_MS = 4_000;

export async function createLocalProxyUrl(
  upstreamUrl: string,
  headers?: Record<string, string>,
): Promise<string> {
  if (upstreamUrl.startsWith('http://127.0.0.1:') || upstreamUrl.startsWith('http://localhost:')) {
    return upstreamUrl;
  }

  if (Platform.OS !== 'android' || !NativeTorrentServerModule?.createProxySession) {
    return upstreamUrl;
  }

  try {
    return await NativeTorrentServerModule.createProxySession(upstreamUrl, headers ?? {});
  } catch {
    return upstreamUrl;
  }
}

export async function createLocalTorrentPlaybackUrl(
  infoHash: string,
  magnetLink: string,
  preferredFilename?: string | null,
): Promise<string | null> {
  if (Platform.OS !== 'android' || !NativeTorrentServerModule?.createTorrentSession) {
    return null;
  }

  try {
    return await Promise.race<string | null>([
      NativeTorrentServerModule.createTorrentSession(infoHash, magnetLink, preferredFilename ?? null),
      new Promise<null>(resolve => {
        setTimeout(() => resolve(null), LOCAL_TORRENT_SESSION_TIMEOUT_MS);
      }),
    ]);
  } catch {
    return null;
  }
}
