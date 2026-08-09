import { api } from './api';
import type { CaseDetailResponse, CasePageResponse, CaseRequest, CaseResponse } from '../types/cases';

export interface CaseQueryParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  search?: string;
  status?: string;
  priority?: string;
}

export async function getCases(params: CaseQueryParams) {
  const { data } = await api.get<CasePageResponse>('/cases', { params });
  return data;
}

export async function getCaseById(id: number | string) {
  const { data } = await api.get<CaseDetailResponse>(`/cases/${id}`);
  return data;
}

export async function createCase(payload: CaseRequest) {
  const { data } = await api.post<CaseResponse>('/cases', payload);
  return data;
}

export async function updateCase(id: number | string, payload: CaseRequest) {
  const { data } = await api.put<CaseResponse>(`/cases/${id}`, payload);
  return data;
}

export async function deleteCase(id: number | string) {
  await api.delete(`/cases/${id}`);
}
