import { afterEach, describe, expect, it } from 'vitest';
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import { api, storeTokens } from '../services/api';

const originalAdapter = api.defaults.adapter;

afterEach(() => {
  api.defaults.adapter = originalAdapter;
});

function respond(config: InternalAxiosRequestConfig, status: number, data: unknown) {
  const response = { data, status, statusText: String(status), headers: {}, config };
  if (status >= 400) {
    const error = Object.assign(new Error(`HTTP ${status}`), { isAxiosError: true, config, response });
    return Promise.reject(error);
  }
  return Promise.resolve(response);
}

describe('API client authentication', () => {
  it('attaches the bearer token to API calls but not to login', async () => {
    storeTokens('access-1', 'refresh-1');
    const seen: Record<string, string | undefined> = {};
    api.defaults.adapter = ((config) => {
      seen[config.url!] = config.headers?.Authorization as string | undefined;
      return respond(config, 200, {});
    }) as AxiosAdapter;

    await api.get('/cases');
    await api.post('/auth/login', {});
    expect(seen['/cases']).toBe('Bearer access-1');
    expect(seen['/auth/login']).toBeUndefined();
  });

  it('refreshes an expired access token once and retries the request', async () => {
    storeTokens('expired', 'refresh-1');
    const calls: string[] = [];
    api.defaults.adapter = ((config) => {
      const auth = config.headers?.Authorization as string | undefined;
      calls.push(`${config.url} ${auth ?? ''}`.trim());
      if (config.url === '/auth/refresh') return respond(config, 200, { accessToken: 'access-2', refreshToken: 'refresh-2' });
      if (auth === 'Bearer expired') return respond(config, 401, { message: 'expired' });
      return respond(config, 200, { ok: true });
    }) as AxiosAdapter;

    const { data } = await api.get('/dashboard');
    expect(data).toEqual({ ok: true });
    expect(calls).toEqual(['/dashboard Bearer expired', '/auth/refresh', '/dashboard Bearer access-2']);
    expect(window.localStorage.getItem('forensic_refresh_token')).toBe('refresh-2');
  });

  it('ends the session with an explanation when refresh is rejected', async () => {
    storeTokens('expired', 'refresh-1');
    api.defaults.adapter = ((config) => {
      if (config.url === '/auth/refresh') return respond(config, 401, { message: 'Session expired due to inactivity. Please sign in again.' });
      return respond(config, 401, {});
    }) as AxiosAdapter;

    await expect(api.get('/cases')).rejects.toBeTruthy();
    expect(window.localStorage.getItem('forensic_access_token')).toBeNull();
    expect(window.sessionStorage.getItem('forensight_session_notice')).toBe('Session expired due to inactivity. Please sign in again.');
  });
});
