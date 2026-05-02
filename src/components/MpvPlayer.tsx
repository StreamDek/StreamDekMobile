import React, { forwardRef, useImperativeHandle, useRef } from 'react';
import {
  findNodeHandle,
  NativeSyntheticEvent,
  Platform,
  requireNativeComponent,
  UIManager,
  ViewProps,
} from 'react-native';

const COMPONENT_NAME = 'MpvPlayer';

const androidVersion = typeof Platform.Version === 'number' ? Platform.Version : Number(Platform.Version);
const isAndroidOOrNewer = Platform.OS === 'android' && Number.isFinite(androidVersion) && androidVersion >= 26;
let lastMpvResolveErrorMessage: string | null = null;
let cachedNativeMpvPlayer: any | null | undefined;

function getViewManagerConfig() {
  return UIManager.getViewManagerConfig?.(COMPONENT_NAME);
}

function resolveNativeMpvPlayer() {
  if (cachedNativeMpvPlayer !== undefined) {
    return cachedNativeMpvPlayer;
  }
  if (!isAndroidOOrNewer) return null;
  try {
    const component = requireNativeComponent<MpvPlayerNativeProps>(COMPONENT_NAME);
    lastMpvResolveErrorMessage = null;
    cachedNativeMpvPlayer = component;
    return cachedNativeMpvPlayer;
  } catch (error) {
    lastMpvResolveErrorMessage = error instanceof Error ? error.message : String(error);
    cachedNativeMpvPlayer = null;
    return null;
  }
}

export function isMpvNativeViewAvailable(): boolean {
  return !!resolveNativeMpvPlayer();
}

export function getMpvNativeViewAvailabilityDiagnostics(): { available: boolean; reason: string | null } {
  const available = !!resolveNativeMpvPlayer();
  return {
    available,
    reason: available ? null : lastMpvResolveErrorMessage,
  };
}

type ResizeMode = 'contain' | 'cover' | 'stretch';

export type MpvLoadEvent = {
  duration: number;
  width: number;
  height: number;
};

export type MpvProgressEvent = {
  currentTime: number;
  duration: number;
};

export type MpvErrorEvent = {
  error: string;
};

export type MpvTrack = {
  id: number;
  type: 'audio' | 'sub';
  title?: string | null;
  language?: string | null;
  codec?: string | null;
  selected?: boolean;
};

export type MpvTracksChangedEvent = {
  audioTracks: MpvTrack[];
  subtitleTracks: MpvTrack[];
  selectedAudioTrackId?: number | null;
  selectedSubtitleTrackId?: number | null;
};

export type MpvPlayerHandle = {
  seekTo: (seconds: number) => void;
  setAudioTrack: (trackId: number) => void;
  setSubtitleTrack: (trackId: number | null) => void;
  /**
   * Load an external subtitle file into mpv using the sub-add command.
   * @param filePath A file:// URI or absolute path to the subtitle file
   *                 (typically from expo-file-system's cache directory).
   * The newly added subtitle is immediately selected for display.
   * An onTracksChanged event will fire shortly after, updating the subtitle
   * tracks list in React state.
   */
  addSubtitleFile: (filePath: string) => void;
  /**
   * Set the subtitle display delay in seconds relative to the audio.
   * Positive = show later, negative = show earlier.
   * Maps directly to mpv's sub-delay property.
   */
  setSubtitleDelay: (seconds: number) => void;
  /** Set subtitle font size (default 55 in mpv). */
  setSubtitleFontSize: (size: number) => void;
  /** Set subtitle text color in #RRGGBBAA hex format. E.g. "#FFFFFFFF" = white. */
  setSubtitleColor: (color: string) => void;
  /** Set subtitle vertical position (0–150; 90 ≈ near bottom). */
  setSubtitlePosition: (position: number) => void;
};

type MpvPlayerNativeProps = ViewProps & {
  source?: string;
  uri?: string;
  paused?: boolean;
  volume?: number;
  rate?: number;
  resizeMode?: ResizeMode;
  headers?: Record<string, string>;
  onLoad?: (event: NativeSyntheticEvent<MpvLoadEvent>) => void;
  onProgress?: (event: NativeSyntheticEvent<MpvProgressEvent>) => void;
  onEnd?: (event: NativeSyntheticEvent<Record<string, never>>) => void;
  onError?: (event: NativeSyntheticEvent<MpvErrorEvent>) => void;
  onTracksChanged?: (event: NativeSyntheticEvent<MpvTracksChangedEvent>) => void;
};

export type MpvPlayerProps = MpvPlayerNativeProps;

function dispatchCommand(
  nativeRef: React.RefObject<unknown>,
  commandName: string,
  args: unknown[],
) {
  const nodeHandle = findNodeHandle(nativeRef.current as any);
  if (!nodeHandle) return;
  const viewManagerConfig = getViewManagerConfig();
  const commandId = viewManagerConfig?.Commands?.[commandName];
  const command = commandId === undefined || commandId === null ? commandName : commandId;
  UIManager.dispatchViewManagerCommand(nodeHandle, command as any, args);
}

export const MpvPlayer = forwardRef<MpvPlayerHandle, MpvPlayerProps>((props, ref) => {
  const nativeRef = useRef<unknown>(null);
  const NativeMpvPlayer = resolveNativeMpvPlayer();

  useImperativeHandle(ref, () => ({
    seekTo: (seconds: number) => {
      dispatchCommand(nativeRef, 'seek', [seconds]);
    },
    setAudioTrack: (trackId: number) => {
      dispatchCommand(nativeRef, 'setAudioTrack', [trackId]);
    },
    setSubtitleTrack: (trackId: number | null) => {
      if (trackId === null) {
        dispatchCommand(nativeRef, 'disableSubtitleTrack', []);
        return;
      }
      dispatchCommand(nativeRef, 'setSubtitleTrack', [trackId]);
    },
    addSubtitleFile: (filePath: string) => {
      dispatchCommand(nativeRef, 'addSubtitleFile', [filePath]);
    },
    setSubtitleDelay: (seconds: number) => {
      dispatchCommand(nativeRef, 'setSubtitleDelay', [seconds]);
    },
    setSubtitleFontSize: (size: number) => {
      dispatchCommand(nativeRef, 'setSubtitleFontSize', [size]);
    },
    setSubtitleColor: (color: string) => {
      dispatchCommand(nativeRef, 'setSubtitleColor', [color]);
    },
    setSubtitlePosition: (position: number) => {
      dispatchCommand(nativeRef, 'setSubtitlePosition', [position]);
    },
  }), []);

  if (!NativeMpvPlayer) return null;
  return <NativeMpvPlayer ref={nativeRef as any} {...props} />;
});

MpvPlayer.displayName = 'MpvPlayer';
