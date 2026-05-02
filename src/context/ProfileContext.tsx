import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import { Storage } from '../utils/storage';
import { useAuth } from './AuthContext';
import {
  createProfile as apiCreate,
  deleteProfile as apiDelete,
  fetchProfiles,
  setDefaultProfile as apiSetDefaultProfile,
  setProfilePin as apiSetPin,
  updateProfile as apiUpdate,
  verifyProfilePin as apiVerifyPin,
  type CreateProfileInput,
  MAX_PROFILES_PER_ACCOUNT,
  type StreamProfile,
  type UpdateProfileInput,
} from '../utils/profileApi';
import { consumeProfileLaunchBootstrap } from '../utils/profileLaunchBootstrap';

const activeProfileKey = (uid: string) => `streamdek_active_profile_${uid}`;

interface ProfileContextValue {
  profiles: StreamProfile[];
  activeProfile: StreamProfile | null;
  profilesReady: boolean;
  loadingProfiles: boolean;
  profileSwitching: boolean;

  setActiveProfile: (profile: StreamProfile) => Promise<void>;
  clearActiveProfile: () => Promise<void>;
  refreshProfiles: () => Promise<void>;

  createProfile: (input: CreateProfileInput) => Promise<{ profile?: StreamProfile; error?: string }>;
  updateProfile: (id: string, input: UpdateProfileInput) => Promise<{ error?: string }>;
  deleteProfile: (id: string) => Promise<{ error?: string }>;
  setDefaultProfile: (id: string) => Promise<{ error?: string }>;
  setProfilePin: (id: string, pin: string | null) => Promise<{ error?: string }>;
  verifyProfilePin: (id: string, pin: string) => Promise<boolean>;
}

const ProfileContext = createContext<ProfileContextValue | null>(null);

