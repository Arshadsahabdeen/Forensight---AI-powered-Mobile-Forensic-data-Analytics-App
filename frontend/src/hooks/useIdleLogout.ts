import { useEffect } from 'react';
import { endSession } from '../services/api';

const EVENTS = ['mousedown', 'keydown', 'scroll', 'touchstart'] as const;

/** Signs the user out after the administrator-configured period of inactivity. */
export function useIdleLogout(timeoutMinutes: number | undefined) {
  useEffect(() => {
    if (!timeoutMinutes || timeoutMinutes <= 0) return;
    let timer = window.setTimeout(expire, timeoutMinutes * 60_000);
    function expire() {
      endSession(`You were signed out after ${timeoutMinutes} minutes of inactivity.`);
    }
    function reset() {
      window.clearTimeout(timer);
      timer = window.setTimeout(expire, timeoutMinutes! * 60_000);
    }
    EVENTS.forEach((name) => window.addEventListener(name, reset, { passive: true }));
    return () => {
      window.clearTimeout(timer);
      EVENTS.forEach((name) => window.removeEventListener(name, reset));
    };
  }, [timeoutMinutes]);
}
