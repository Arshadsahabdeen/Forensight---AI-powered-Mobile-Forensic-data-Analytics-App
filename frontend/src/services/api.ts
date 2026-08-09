import axios, { AxiosHeaders } from 'axios';

const ACCESS_TOKEN_KEY = 'forensic_access_token';
const REFRESH_TOKEN_KEY = 'forensic_refresh_token';
const USER_KEY = 'forensic_user';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  headers: {
    'Content-Type': 'application/json'
  }
});

let refreshPromise: Promise<string> | null = null;

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY) ?? localStorage.getItem('forensic_token');
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function storeTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  localStorage.removeItem('forensic_token');
}

export function clearStoredAuth() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  localStorage.removeItem('forensic_token');
}

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  const requestUrl = config.url ?? '';
  const isAuthEndpoint = requestUrl.includes('/auth/login') || requestUrl.includes('/auth/refresh');

  if (token && !isAuthEndpoint) {
    config.headers = AxiosHeaders.from(config.headers);
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config as any;
    const status = error.response?.status;
    const isAuthCall = original?.url?.includes('/auth/login') || original?.url?.includes('/auth/refresh');

    if (status === 401 && original && !original._retry && !isAuthCall) {
      original._retry = true;
      const refreshToken = getRefreshToken();
      if (!refreshToken) {
        clearStoredAuth();
        window.location.href = '/login';
        return Promise.reject(error);
      }
      try {
        refreshPromise ??= api.post('/auth/refresh', { refreshToken }).then(({ data }) => {
          storeTokens(data.accessToken, data.refreshToken);
          return data.accessToken;
        }).finally(() => {
          refreshPromise = null;
        });
        const accessToken = await refreshPromise;
        original.headers.Authorization = `Bearer ${accessToken}`;
        return api(original);
      } catch (refreshError) {
        clearStoredAuth();
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  }
);
