import api from './client'

export interface LoginReq { username: string; password: string }
export interface LoginRes { token: string; adminId: string; username: string; role: string }

export const authApi = {
  login: (req: LoginReq) => api.post<unknown, LoginRes>('/auth/login', req),
}