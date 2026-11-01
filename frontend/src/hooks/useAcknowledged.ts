import { useCallback, useState } from 'react';

function read(key: string): Set<string> {
  try {
    return new Set(JSON.parse(localStorage.getItem(key) ?? '[]') as string[]);
  } catch {
    return new Set();
  }
}

/**
 * The backend has no read state for notifications, so acknowledgements are kept per
 * browser and per user. They do not follow the user to another device.
 */
export function useAcknowledged(userId: string) {
  const key = `alertpulse.acknowledged.${userId}`;
  const [acknowledged, setAcknowledged] = useState(() => read(key));

  const acknowledge = useCallback(
    (ids: string[]) => {
      setAcknowledged((current) => {
        const next = new Set([...current, ...ids]);
        try {
          localStorage.setItem(key, JSON.stringify([...next]));
        } catch {
          // Still acknowledged for this page load if storage is blocked.
        }
        return next;
      });
    },
    [key],
  );

  return { acknowledged, acknowledge };
}
