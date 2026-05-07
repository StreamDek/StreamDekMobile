import React, {
  createContext, useContext, useState, useCallback, useEffect,
} from 'react';
import { API_BASE } from '../constants/api';
import { useAuth } from './AuthContext';
import { useLanguage } from './LanguageContext';
import { buildAuthHeaders } from '../utils/authHeaders';
import { getSharedCachedAsync, invalidateSharedCache } from '../utils/sharedDataCache';

const DEBRID_STATE_TTL_MS = 20_000;


export type DebridProviderName = 'real-debrid' | 'alldebrid' | 'premiumize' | 'torbox' | 'debrid-link';
export type DebridServiceName = DebridProviderName;
export type DebridServiceKind = 'account';

export interface DebridAccount {
  provider: DebridProviderName;
  enabled: boolean;
  priority: number;
  username?: string;
}

export interface DebridService {
  name: DebridServiceName;
  kind: DebridServiceKind;
  label: string;
  description: string;
  color: string;
  website: string;
}

export interface DebridResolvedStream {
  provider: DebridProviderName;
  url: string;
  filename: string;
  filesize: number;
  failures?: DebridFailure[];
}

export interface DebridFailure {
  provider: DebridProviderName;
  code:
    | 'subscription_required'
    | 'unsupported_host'
    | 'access_denied'
    | 'rate_limited'
    | 'timeout'
    | 'upstream_error'
    | 'not_configured'
    | 'unknown';
  message: string;
}

interface DebridContextType {
  accounts: DebridAccount[];
  isLoading: boolean;
  /** Add or update a provider account. Validates the key server-side before saving. */
  addAccount(provider: DebridProviderName, apiKey: string): Promise<{ success: boolean; username?: string; error?: string }>;
  /** Remove a provider account. Returns true on success. */
  removeAccount(provider: DebridProviderName): Promise<boolean>;
  /** Test an API key without saving it. */
  testKey(provider: DebridProviderName, apiKey: string): Promise<{ valid: boolean; username?: string }>;
  /** Resolve a torrent hash + magnet link to a direct stream URL. */
  resolveStream(infoHash: string, magnetLink: string, filename?: string, options?: { maxSize?: number; providerHint?: DebridProviderName }): Promise<DebridResolvedStream | null>;
  /** Unrestrict a premium hoster URL. */
  unrestrictLink(url: string): Promise<DebridResolvedStream | null>;
  /**
   * Stream a torrent directly via the backend WebTorrent engine.
   * Works without a Debrid account — the backend downloads and streams the torrent.
   * Returns the HTTP streaming URL, or null if the backend can't handle it.
   */
  streamTorrent(infoHash: string, magnetLink: string, filename?: string): Promise<string | null>;
  refreshAccounts(): Promise<void>;
  /** Persist a new priority order for connected accounts. */
  reorderAccounts(orderedProviders: DebridProviderName[]): Promise<void>;
}

const DebridContext = createContext<DebridContextType>({
  accounts: [],
  isLoading: false,
  addAccount:       async () => ({ success: false }),
  removeAccount:    async () => false,
  testKey:          async () => ({ valid: false }),
  resolveStream:    async () => null,
  unrestrictLink:   async () => null,
  streamTorrent:    async () => null,
  refreshAccounts:  async () => {},
  reorderAccounts:  async () => {},
});

