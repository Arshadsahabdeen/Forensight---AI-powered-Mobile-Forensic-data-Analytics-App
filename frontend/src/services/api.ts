import axios, { AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';

const ACCESS_TOKEN_KEY = 'forensic_access_token';
const REFRESH_TOKEN_KEY = 'forensic_refresh_token';
const USER_KEY = 'forensic_user';
export const SESSION_NOTICE_KEY = 'forensight_session_notice';

function normalizeBaseUrl(url: string) {
  return url.endsWith('/') ? url.slice(0, -1) : url;
}

export const api = axios.create({
  baseURL: normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL ?? '/api/v1'),
  headers: { 'Content-Type': 'application/json' },
  timeout: 120_000
});

let refreshPromise: Promise<string> | null = null;

function storage(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

export function getAccessToken() {
  return storage()?.getItem(ACCESS_TOKEN_KEY) ?? null;
}

export function getRefreshToken() {
  return storage()?.getItem(REFRESH_TOKEN_KEY) ?? null;
}

export function storeTokens(accessToken: string, refreshToken: string) {
  storage()?.setItem(ACCESS_TOKEN_KEY, accessToken);
  storage()?.setItem(REFRESH_TOKEN_KEY, refreshToken);
  storage()?.removeItem('forensic_token');
}

export function clearStoredAuth() {
  const s = storage();
  s?.removeItem(ACCESS_TOKEN_KEY);
  s?.removeItem(REFRESH_TOKEN_KEY);
  s?.removeItem(USER_KEY);
  s?.removeItem('forensic_token');
}

/** Ends the local session and returns to the sign-in page with an explanation. */
export function endSession(notice: string) {
  clearStoredAuth();
  try {
    window.sessionStorage.setItem(SESSION_NOTICE_KEY, notice);
  } catch {
    // sessionStorage unavailable; the login page simply shows no notice
  }
  if (!window.location.pathname.startsWith('/login')) {
    window.location.assign('/login');
  }
}

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  const url = config.url ?? '';
  if (token && !url.includes('/auth/login') && !url.includes('/auth/refresh')) {
    config.headers = AxiosHeaders.from(config.headers);
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const status = error.response?.status;
    const isAuthCall = original?.url?.includes('/auth/login') || original?.url?.includes('/auth/refresh');

    if (status === 401 && original && !original._retry && !isAuthCall) {
      original._retry = true;
      const refreshToken = getRefreshToken();
      if (!refreshToken) {
        endSession('Your session has ended. Please sign in again.');
        return Promise.reject(error);
      }
      try {
        refreshPromise ??= api
          .post('/auth/refresh', { refreshToken })
          .then(({ data }) => {
            storeTokens(data.accessToken, data.refreshToken);
            return data.accessToken as string;
          })
          .finally(() => {
            refreshPromise = null;
          });
        const accessToken = await refreshPromise;
        original.headers = AxiosHeaders.from(original.headers);
        original.headers.set('Authorization', `Bearer ${accessToken}`);
        return api(original);
      } catch (refreshError) {
        const message = axios.isAxiosError(refreshError) ? refreshError.response?.data?.message : undefined;
        endSession(typeof message === 'string' ? message : 'Your session expired. Please sign in again.');
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  }
);

/** Downloads an authenticated file (PDF, CSV, original evidence) using the server-provided file name. */
export async function downloadFile(url: string, fallbackName: string, params?: Record<string, unknown>) {
  const response = await api.get(url, { responseType: 'blob', params });
  const disposition = String(response.headers['content-disposition'] ?? '');
  const match = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(disposition);
  const name = match ? decodeURIComponent(match[1] ?? match[2]) : fallbackName;
  const href = URL.createObjectURL(response.data as Blob);
  const link = document.createElement('a');
  link.href = href;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(href), 1000);
}
