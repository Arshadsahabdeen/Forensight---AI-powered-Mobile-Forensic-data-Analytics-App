export type Role = 'ADMIN' | 'INVESTIGATOR' | 'REVIEWER';

export interface User {
  id: number;
  username: string;
  fullName: string;
  email: string;
  roles: Role[];
  updatedAt: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: User;
}

export interface RefreshTokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}
