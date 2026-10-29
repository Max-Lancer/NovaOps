import type { RoleDto, UserListItemDto, UserProfile } from '@/types/auth'
import type { MenuDataDto, MenuItemDto } from '@/types/menu'

type MenuTemplateKey = 'full' | 'staff' | 'guest'

interface MockUser {
  id: string
  username: string
  password: string
  displayName: string
  roles: string[]
  enabled: boolean
  createdAt: string
}

interface SessionPayload {
  username: string
}

const users: Record<string, MockUser> = {
  admin: {
    id: 'u-admin',
    username: 'admin',
    password: '123456',
    displayName: 'System Admin',
    roles: ['admin'],
    enabled: true,
    createdAt: '2026-04-01T08:00:00Z',
  },
  staff: {
    id: 'u-staff',
    username: 'staff',
    password: '123456',
    displayName: 'Support Staff',
    roles: ['staff'],
    enabled: true,
    createdAt: '2026-04-02T08:00:00Z',
  },
  guest: {
    id: 'u-guest',
    username: 'guest',
    password: '123456',
    displayName: 'Read-only Guest',
    roles: ['guest'],
    enabled: true,
    createdAt: '2026-04-03T08:00:00Z',
  },
  tom: {
    id: 'u-tom',
    username: 'tom',
    password: '123456',
    displayName: 'Tom',
    roles: ['staff'],
    enabled: true,
    createdAt: '2026-04-04T08:00:00Z',
  },
  jerry: {
    id: 'u-jerry',
    username: 'jerry',
    password: '123456',
    displayName: 'Jerry',
    roles: ['staff'],
    enabled: true,
    createdAt: '2026-04-05T08:00:00Z',
  },
  alice: {
    id: 'u-alice',
    username: 'alice',
    password: '123456',
    displayName: 'Alice',
    roles: ['staff'],
    enabled: true,
    createdAt: '2026-04-06T08:00:00Z',
  },
}

// 动态注册的用户
const dynamicUsers = new Map<string, MockUser>()

export const roles: RoleDto[] = [
  { id: 'role-admin', code: 'admin', name: '管理员', description: '管理用户、身份、知识库以及全部业务数据', permissions: ['auth:user:manage', 'dashboard:view', 'ticket:view', 'asset:view', 'kb:view', 'kb:edit', 'agent:chat'] },
  { id: 'role-staff', code: 'staff', name: '运维人员', description: '处理工单、资产与使用智能问答', permissions: ['dashboard:view', 'ticket:view', 'ticket:create', 'asset:view', 'agent:chat'] },
  { id: 'role-guest', code: 'guest', name: '访客', description: '只读访问授权看板与智能问答', permissions: ['dashboard:view', 'agent:chat'] },
  { id: 'role-member', code: 'member', name: '普通成员', description: '注册用户默认身份：只读看板、提交工单与智能问答', permissions: ['dashboard:view', 'ticket:create', 'agent:chat'] },
]

// 注册验证（mock 内存实现）：token -> username
const verificationTokens = new Map<string, string>()
const registeredEmails = new Set<string>()

export const registerMockUser = (username: string, email: string, password: string): string => {
  if (users[username] || dynamicUsers.get(username)) {
    throw new Error('账号已存在')
  }
  if (registeredEmails.has(email)) {
    throw new Error('邮箱已被注册')
  }
  registeredEmails.add(email)
  const user: MockUser = {
    id: `u-reg-${Date.now()}`,
    username,
    password,
    displayName: username,
    roles: ['member'],
    enabled: false,
    createdAt: new Date().toISOString(),
  }
  dynamicUsers.set(username, user)
  const token = `mock-ev-${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  verificationTokens.set(token, username)
  // 模拟 LogEmailSender：激活链接打印到浏览器控制台，同时把 token 交回注册接口
  console.log(`[激活邮件] 收件人=${email} 激活链接=http://localhost:5173/verify?token=${token}`)
  return token
}