export function ProfileProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [profiles, setProfiles] = useState<StreamProfile[]>([]);
  const [activeProfile, setActiveProfileState] = useState<StreamProfile | null>(null);
  const [loadingProfiles, setLoadingProfiles] = useState(false);
  const [profilesReady, setProfilesReady] = useState(false);
  const [profileSwitching, setProfileSwitching] = useState(false);
  const loadedUidRef = useRef<string | null>(null);
  const requireProfileSelectionOnLaunchRef = useRef(true);

  const createInitialProfile = useCallback(async (uid: string) => {
    if (!user) return null;
    const defaultName = user.displayName?.trim() || 'My Profile';
    const created = await apiCreate(user, { name: defaultName, avatarIndex: 0 });
    setProfiles([created]);
    setActiveProfileState(requireProfileSelectionOnLaunchRef.current ? null : created);
    await Storage.setItem(activeProfileKey(uid), created.id);
    return created;
  }, [user]);

  const syncProfilesState = useCallback(async (
    loaded: StreamProfile[],
    preserveActiveId?: string | null,
  ) => {
    setProfiles(loaded);

    const uid = user?.uid ?? null;
    const storedId = uid ? await Storage.getItem(activeProfileKey(uid)) : null;
    const preferredId = preserveActiveId ?? storedId;
    const preferred = preferredId ? loaded.find(profile => profile.id === preferredId) ?? null : null;
    const fallback = loaded.find(profile => profile.isDefault) ?? loaded[0] ?? null;
    const nextActive = requireProfileSelectionOnLaunchRef.current
      ? null
      : ((preferred && !preferred.hasPinSet)
          ? preferred
          : (fallback && !fallback.hasPinSet ? fallback : null));

    setActiveProfileState(nextActive);
    if (uid) {
      if (nextActive) await Storage.setItem(activeProfileKey(uid), nextActive.id);
      else if (storedId) await Storage.setItem(activeProfileKey(uid), storedId);
      else await Storage.removeItem(activeProfileKey(uid));
    }
  }, [user?.uid]);

  // Load profiles whenever the signed-in user changes
  useEffect(() => {
    const uid = user?.uid ?? null;

    if (uid === loadedUidRef.current) return;
    requireProfileSelectionOnLaunchRef.current = true;
    loadedUidRef.current = uid;

    if (!uid) {
      setProfiles([]);
      setActiveProfileState(null);
      setProfilesReady(false);
      return;
    }

    let cancelled = false;
    setLoadingProfiles(true);

    (async () => {
      try {
        const preloaded = consumeProfileLaunchBootstrap(uid);
        if (preloaded) {
          await syncProfilesState(preloaded.profiles);
          return;
        }

        const loaded = await fetchProfiles(user!);
        if (cancelled) return;
        if (loaded.length === 0) {
          await createInitialProfile(uid);
          return;
        }
        await syncProfilesState(loaded);
      } catch {
        // If profiles can't be loaded, create a default one on first login
        if (!cancelled && user) {
          try {
            await createInitialProfile(uid);
          } catch {
            // Silent failure — user will see empty state
          }
        }
      } finally {
        if (!cancelled) {
          setLoadingProfiles(false);
          setProfilesReady(true);
        }
      }
    })();

    return () => { cancelled = true; };
  }, [createInitialProfile, syncProfilesState, user?.uid]); // eslint-disable-line react-hooks/exhaustive-deps

  const setActiveProfile = useCallback(async (profile: StreamProfile) => {
    setProfileSwitching(true);
    requireProfileSelectionOnLaunchRef.current = false;
    setActiveProfileState(profile);
    const uid = user?.uid;
    if (uid) void Storage.setItem(activeProfileKey(uid), profile.id).catch(() => {});
    setTimeout(() => setProfileSwitching(false), 320);
  }, [user?.uid]); // eslint-disable-line react-hooks/exhaustive-deps

  const clearActiveProfile = useCallback(async () => {
    setActiveProfileState(null);
    const uid = user?.uid;
    if (uid) await Storage.removeItem(activeProfileKey(uid));
  }, [user?.uid]);

  const refreshProfiles = useCallback(async () => {
    if (!user) return;
    setLoadingProfiles(true);
    try {
      const loaded = await fetchProfiles(user);
      await syncProfilesState(loaded, activeProfile?.id ?? null);
    } finally {
      setLoadingProfiles(false);
    }
  }, [activeProfile?.id, syncProfilesState, user]);

  const createProfileFn = useCallback(async (
    input: CreateProfileInput,
  ): Promise<{ profile?: StreamProfile; error?: string }> => {
    if (!user) return { error: 'Not signed in' };
    if (profiles.length >= MAX_PROFILES_PER_ACCOUNT) {
      return { error: `You can only have up to ${MAX_PROFILES_PER_ACCOUNT} profiles.` };
    }
    try {
      const profile = await apiCreate(user, input);
      setProfiles(prev => [...prev, profile]);
      return { profile };
    } catch (e: any) {
      return { error: e?.message ?? 'Failed to create profile' };
    }
  }, [profiles.length, user]);

  const updateProfileFn = useCallback(async (
    id: string,
    input: UpdateProfileInput,
  ): Promise<{ error?: string }> => {
    if (!user) return { error: 'Not signed in' };
    const previousProfiles = profiles;
    const previousActive = activeProfile;
    const current = profiles.find(p => p.id === id);
    if (!current) return { error: 'Profile not found' };

    const optimistic: StreamProfile = {
      ...current,
      ...input,
      subtitleLanguage: Object.prototype.hasOwnProperty.call(input, 'subtitleLanguage')
        ? input.subtitleLanguage ?? null
        : current.subtitleLanguage,
      audioLanguage: Object.prototype.hasOwnProperty.call(input, 'audioLanguage')
        ? input.audioLanguage ?? null
        : current.audioLanguage,
    };

    // Profile preferences should feel instant; the API save reconciles in the background.
    setProfiles(prev => prev.map(p => p.id === id ? optimistic : p));
    setActiveProfileState(prev => prev?.id === id ? optimistic : prev);

    void apiUpdate(user, id, input).then((updated) => {
      setProfiles(prev => prev.map(p => p.id === id ? updated : p));
      setActiveProfileState(prev => prev?.id === id ? updated : prev);
    }).catch(() => {
      setProfiles(previousProfiles);
      setActiveProfileState(previousActive);
    });

    return {};
  }, [activeProfile, profiles, user]);

  const deleteProfileFn = useCallback(async (
    id: string,
  ): Promise<{ error?: string }> => {
    if (!user) return { error: 'Not signed in' };
    try {
      await apiDelete(user, id);
      const loaded = await fetchProfiles(user);
      await syncProfilesState(loaded, activeProfile?.id === id ? null : activeProfile?.id ?? null);
      return {};
    } catch (e: any) {
      return { error: e?.message ?? 'Failed to delete profile' };
    }
  }, [activeProfile?.id, syncProfilesState, user]);

  const setDefaultProfileFn = useCallback(async (
    id: string,
  ): Promise<{ error?: string }> => {
    if (!user) return { error: 'Not signed in' };
    const previousProfiles = profiles;
    const previousActive = activeProfile;

    // Mark the default locally first; fetching the authoritative list happens after the tap response.
    setProfiles(prev => prev.map(profile => ({ ...profile, isDefault: profile.id === id })));

    void (async () => {
      await apiSetDefaultProfile(user, id);
      const loaded = await fetchProfiles(user);
      await syncProfilesState(loaded, activeProfile?.id ?? null);
    })().catch(() => {
      setProfiles(previousProfiles);
      setActiveProfileState(previousActive);
    });

    return {};
  }, [activeProfile, syncProfilesState, profiles, user]);

  const setProfilePinFn = useCallback(async (
    id: string,
    pin: string | null,
  ): Promise<{ error?: string }> => {
    if (!user) return { error: 'Not signed in' };
    const previousProfiles = profiles;
    const previousActive = activeProfile;
    const hasPinSet = !!pin;

    setProfiles(prev => prev.map(p => p.id === id ? { ...p, hasPinSet } : p));
    setActiveProfileState(prev => prev?.id === id ? { ...prev, hasPinSet } : prev);

    void apiSetPin(user, id, pin).catch(() => {
      setProfiles(previousProfiles);
      setActiveProfileState(previousActive);
    });

    return {};
  }, [activeProfile, profiles, user]);

  const verifyProfilePinFn = useCallback(async (
    id: string,
    pin: string,
  ): Promise<boolean> => {
    if (!user) return false;
    return apiVerifyPin(user, id, pin);
  }, [user]);

  return (
    <ProfileContext.Provider
      value={{
        profiles,
        activeProfile,
        profilesReady,
        loadingProfiles,
        profileSwitching,
        setActiveProfile,
        clearActiveProfile,
        refreshProfiles,
        createProfile: createProfileFn,
        updateProfile: updateProfileFn,
        deleteProfile: deleteProfileFn,
        setDefaultProfile: setDefaultProfileFn,
        setProfilePin: setProfilePinFn,
        verifyProfilePin: verifyProfilePinFn,
      }}
    >
      {children}
    </ProfileContext.Provider>
  );
}

export function useProfile() {
  const ctx = useContext(ProfileContext);
  if (!ctx) throw new Error('useProfile must be used within ProfileProvider');
  return ctx;
}
