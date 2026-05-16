import { InteractionManager } from 'react-native';

/**
 * Runs a task when the JS thread is idle.
 * This is the modern replacement for InteractionManager.runAfterInteractions
 * that avoids deprecation warnings and ensures better frame stability.
 */
export function runIdle(task: () => void | Promise<void>) {
  if (typeof requestIdleCallback === 'function') {
    requestIdleCallback(() => {
      void task();
    });
  } else {
    // Fallback if requestIdleCallback is missing (rare in modern RN)
    InteractionManager.runAfterInteractions(() => {
      void task();
    });
  }
}