export const verifyMockUser = (token: string): boolean => {
  const username = verificationTokens.get(token)
  if (!username) {
    return false
  }
  const user = dynamicUsers.get(username)
  if (!user) {
    return false
  }
  user.enabled = true
  verificationTokens.delete(token)
  return true
}

const permissionMap: Record<string, string[]> = {
  admin: [
    'dashboard:view',
    'ticket:view',
    'ticket:create',
    'ticket:edit',
    'ticket:assign',
    'ticket:transfer',
    'ticket:close',
    'ticket:comment',
    'ticket:advance',
    'ticket:approve',
    'ticket:reject',
    'asset:view',
    'asset:create',
    'asset:edit',
    'asset:claim',
    'asset:scrap',
    'kb:view',
    'kb:edit',
    'auth:user:manage',
    'agent:chat',
    'agent:task',
  ],
  staff: [
    'dashboard:view',
    'ticket:view',
    'ticket:create',
    'ticket:assign',
    'ticket:transfer',
    'ticket:comment',
    'ticket:advance',
    'asset:view',
    'asset:claim',
    'agent:chat',
    'agent:task',
  ],
  guest: ['dashboard:view', 'agent:chat'],
  member: ['dashboard:view', 'ticket:create', 'agent:chat'],
}

// 权限目录与后端允许配置的权限保持一致；Mock 数据仅保存在当前页面会话内。
const labels: Record<string, string> = {
  'dashboard:view': '访问看板', 'ticket:view': '访问工单', 'ticket:create': '新建工单', 'ticket:edit': '编辑工单',
  'ticket:assign': '指派工单', 'ticket:transfer': '转派工单', 'ticket:close': '关闭工单', 'ticket:comment': '评论工单',
  'ticket:advance': '提交复核', 'ticket:approve': '复核通过', 'ticket:reject': '驳回复核', 'asset:view': '访问资产',
  'asset:create': '新增资产', 'asset:edit': '编辑资产', 'asset:claim': '领用与接收资产', 'asset:scrap': '报废资产',
  'kb:view': '访问知识库', 'kb:edit': '维护知识库文档', 'agent:chat': '使用智能问答', 'agent:task': '使用智能体任务',
}
const groupNames: Record<string, string> = { dashboard: '看板', ticket: '工单', asset: '资产', kb: '知识库', agent: '智能体' }
export const mockPermissionCatalog = Object.entries(labels).map(([code, name]) => ({ code, name, group: groupNames[code.split(':')[0]!]! }))
roles.forEach(role => { role.permissions = [...(permissionMap[role.code] ?? [])] })
export const saveMockRole = (id: string | undefined, data: { name: string; description: string; permissions: string[] }) => {
  if (!data.name?.trim() || data.name.length > 100 || data.description.length > 255) throw new Error('角色名称或说明不合法')
  const codes = new Set(data.permissions)
  if (codes.size !== data.permissions.length || [...codes].some(code => !labels[code])) throw new Error('权限包含重复项或系统不支持的权限')
  for (const prefix of ['ticket:', 'asset:', 'kb:']) {
    if ([...codes].some(code => code.startsWith(prefix) && code !== prefix + 'view') && !codes.has(prefix + 'view')) throw new Error('配置业务操作前，请先授予对应页面的访问权限：' + prefix + 'view')
  }
  if (id === 'role-admin') throw new Error('系统管理员角色不可修改')
  const role = id ? roles.find(item => item.id === id) : undefined
  if (id && !role) throw new Error('角色不存在')
  if (role) Object.assign(role, { name: data.name.trim(), description: data.description.trim(), permissions: [...codes] })
  else { const code = `role-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`; roles.push({ id: code, code, name: data.name.trim(), description: data.description.trim(), permissions: [...codes] }) }
}
export const deleteMockRole = (id: string) => {
  if (['role-admin', 'role-staff', 'role-guest', 'role-member'].includes(id)) throw new Error('系统预设角色不可删除')
  const index = roles.findIndex(role => role.id === id)
  if (index < 0) throw new Error('角色不存在')
  if (allUsers().some(user => user.roles.includes(roles[index]!.code))) throw new Error('角色仍有关联用户，请先调整用户身份')
  roles.splice(index, 1)
}

