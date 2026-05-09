import { useSyncExternalStore } from 'react';

type Listener = () => void;

export type PlaybackSessionStore<T extends object> = {
  getState: () => T;
  setState: (updater: Partial<T> | ((prev: T) => Partial<T> | T)) => void;
  subscribe: (listener: Listener) => () => void;
};

export function createPlaybackSessionStore<T extends object>(initialState: T): PlaybackSessionStore<T> {
  let state = initialState;
  const listeners = new Set<Listener>();

  return {
    getState: () => state,
    setState: updater => {
      const nextPatch = typeof updater === 'function' ? updater(state) : updater;
      const nextState = { ...state, ...nextPatch };
      if (Object.is(nextState, state)) return;
      let changed = false;
      for (const key of Object.keys(nextState) as Array<keyof T>) {
        if (!Object.is(state[key], nextState[key])) {
          changed = true;
          break;
        }
      }
      if (!changed) return;
      state = nextState;
      listeners.forEach(listener => listener());
    },
    subscribe: listener => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
}

export function usePlaybackSessionSelector<T extends object, S>(
  store: PlaybackSessionStore<T>,
  selector: (state: T) => S,
): S {
  return useSyncExternalStore(
    store.subscribe,
    () => selector(store.getState()),
    () => selector(store.getState()),
  );
}
