import { useState, useEffect } from 'react';
import { WITTY_LOADING_MESSAGES } from '../utils/loadingMessages';

/**
 * Returns a witty loading message that rotates every X seconds.
 * @param isActive If true, the rotation will start/continue.
 * @param intervalMs The frequency of message change.
 */
export function useLoadingMessage(isActive: boolean, intervalMs: number = 3500) {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    if (!isActive) return;

    // Pick a random starting index
    setIndex(Math.floor(Math.random() * WITTY_LOADING_MESSAGES.length));

    const iv = setInterval(() => {
      setIndex(prev => (prev + 1) % WITTY_LOADING_MESSAGES.length);
    }, intervalMs);

    return () => clearInterval(iv);
  }, [isActive, intervalMs]);

  return WITTY_LOADING_MESSAGES[index];
}
