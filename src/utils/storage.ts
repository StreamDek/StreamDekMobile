import { Directory, File, Paths } from 'expo-file-system';

const storageDir = new Directory(Paths.document, 'streamdek_storage');
type HotStorageLike = {
  getString: (key: string) => string | undefined;
  set: (key: string, value: string) => void;
  remove: (key: string) => boolean;
};

let hotStorage: HotStorageLike | null = null;

try {
  const mmkvModule = require('react-native-mmkv') as { createMMKV?: (config?: { id?: string }) => HotStorageLike };
  if (typeof mmkvModule.createMMKV === 'function') {
    hotStorage = mmkvModule.createMMKV({ id: 'streamdek-hot-storage' });
  }
} catch {
  hotStorage = null;
}

const HOT_KEY_PATTERNS = [
  /^streamdek_progress_/i,
  /^streamdek_progress_index/i,
  /^streamdek_last_stream/i,
  /^streamdek_theme(?:_|$)/i,
  /^streamdek_appearance(?:_|$)/i,
  /^streamdek_show_hero_synopsis(?:_|$)/i,
  /^streamdek_language(?:_|$)/i,
  /^streamdek_ui_style(?:_|$)/i,
  /^streamdek_display_settings(?:_|$)/i,
  /^streamdek_active_profile_/i,
  /^streamdek_tmdb_key_/i,
  /^subtitle_settings$/i,
  /^torrent_server_settings$/i,
  /^home_sections(?:_|$)/i,
  /^stream_selection_settings$/i,
  /^playback_settings$/i,
  /^streamdek_progress_v1_/i,
  /^streamdek_watchlist(?:_|$)/i,
  /^streamdek_watchlist_removed(?:_|$)/i,
];

function getFile(key: string): File {
  const safeName = key.replace(/[^a-z0-9_-]/gi, '_') + '.json';
  return new File(storageDir, safeName);
}

function shouldUseHotStorage(key: string): boolean {
  return HOT_KEY_PATTERNS.some(pattern => pattern.test(key));
}

export const Storage = {
  async getItem(key: string): Promise<string | null> {
    try {
      if (hotStorage && shouldUseHotStorage(key)) {
        const hotValue = hotStorage.getString(key);
        if (typeof hotValue === 'string') return hotValue;
      }
      if (!storageDir.exists) await storageDir.create();
      const file = getFile(key);
      if (!file.exists) return null;
      const value = await file.text();
      if (hotStorage && shouldUseHotStorage(key) && value != null) {
        hotStorage.set(key, value);
      }
      return value;
    } catch {
      return null;
    }
  },

  async setItem(key: string, value: string): Promise<void> {
    if (hotStorage && shouldUseHotStorage(key)) {
      hotStorage.set(key, value);
      return;
    }
    if (!storageDir.exists) await storageDir.create();
    await getFile(key).write(value);
  },

  async removeItem(key: string): Promise<void> {
    try {
      if (hotStorage && shouldUseHotStorage(key)) {
        hotStorage.remove(key);
      }
      const file = getFile(key);
      if (file.exists) await file.delete();
    } catch {}
  },
};
