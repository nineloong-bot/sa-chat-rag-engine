import { useEffect, useRef } from 'react';

export function usePolling(
  callback: () => Promise<void>,
  intervalMs: number,
  enabled: boolean,
) {
  const savedCallback = useRef(callback);
  savedCallback.current = callback;

  useEffect(() => {
    if (!enabled) return;
    let active = true;
    const poll = async () => {
      if (!active) return;
      await savedCallback.current();
      if (active) {
        setTimeout(poll, intervalMs);
      }
    };
    setTimeout(poll, intervalMs);
    return () => { active = false; };
  }, [intervalMs, enabled]);
}
