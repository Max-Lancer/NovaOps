import request from '@/utils/request'
import type { AuthTokenDto, LoginRequestDto, LoginResponseDto, RegisterRequestDto, RegisterResponseDto, RoleDto, UserListItemDto, UserListQueryDto, UserOptionDto, UserProfile } from '@/types/auth'
import type { PageResult } from '@/types/api'
import type { MenuDataDto } from '@/types/menu'

export const loginApi = (payload: LoginRequestDto) => {
  return request.post<LoginResponseDto, LoginRequestDto>('/auth/login', payload, {
    skipAuthRefresh: true,
  })
}

export const registerApi = (payload: RegisterRequestDto) => {
  return request.post<RegisterResponseDto, RegisterRequestDto>('/auth/register', payload, {
    skipAuthRefresh: true,
  })
}

export const verifyApi = (token: string) => {
  return request.get<void>('/auth/verify', {
    params: { token },
    skipAuthRefresh: true,
  })
}

export const refreshTokenApi = (refreshToken: string) => {
  return request.post<AuthTokenDto, { refreshToken: string }>(
    '/auth/refresh',
    { refreshToken },
    { skipAuthRefresh: true }
  )
}

export const meApi = () => {
  return request.get<UserProfile>('/auth/me')
}

export const menuApi = () => {
  return request.get<MenuDataDto>('/auth/menu')
}

export const getRolesApi = () => {
  return request.get<RoleDto[]>('/auth/roles')
}

export const getUsersApi = (params: UserListQueryDto) => request.get<PageResult<UserListItemDto>>('/auth/users', { params })
export const getUserOptionsApi = () => request.get<UserOptionDto[]>('/auth/user-options')
export const updateUserStatusApi = (id: string, enabled: boolean) => request.put<void, { enabled: boolean }>(`/auth/users/${id}/status`, { enabled })
export const updateUserRoleApi = (id: string, roleId: string) => request.put<void, { roleId: string }>(`/auth/users/${id}/role`, { roleId })
export const resetUserPasswordApi = (id: string, password: string) => request.put<void, { password: string }>(`/auth/users/${id}/password`, { password })

export const getPermissionCatalogApi = () => request.get<import('@/types/auth').PermissionOption[]>('/auth/permissions')
export const createRoleApi = (data: import('@/types/auth').RoleSaveDto) => request.post<void, import('@/types/auth').RoleSaveDto>('/auth/roles', data)
export const updateRoleApi = (id: string, data: import('@/types/auth').RoleSaveDto) => request.put<void, import('@/types/auth').RoleSaveDto>(`/auth/roles/${id}`, data)
export const deleteRoleApi = (id: string) => request.delete<void>(`/auth/roles/${id}`)