export const DebridProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const { t } = useLanguage();
  const [accounts, setAccounts] = useState<DebridAccount[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const refreshAccounts = useCallback(async () => {
    if (!user) { setAccounts([]); return; }
    setIsLoading(true);
    try {
      const res = await fetch(`${API_BASE}/debrid/accounts`, { headers: await buildAuthHeaders(user) });
      if (res.ok) {
        const data = await res.json();
        setAccounts(data.accounts ?? []);
      }
    } catch { /* network error — keep stale state */ }
    finally { setIsLoading(false); }
  }, [user]);

  useEffect(() => { refreshAccounts(); }, [refreshAccounts]);

  const addAccount = useCallback(async (
    provider: DebridProviderName,
    apiKey: string,
  ): Promise<{ success: boolean; username?: string; error?: string }> => {
    if (!user) return { success: false, error: t('common_not_signed_in') };
    try {
      const res = await fetch(`${API_BASE}/debrid/accounts`, {
        method: 'POST',
        headers: await buildAuthHeaders(user),
        body: JSON.stringify({ provider, apiKey }),
      });
      const data = await res.json();
      if (!res.ok) return { success: false, error: data.error ?? t('error_add_account_failed') };
      invalidateSharedCache(`debrid:accounts:${user.uid}`);
      await refreshAccounts();
      return { success: true, username: data.username };
    } catch {
      return { success: false, error: t('common_network_error') };
    }
  }, [refreshAccounts, t, user]);

  const removeAccount = useCallback(async (provider: DebridProviderName): Promise<boolean> => {
    if (!user) return false;
    try {
      const res = await fetch(`${API_BASE}/debrid/accounts/${provider}`, {
        method: 'DELETE',
        headers: await buildAuthHeaders(user, { includeContentType: false }),
      });
      if (!res.ok) return false;
      invalidateSharedCache(`debrid:accounts:${user.uid}`);
      await refreshAccounts();
      return true;
    } catch {
      return false;
    }
  }, [refreshAccounts, user]);

  const reorderAccounts = useCallback(async (orderedProviders: DebridProviderName[]) => {
    if (!user) return;
    const previousAccounts = accounts;
    // Reordering is visual preference state; update immediately and reconcile with the backend later.
    setAccounts(prev => {
      const byProvider = new Map(prev.map(account => [account.provider, account]));
      return orderedProviders
        .filter(provider => byProvider.has(provider))
        .map((provider, index) => ({ ...byProvider.get(provider)!, priority: index }));
    });

    void fetch(`${API_BASE}/debrid/accounts/reorder`, {
        method: 'POST',
        headers: await buildAuthHeaders(user),
        body: JSON.stringify({ order: orderedProviders }),
    }).then(() => {
      invalidateSharedCache(`debrid:accounts:${user.uid}`);
      void refreshAccounts();
    }).catch(() => {
      setAccounts(previousAccounts);
    });
  }, [accounts, refreshAccounts, user]);

  const testKey = useCallback(async (
    provider: DebridProviderName,
    apiKey: string,
  ): Promise<{ valid: boolean; username?: string }> => {
    try {
      const res = await fetch(`${API_BASE}/debrid/accounts/${provider}/test`, {
        method: 'POST',
        headers: await buildAuthHeaders(null),
        body: JSON.stringify({ apiKey }),
      });
      return await res.json();
    } catch {
      return { valid: false };
    }
  }, []);

  const resolveStream = useCallback(async (
    infoHash: string,
    magnetLink: string,
    filename?: string,
    options?: { maxSize?: number; providerHint?: DebridProviderName },
  ): Promise<DebridResolvedStream | null> => {
    if (!user) return null;
    try {
      const res = await fetch(`${API_BASE}/debrid/resolve`, {
        method: 'POST',
        headers: await buildAuthHeaders(user),
        body: JSON.stringify({
          infoHash,
          magnetLink,
          ...(filename ? { filename } : {}),
          ...(options?.maxSize ? { maxSize: options.maxSize } : {}),
          ...(options?.providerHint ? { providerHint: options.providerHint } : {}),
        }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw Object.assign(new Error(data.error ?? 'Could not resolve stream'), {
          failures: data.failures ?? [],
        });
      }
      return {
        provider: data.provider,
        url: data.url,
        filename: data.filename,
        filesize: data.filesize,
        failures: data.failures ?? [],
      };
    } catch (error: any) {
      throw Object.assign(error instanceof Error ? error : new Error('Could not resolve stream'), {
        failures: error?.failures ?? [],
      });
    }
  }, [user]);

  const unrestrictLink = useCallback(async (
    url: string,
  ): Promise<DebridResolvedStream | null> => {
    if (!user) return null;
    try {
      const res = await fetch(`${API_BASE}/debrid/unrestrict`, {
        method: 'POST',
        headers: await buildAuthHeaders(user),
        body: JSON.stringify({ url }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw Object.assign(new Error(data.error ?? 'Could not unrestrict link'), {
          failures: data.failures ?? [],
        });
      }
      return {
        provider: data.provider,
        url: data.url,
        filename: data.filename,
        filesize: data.filesize,
        failures: data.failures ?? [],
      };
    } catch (error: any) {
      throw Object.assign(error instanceof Error ? error : new Error('Could not unrestrict link'), {
        failures: error?.failures ?? [],
      });
    }
  }, [user]);

  const streamTorrent = useCallback(async (
    infoHash: string,
    magnetLink: string,
    filename?: string,
  ): Promise<string | null> => {
    try {
      const res = await fetch(`${API_BASE}/stream/torrent/add`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ infoHash, magnetLink, ...(filename ? { filename } : {}) }),
      });
      if (!res.ok) return null;
      const data = await res.json();
      return data.streamUrl ?? null;
    } catch {
      return null;
    }
  }, []);

  return (
    <DebridContext.Provider value={{
      accounts, isLoading,
      addAccount, removeAccount, testKey,
      resolveStream, unrestrictLink, streamTorrent,
      refreshAccounts, reorderAccounts,
    }}>
      {children}
    </DebridContext.Provider>
  );
};

export const useDebrid = () => useContext(DebridContext);

// ── Provider display metadata ─────────────────────────────────────────────────

export const DEBRID_PROVIDERS: DebridService[] = [
  {
    name: 'real-debrid',
    kind: 'account',
    label: 'Real-Debrid',
    description: 'Most popular Debrid service with 100k+ cached torrents',
    color: '#00d1b2',
    website: 'real-debrid.com',
  },
  {
    name: 'alldebrid',
    kind: 'account',
    label: 'AllDebrid',
    description: 'Premium link generator with extensive hoster support',
    color: '#f5a623',
    website: 'alldebrid.com',
  },
  {
    name: 'premiumize',
    kind: 'account',
    label: 'Premiumize',
    description: 'Cloud storage + debrid with built-in VPN',
    color: '#9b59b6',
    website: 'premiumize.me',
  },
  {
    name: 'torbox',
    kind: 'account',
    label: 'TorBox',
    description: 'Fast torrent debrid with instant cache and high speed downloads',
    color: '#e85d27',
    website: 'torbox.app',
  },
  {
    name: 'debrid-link',
    kind: 'account',
    label: 'Debrid-Link',
    description: 'Seedbox + downloader API with streaming transcode support and hoster unrestricting.',
    color: '#00506d',
    website: 'https://debrid-link.com/webapp/apikey',
  },
];
