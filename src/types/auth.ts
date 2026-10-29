export interface UserProfile {
  id: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

export interface LoginRequestDto {
  username: string
  password: string
}

export interface RegisterRequestDto {
  username: string
  email: string
  password: string
}

export interface RegisterResponseDto {
  /** log 本地降级模式下由后端直接返回，用于前端跳转激活页；smtp 模式为 null */
  activationToken: string | null
}

export interface RoleDto {
  id: string
  code: string
  name: string
  description: string
  permissions: string[]
}

export interface UserListItemDto {
  id: string
  username: string
  displayName: string
  roleId: string
  roleCode: string
  roleName: string
  enabled: boolean
  createdAt: string
}

export interface UserOptionDto {
  id: string
  username: string
  displayName: string
}

export interface UserListQueryDto {
  page: number
  pageSize: number
  keyword?: string
  roleId?: string
  enabled?: boolean
}

export interface AuthTokenDto {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export type LoginResponseDto = AuthTokenDto

export interface PermissionOption { code: string; name: string; group: string }
export interface RoleSaveDto { name: string; description: string; permissions: string[] }
