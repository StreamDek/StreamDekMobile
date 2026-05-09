type IdleTaskOptions = {
  timeoutMs?: number;
};

export type IdleTaskHandle = {
  cancel: () => void;
};

/**
 * Runs a task when the JS thread is idle.
 * This is the modern replacement for InteractionManager.runAfterInteractions
 * that avoids deprecation warnings and ensures better frame stability.
 */
export function runIdle(task: () => void | Promise<void>, options?: IdleTaskOptions): IdleTaskHandle {
  let cancelled = false;
  const timeoutMs = options?.timeoutMs ?? 1200;
  const runTask = () => {
    if (cancelled) return;
    void task();
  };

  if (typeof requestIdleCallback === 'function') {
    const idleId = requestIdleCallback(runTask, { timeout: timeoutMs });
    return {
      cancel: () => {
        cancelled = true;
        cancelIdleCallback(idleId);
      },
    };
  }

  // Fallback for environments where requestIdleCallback is unavailable.
  const timeoutId = setTimeout(runTask, 48);
  return {
    cancel: () => {
      cancelled = true;
      clearTimeout(timeoutId);
    },
  };
}
