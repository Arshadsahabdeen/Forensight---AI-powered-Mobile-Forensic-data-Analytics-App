import { api } from './api';
import type { LoginResponse, RefreshTokenResponse, User } from '../types/auth';

export async function login(username: string, password: string) {
  const { data } = await api.post<LoginResponse>('/auth/login', { username, password });
  return data;
}

export async function refresh(refreshToken: string) {
  const { data } = await api.post<RefreshTokenResponse>('/auth/refresh', { refreshToken });
  return data;
}

export async function getCurrentUser() {
  const { data } = await api.get<User>('/auth/me');
  return data;
}

export async function logout(refreshToken?: string | null) {
  await api.post('/auth/logout', { refreshToken });
}
