import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Recent Node versions ship an experimental global localStorage that can shadow jsdom's and be undefined
// without --localstorage-file. Provide a spec-compatible in-memory Storage for tests when needed.
class MemoryStorage implements Storage {
  private store = new Map<string, string>();
  get length() {
    return this.store.size;
  }
  clear() {
    this.store.clear();
  }
  getItem(key: string) {
    return this.store.has(key) ? this.store.get(key)! : null;
  }
  key(index: number) {
    return Array.from(this.store.keys())[index] ?? null;
  }
  removeItem(key: string) {
    this.store.delete(key);
  }
  setItem(key: string, value: string) {
    this.store.set(key, String(value));
  }
}

for (const name of ['localStorage', 'sessionStorage'] as const) {
  let usable = false;
  try {
    usable = typeof window[name]?.getItem === 'function';
  } catch {
    usable = false;
  }
  if (!usable) {
    Object.defineProperty(window, name, { configurable: true, value: new MemoryStorage() });
    Object.defineProperty(globalThis, name, { configurable: true, value: window[name] });
  }
}

afterEach(() => {
  cleanup();
  window.localStorage.clear();
  window.sessionStorage.clear();
});
