import type { RoleDto, UserListItemDto, UserProfile } from '@/types/auth'
import type { MenuDataDto, MenuItemDto } from '@/types/menu'

type MenuTemplateKey = 'full' | 'staff' | 'member' | 'guest'

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
    'ticket:claim',
    'ticket:claim:approve',
  ],
  staff: [
    'dashboard:view',
    'ticket:view',
    'ticket:create',
    'ticket:assign',
    'ticket:transfer',
    'ticket:comment',
    'ticket:advance',
    'ticket:claim:approve',
    'asset:view',
    'asset:claim',
    'agent:chat',
    'agent:task',
  ],
  guest: ['agent:chat'],
  member: ['ticket:create', 'ticket:claim', 'agent:chat'],
}

const homeMenu = (id: string): MenuItemDto => ({
  id,
  title: '工作台',
  name: 'Home',
  path: '/home',
  component: 'HomeView',
  icon: 'home',
  permission: 'agent:chat',
  keepAlive: true,
})

const opsMenu = (id: string, includeAssets: boolean): MenuItemDto => ({
  id,
  title: '运维',
  name: 'OpsRoot',
  path: '/ops',
  component: 'RouteView',
  icon: 'ticket',
  children: [
    {
      id: `${id}-dashboard`,
      title: '运维看板',
      name: 'Dashboard',
      path: '/ops/dashboard',
      component: 'DashboardView',
      permission: 'dashboard:view',
      keepAlive: true,
    },
    {
      id: `${id}-tickets`,
      title: '工单列表',
      name: 'TicketList',
      path: '/ops/ticket/list',
      component: 'TicketListView',
      permission: 'ticket:view',
      keepAlive: true,
    },
    ...(includeAssets
      ? [{
          id: `${id}-assets`,
          title: '资产列表',
          name: 'AssetList',
          path: '/ops/asset/list',
          component: 'AssetListView',
          permission: 'asset:view',
          keepAlive: true,
        }]
      : []),
  ],
})

const menuTemplates: Record<MenuTemplateKey, MenuItemDto[]> = {
  full: [
    homeMenu('home'),
    opsMenu('ops', true),
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
    homeMenu('staff-home'),
    opsMenu('staff-ops', true),
    { id: 'staff-agent-console', title: '智能体工作台', name: 'AgentConsole', path: '/agent/console', component: 'AgentConsoleView', icon: 'robot', permission: 'agent:task', keepAlive: true },
    { id: 'staff-agent-tasks', title: '任务中心', name: 'AgentTasks', path: '/agent/tasks', component: 'AgentTasksView', icon: 'robot', permission: 'agent:task', keepAlive: true },
  ],
  member: [
    homeMenu('member-home'),
    { id: 'member-claim', title: '待接工单', name: 'ClaimQueue', path: '/ops/ticket/claim', component: 'ClaimQueueView', icon: 'ticket', permission: 'ticket:claim', keepAlive: true },
  ],
  guest: [homeMenu('guest-home')],
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
  const user = users[username] || dynamicUsers.get(username)
  const roleCode = user?.roles?.[0]
  if (roleCode === 'guest') {
    return cloneMenu(menuTemplates.guest)
  }
  if (roleCode === 'member') {
    return cloneMenu(menuTemplates.member)
  }
  if (roleCode === 'staff') {
    return cloneMenu(menuTemplates.staff)
  }
  return cloneMenu(menuTemplates.full)
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
const resolvePermissions = (username: string, roleCode: string): string[] =>
  permissionMap[username] ?? permissionMap[roleCode] ?? []

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
