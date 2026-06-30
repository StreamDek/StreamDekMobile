import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { Storage } from '../utils/storage';
import { profileScopedStorageKey } from '../utils/profileStorage';
import { useAuth } from './AuthContext';
import { useProfile } from './ProfileContext';
import {
  COLLECTIONS_STORAGE_KEY,
  normalizeImportedCollections,
  type Collection,
  type CollectionValidationResult,
  validateCollectionsImport,
} from '../utils/collections';

type CollectionsContextValue = {
  collections: Collection[];
  isReady: boolean;
  importFromJson: (jsonString: string) => Promise<CollectionValidationResult>;
  exportToJson: () => string;
  removeCollection: (collectionId: string) => Promise<void>;
  moveCollection: (fromIndex: number, toIndex: number) => Promise<void>;
  replaceCollections: (nextCollections: Collection[]) => Promise<void>;
};

const CollectionsContext = createContext<CollectionsContextValue>({
  collections: [],
  isReady: false,
  importFromJson: async () => ({ valid: false, error: 'Collections unavailable.', collectionCount: 0, folderCount: 0 }),
  exportToJson: () => '[]',
  removeCollection: async () => {},
  moveCollection: async () => {},
  replaceCollections: async () => {},
});

export function CollectionsProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const [collections, setCollections] = useState<Collection[]>([]);
  const [isReady, setIsReady] = useState(false);
  const storageKeys = useMemo(() => ([
    profileScopedStorageKey(COLLECTIONS_STORAGE_KEY, user?.uid, activeProfile?.id),
    ...(user?.uid && activeProfile?.id ? [profileScopedStorageKey(COLLECTIONS_STORAGE_KEY, user.uid, null)] : []),
    COLLECTIONS_STORAGE_KEY,
  ]), [activeProfile?.id, user?.uid]);

  useEffect(() => {
    let cancelled = false;
    setIsReady(false);

    void (async () => {
      try {
        let raw: string | null = null;
        for (const key of storageKeys) {
          raw = await Storage.getItem(key);
          if (raw) break;
        }
        if (cancelled) return;
        if (!raw) {
          setCollections([]);
          return;
        }
        const parsed = JSON.parse(raw);
        setCollections(normalizeImportedCollections(parsed));
      } catch {
        if (!cancelled) setCollections([]);
      } finally {
        if (!cancelled) setIsReady(true);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [storageKeys]);

  const persistCollections = useCallback(async (nextCollections: Collection[]) => {
    const normalized = normalizeImportedCollections(nextCollections);
    setCollections(normalized);
    const payload = JSON.stringify(normalized);
    await Promise.all(storageKeys.map(key => Storage.setItem(key, payload)));
  }, [storageKeys]);

  const importFromJson = useCallback(async (jsonString: string) => {
    const validation = validateCollectionsImport(jsonString);
    if (!validation.valid) return validation;
    const parsed = normalizeImportedCollections(JSON.parse(jsonString));
    await persistCollections(parsed);
    return validation;
  }, [persistCollections]);

  const exportToJson = useCallback(() => JSON.stringify(collections, null, 2), [collections]);

  const removeCollection = useCallback(async (collectionId: string) => {
    await persistCollections(collections.filter(collection => collection.id !== collectionId));
  }, [collections, persistCollections]);

  const moveCollection = useCallback(async (fromIndex: number, toIndex: number) => {
    if (fromIndex === toIndex) return;
    if (fromIndex < 0 || fromIndex >= collections.length) return;
    if (toIndex < 0 || toIndex >= collections.length) return;
    const next = collections.slice();
    const [moved] = next.splice(fromIndex, 1);
    next.splice(toIndex, 0, moved);
    await persistCollections(next);
  }, [collections, persistCollections]);

  const value = useMemo(() => ({
    collections,
    isReady,
    importFromJson,
    exportToJson,
    removeCollection,
    moveCollection,
    replaceCollections: persistCollections,
  }), [collections, exportToJson, importFromJson, isReady, moveCollection, persistCollections, removeCollection]);

  return (
    <CollectionsContext.Provider value={value}>
      {children}
    </CollectionsContext.Provider>
  );
}

export function useCollections() {
  return useContext(CollectionsContext);
}