const menuTemplates: Record<MenuTemplateKey, MenuItemDto[]> = {
  full: [
    {
      id: 'dashboard',
      title: 'Dashboard',
      name: 'Dashboard',
      path: '/dashboard',
      component: 'DashboardView',
      icon: 'dashboard',
      permission: 'dashboard:view',
      keepAlive: true,
    },
    {
      id: 'ticket',
      title: '工单',
      name: 'TicketRoot',
      path: '/ticket',
      component: 'RouteView',
      icon: 'ticket',
      children: [
        {
          id: 'ticket-list',
          title: '工单列表',
          name: 'TicketList',
          path: '/ticket/list',
          component: 'TicketListView',
          permission: 'ticket:view',
          keepAlive: true,
        },
      ],
    },
    {
      id: 'asset',
      title: '资产',
      name: 'AssetRoot',
      path: '/asset',
      component: 'RouteView',
      icon: 'asset',
      children: [
        {
          id: 'asset-list',
          title: '资产列表',
          name: 'AssetList',
          path: '/asset/list',
          component: 'AssetListView',
          permission: 'asset:view',
          keepAlive: true,
        },
      ],
    },
    {
      id: 'kb',
      title: '知识库',
      name: 'KbRoot',
      path: '/kb',
      component: 'RouteView',
      icon: 'kb',
      children: [
        {
          id: 'kb-list',
          title: '文章列表',
          name: 'KbList',
          path: '/kb/list',
          component: 'KbListView',
          permission: 'kb:view',
          keepAlive: true,
        },
      ],
    },
    { id: 'agent-console', title: '智能体工作台', name: 'AgentConsole', path: '/agent/console', component: 'AgentConsoleView', icon: 'robot', permission: 'agent:task', keepAlive: true },
    { id: 'agent-tasks', title: '任务中心', name: 'AgentTasks', path: '/agent/tasks', component: 'AgentTasksView', icon: 'robot', permission: 'agent:task', keepAlive: true },
    { id: 'users', title: '用户与身份', name: 'UserManagement', path: '/system/users', component: 'UserManagementView', icon: 'user', permission: 'auth:user:manage', keepAlive: true },
  ],
  staff: [
    {
      id: 'dashboard',
      title: 'Dashboard',
      name: 'Dashboard',
      path: '/dashboard',
      component: 'DashboardView',
      icon: 'dashboard',
      permission: 'dashboard:view',
      keepAlive: true,
    },
    {
      id: 'ticket',
      title: '工单',
      name: 'TicketRoot',
      path: '/ticket',
      component: 'RouteView',
      icon: 'ticket',
      children: [
        {
          id: 'ticket-list',
          title: '工单列表',
          name: 'TicketList',
          path: '/ticket/list',
          component: 'TicketListView',
          permission: 'ticket:view',
          keepAlive: true,
        },
      ],
    },
  ],
  guest: [
    {
      id: 'dashboard',
      title: 'Dashboard',
      name: 'Dashboard',
      path: '/dashboard',
      component: 'DashboardView',
      icon: 'dashboard',
      permission: 'dashboard:view',
      keepAlive: true,
    },
  ],
}

const accessTokenTable = new Map<string, SessionPayload>()
const refreshTokenTable = new Map<string, SessionPayload>()

const createToken = (prefix: 'at' | 'rt', payload: SessionPayload) => {
  return `${prefix}_${payload.username}_${Date.now()}_${Math.random()
    .toString(36)
    .slice(2, 8)}`
}

const decodeBase64Url = (value: string) => {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
  return atob(padded)
}

const parseJwtSession = (token: string): SessionPayload | null => {
  const parts = token.split('.')
  if (parts.length !== 3) {
    return null
  }

  try {
    const payload = JSON.parse(decodeBase64Url(parts[1] || '')) as {
      username?: string
    }
    const username = payload.username
    if (!username) {
      return null
    }
    return { username }
  } catch {
    return null
  }
}

