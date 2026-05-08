import * as Network from 'expo-network';
import { Storage } from './storage';

const STORAGE_KEY = 'streamdek_sync_over_cellular';

export async function getSyncOverCellular(): Promise<boolean> {
  const val = await Storage.getItem(STORAGE_KEY).catch(() => null);
  return val === 'true';
}

export async function setSyncOverCellular(enabled: boolean): Promise<void> {
  await Storage.setItem(STORAGE_KEY, enabled ? 'true' : 'false').catch(() => {});
}

export type NetworkGuardResult =
  | { allowed: true; reason: 'wifi' | 'cellular_allowed' }
  | { allowed: false; reason: 'offline' | 'cellular_blocked' };

export async function checkSyncAllowed(): Promise<NetworkGuardResult> {
  try {
    const state = await Network.getNetworkStateAsync();

    if (!state.isConnected) {
      return { allowed: false, reason: 'offline' };
    }

    if (
      state.type === Network.NetworkStateType.WIFI ||
      state.type === Network.NetworkStateType.ETHERNET ||
      state.type === Network.NetworkStateType.NONE ||
      state.type === Network.NetworkStateType.UNKNOWN
    ) {
      return { allowed: true, reason: 'wifi' };
    }

    if (state.type === Network.NetworkStateType.CELLULAR) {
      const pref = await getSyncOverCellular();
      return pref
        ? { allowed: true, reason: 'cellular_allowed' }
        : { allowed: false, reason: 'cellular_blocked' };
    }

    return { allowed: true, reason: 'wifi' };
  } catch {
    // If network state can't be read, don't block the user.
    return { allowed: true, reason: 'wifi' };
  }
}