const cloneMenu = (menus: MenuItemDto[]): MenuItemDto[] => {
  return menus.map((item) => ({
    ...item,
    children: item.children ? cloneMenu(item.children) : undefined,
  }))
}

const resolveMenusByUser = (username: string): MenuItemDto[] => {
  const roleCode = (users[username] || dynamicUsers.get(username))?.roles[0] || 'guest'
  const permissions = resolvePermissions(username, roleCode)
  const filter = (menus: MenuItemDto[]): MenuItemDto[] => menus.flatMap(menu => {
    const children = menu.children ? filter(menu.children) : undefined
    if (menu.component === 'RouteView' && !children?.length) return []
    if (menu.permission && !permissions.includes(menu.permission)) return []
    return [{ ...menu, children }]
  })
  return filter(cloneMenu(menuTemplates.full))
}

export const getUser = (username: string) => {
  return users[username] || dynamicUsers.get(username)
}

const allUsers = () => [...Object.values(users), ...dynamicUsers.values()]

export const listMockUsers = (): UserListItemDto[] => allUsers().map((user) => {
  const role = roles.find((item) => item.code === user.roles[0]) || roles[2]!
  return { id: user.id, username: user.username, displayName: user.displayName, roleId: role.id, roleCode: role.code, roleName: role.name, enabled: user.enabled, createdAt: user.createdAt }
})

export const setMockUserStatus = (id: string, enabled: boolean) => {
  const user = allUsers().find((item) => item.id === id)
  if (user) user.enabled = enabled
  return Boolean(user)
}

export const setMockUserRole = (id: string, roleId: string) => {
  const user = allUsers().find((item) => item.id === id)
  const role = roles.find((item) => item.id === roleId)
  if (user && role) user.roles = [role.code]
  return Boolean(user && role)
}

export const setMockUserPassword = (id: string, password: string) => {
  const user = allUsers().find((item) => item.id === id)
  if (user) user.password = password
  return Boolean(user)
}

export const buildSession = (payload: SessionPayload) => {
  const accessToken = createToken('at', payload)
  const refreshToken = createToken('rt', payload)
  accessTokenTable.set(accessToken, payload)
  refreshTokenTable.set(refreshToken, payload)
  return {
    accessToken,
    refreshToken,
    expiresIn: 1800,
  }
}

export const refreshSession = (refreshToken: string) => {
  const payload = refreshTokenTable.get(refreshToken)
  if (!payload) {
    return null
  }
  const accessToken = createToken('at', payload)
  accessTokenTable.set(accessToken, payload)
  return {
    accessToken,
    refreshToken,
    expiresIn: 1800,
  }
}

export const getSessionFromAccessToken = (authorization: string | null) => {
  if (!authorization?.startsWith('Bearer ')) {
    return null
  }
  const token = authorization.replace('Bearer ', '').trim()
  const mockSession = accessTokenTable.get(token)
  if (mockSession) {
    return mockSession
  }
  return parseJwtSession(token)
}

// 按「用户名 → 角色码」的优先级取权限表；未知角色兜底为空权限，
// 绝不能兜底 admin——否则脏数据会凭空获得管理员权限
const resolvePermissions = (_username: string, roleCode: string): string[] =>
  roles.find(role => role.code === roleCode)?.permissions ?? []

export const buildUserProfile = (username: string): UserProfile => {
  const user = users[username] || dynamicUsers.get(username)
  if (!user) {
    throw new Error(`User not found: ${username}`)
  }
  const roleCode = user.roles[0] || 'guest'
  const perms = resolvePermissions(username, roleCode)
  return {
    id: user.id,
    username: user.username,
    displayName: user.displayName,
    roles: user.roles,
    permissions: perms,
  }
}

export const buildMenuData = (username: string): MenuDataDto => {
  const user = users[username] || dynamicUsers.get(username)
  const roleCode = user?.roles?.[0] || 'guest'
  const perms = resolvePermissions(username, roleCode)
  return {
    menus: resolveMenusByUser(username),
    permissions: perms,
  }
}
