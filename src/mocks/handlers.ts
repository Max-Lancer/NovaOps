import { delay, http, HttpResponse, passthrough } from 'msw'
import type { AuthTokenDto, LoginRequestDto, LoginResponseDto, UserProfile } from '@/types/auth'
import type { MenuDataDto } from '@/types/menu'
import type { ConversationDto } from '@/types/agent'
import type {
  AssetActionDto,
  AssetDetailDto,
  AssetListQueryDto,
  CreateAssetDto,
  UpdateAssetDto,
} from '@/types/asset'
import type {
  CreateCommentDto,
  CreateTicketDto,
  TicketActionDto,
  TicketDetailDto,
  TicketListQueryDto,
  UpdateTicketDto,
} from '@/types/ticket'
import type { KbChunkDto, KbDocumentDto, KbListQueryDto, SaveKbDto } from '@/types/kb'
import {
  buildMenuData,
  buildSession,
  buildUserProfile,
  getSessionFromAccessToken,
  getUser,
  refreshSession,
  roles,
  listMockUsers,
  registerMockUser,
  setMockUserPassword,
  setMockUserRole,
  setMockUserStatus,
  verifyMockUser,
} from './db'
import {
  getKbDetail,
  getKbVersions,
  queryKbList,
  saveKb,
} from './kbDb'
import {
  actionAsset,
  createAsset,
  getAssetDetail,
  getAssetsByIds,
  queryAssets,
  updateAsset,
} from './assetDb'
import {
  actionTicket,
  createTicket,
  createTicketComment,
  getTicketDetail,
  listRelatedTicketsByAsset,
  listTicketComments,
  queryTickets,
  queryClaimQueue,
  buildTicketMetrics,
  updateTicket,
  uploadTicketAttachment,
} from './ticketDb'

const ok = <T>(data: T, message = 'ok') => {
  return HttpResponse.json({
    code: 0,
    message,
    data,
  })
}

const fail = (code: number, message: string) => {
  return HttpResponse.json({
    code,
    message,
    data: null,
  })
}

const mockMode = (import.meta.env.VITE_ENABLE_MOCK || 'full').toLowerCase()
const shouldPassthroughTicketBackend = mockMode === 'partial'
const mockDocuments: KbDocumentDto[] = [{ id:'mock-doc-1',title:'NovaOps 使用手册',fileName:'novaops-guide.md',fileType:'md',fileSize:4096,status:'READY',chunkCount:2,createdBy:'u-admin',createdAt:new Date().toISOString(),updatedAt:new Date().toISOString() }]
const mockChunks: Record<string,KbChunkDto[]> = { 'mock-doc-1': [{id:'chunk-1',documentId:'mock-doc-1',chunkIndex:0,content:'NovaOps 是企业知识与运维协作平台。',vectorId:'vector-1'},{id:'chunk-2',documentId:'mock-doc-1',chunkIndex:1,content:'知识库文档支持 RAG 检索并附带来源引用。',vectorId:'vector-2'}] }
const mockConversations: ConversationDto[] = []
// 分片上传会话（Mock）：记录已接收的分片序号，用于验证续传进度与秒传判定
const mockUploadSessions = new Map<string,{fileName:string;fileSize:number;contentHash:string;chunks:Set<number>}>()
const getSession = (request: Request) => {
  return getSessionFromAccessToken(request.headers.get('Authorization'))
}

export const handlers = [
  http.post('/api/auth/login', async ({ request }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(350)
    const payload = (await request.json()) as LoginRequestDto

    const user = getUser(payload.username)
    if (!user) {
      // 用户不存在 → 模糊报错避免账号枚举（与后端一致，不再自助注册）
      return fail(403, '账号或密码错误')
    }
    if (payload.password !== user.password) {
      return fail(403, '账号或密码错误')
    }
    if (!user.enabled) return fail(403, '账号未激活或已被禁用')

    const session = buildSession({ username: user.username })
    const data: LoginResponseDto = { ...session }
    return ok(data, '登录成功')
  }),

  http.post('/api/auth/register', async ({ request }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(300)
    const payload = (await request.json()) as { username: string; email: string; password: string }
    try {
      const activationToken = registerMockUser(payload.username, payload.email, payload.password)
      return ok({ activationToken }, '注册成功')
    } catch (error) {
      return fail(409, (error as Error).message)
    }
  }),

  http.get('/api/auth/verify', async ({ request }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(200)
    const url = new URL(request.url)
    const token = url.searchParams.get('token') || ''
    return verifyMockUser(token) ? ok(null, '激活成功，请登录') : fail(400, '激活链接无效或已使用')
  }),

  http.get('/api/auth/roles', async () => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(100)
    return ok(roles)
  }),

  http.post('/api/auth/refresh', async ({ request }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(250)
    const payload = (await request.json()) as { refreshToken: string }
    const nextSession = refreshSession(payload.refreshToken)
    if (!nextSession) {
      return fail(401, 'refresh token 已失效')
    }
    return ok<AuthTokenDto>(nextSession, '刷新成功')
  }),

  http.get('/api/auth/me', async ({ request }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(200)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const profile: UserProfile = buildUserProfile(session.username)
    return ok(profile)
  }),

  http.get('/api/auth/menu', async ({ request }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(220)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const menuData: MenuDataDto = buildMenuData(session.username)
    return ok(menuData)
  }),

  http.get('/api/auth/users', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session = getSession(request)
    if (!session || getUser(session.username)?.roles[0] !== 'admin') return fail(403, '仅系统管理员可操作')
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') || 1)
    const pageSize = Number(url.searchParams.get('pageSize') || 10)
    const keyword = (url.searchParams.get('keyword') || '').toLowerCase()
    const roleId = url.searchParams.get('roleId')
    const enabledText = url.searchParams.get('enabled')
    const filtered = listMockUsers().filter((item) => (!keyword || `${item.username} ${item.displayName}`.toLowerCase().includes(keyword)) && (!roleId || item.roleId === roleId) && (enabledText === null || item.enabled === (enabledText === 'true')))
    return ok({ list: filtered.slice((page - 1) * pageSize, page * pageSize), page, pageSize, total: filtered.length })
  }),

  http.get('/api/auth/user-options', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session = getSession(request)
    if (!session) return fail(401, 'token 无效')
    const allowedPermissions = ['ticket:create', 'ticket:assign', 'ticket:transfer', 'asset:claim']
    const permissions = buildUserProfile(session.username).permissions
    if (!allowedPermissions.some((permission) => permissions.includes(permission))) {
      return fail(403, '无权限执行该操作')
    }
    return ok(
      listMockUsers()
        .filter((user) => user.enabled)
        .map(({ id, username, displayName }) => ({ id, username, displayName }))
    )
  }),

  http.put('/api/auth/users/:id/status', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session = getSession(request)
    if (!session || getUser(session.username)?.roles[0] !== 'admin') return fail(403, '仅系统管理员可操作')
    const payload = await request.json() as { enabled: boolean }
    return setMockUserStatus(String(params.id), payload.enabled) ? ok(null, '用户状态已更新') : fail(404, '用户不存在')
  }),

  http.put('/api/auth/users/:id/role', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session = getSession(request)
    if (!session || getUser(session.username)?.roles[0] !== 'admin') return fail(403, '仅系统管理员可操作')
    const payload = await request.json() as { roleId: string }
    return setMockUserRole(String(params.id), payload.roleId) ? ok(null, '用户身份已更新') : fail(404, '用户或身份不存在')
  }),

  http.put('/api/auth/users/:id/password', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session = getSession(request)
    if (!session || getUser(session.username)?.roles[0] !== 'admin') return fail(403, '仅系统管理员可操作')
    const payload = await request.json() as { password: string }
    return setMockUserPassword(String(params.id), payload.password) ? ok(null, '密码已重置') : fail(404, '用户不存在')
  }),

  http.get('/api/agent/conversations', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    if (!getSession(request)) return fail(401, 'token 无效')
    return ok(mockConversations)
  }),
  http.get('/api/agent/conversations/:id', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    if (!getSession(request)) return fail(401, 'token 无效')
    const conversation = mockConversations.find((item) => item.id === String(params.id))
    return conversation ? ok({ conversation, messages: [] }) : fail(404, '会话不存在')
  }),
  http.post('/api/agent/chat', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session = getSession(request)
    if (!session) return fail(401, 'token 无效')
    const payload = await request.json() as { conversationId?: string; content: string }
    const conversationId = payload.conversationId || `mock-conv-${Date.now()}`
    if (!payload.conversationId) mockConversations.unshift({ id: conversationId, userId: session.username, title: payload.content.slice(0, 40), createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() })
    const isMetadata = /知识库.*(什么|哪些|有什么|内容|文档|资料|主题|概览|目录)/u.test(payload.content)
    const isChat = /^(你好|您好|嗨|hi|hello)[!！。?？\s]*$/iu.test(payload.content.trim())
    const route = isMetadata ? 'METADATA' : isChat ? 'CHAT' : 'RAG'
    const evidence = route === 'RAG'
      ? [{ index: 1, documentId: 'mock-doc-1', documentName: 'NovaOps 使用手册', chunkId: 'chunk-2', content: '知识库文档支持 RAG 检索并附带来源引用。', score: .92 }]
      : []
    const answer = route === 'METADATA'
      ? '当前 Mock 知识库包含《NovaOps 使用手册》，可用于了解知识库与 RAG 问答能力。'
      : route === 'CHAT'
        ? '你好，我是 Nova AI。你可以问我通用问题，也可以询问知识库中的具体内容。'
        : 'NovaOps 会检索企业知识库，并仅基于通过校验的真实证据回答。'
    const planSteps = [
      ...(route !== 'CHAT' ? [{ action: 'search_kb', label: route === 'METADATA' ? '检索文档元数据' : '检索知识库', query: payload.content, reason: '定位与问题相关的知识库资料', status: 'pending' }] : []),
      { action: 'answer', label: '生成回答', reason: '依据检索结果组织带引用的回答', status: 'pending' },
      ...(route === 'RAG' ? [{ action: 'validate', label: '校验引用', reason: '核对引用编号与知识库资料是否一致', status: 'pending' }] : []),
    ]
    const frames = [
      `event: route\ndata: ${JSON.stringify({ conversationId, route, reason: 'Mock 受控路由结果' })}\n\n`,
      `event: plan\ndata: ${JSON.stringify({ conversationId, steps: planSteps })}\n\n`,
      ...(route !== 'CHAT' ? [
        `event: step\ndata: ${JSON.stringify({ conversationId, action: 'search_kb', status: 'running' })}\n\n`,
        `event: step\ndata: ${JSON.stringify({ conversationId, action: 'search_kb', status: 'done', payload: { count: evidence.length } })}\n\n`,
      ] : []),
      `event: step\ndata: ${JSON.stringify({ conversationId, action: 'answer', status: 'running' })}\n\n`,
      ...[...answer.matchAll(/.{1,12}/gu)].map((match) => `event: delta\ndata: ${JSON.stringify({ conversationId, content: match[0] })}\n\n`),
      `event: step\ndata: ${JSON.stringify({ conversationId, action: 'answer', status: 'done', payload: { characterCount: answer.length } })}\n\n`,
      ...(route === 'RAG' ? [
        `event: step\ndata: ${JSON.stringify({ conversationId, action: 'validate', status: 'running' })}\n\n`,
        `event: step\ndata: ${JSON.stringify({ conversationId, action: 'validate', status: 'done', payload: { passed: true, reason: 'grounding_and_citation_integrity_passed' } })}\n\n`,
      ] : []),
    ]
    frames.push(`event: citation\ndata: ${JSON.stringify({ conversationId, citations: evidence })}\n\n`)
    frames.push(`event: evidence\ndata: ${JSON.stringify({ conversationId, evidence })}\n\n`)
    frames.push(`event: meta\ndata: ${JSON.stringify({ conversationId, retrievalExecuted: route === 'RAG', retrievedCount: evidence.length, validatedCount: evidence.length, validationStatus: route === 'RAG' ? 'PASSED' : 'NOT_APPLICABLE', validationReason: route === 'RAG' ? 'grounding_and_citation_integrity_passed' : 'retrieval_not_required' })}\n\n`)
    frames.push(`event: done\ndata: ${JSON.stringify({ conversationId })}\n\n`)
    const encoder = new TextEncoder()
    const body = new ReadableStream({ start(controller) { frames.forEach((frame) => controller.enqueue(encoder.encode(frame))); controller.close() } })
    return new HttpResponse(body, { headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' } })
  }),

  http.post('/api/agent/tasks', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session = getSession(request)
    if (!session) return fail(401, 'token 无效')
    await delay(200)
    return ok({ taskId: `mock-task-${Date.now()}` })
  }),

  http.post('/api/agent/tasks/:id/stream', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session = getSession(request)
    if (!session) return fail(401, 'token 无效')
    const answer = '已完成任务：检索到 1 个 pending 工单《VPN 掉线》，知识库命中《VPN 处置手册》。'
    const planSteps = [
      { seq: 1, tool: 'ticket.search', title: '检索工单', why: '找到目标工单' },
      { seq: 2, tool: 'kb.search', title: '知识库检索', why: '查找处置手册' },
    ]
    const frames = [
      `event: plan\ndata: ${JSON.stringify({ steps: planSteps })}\n\n`,
      `event: step\ndata: ${JSON.stringify({ seq: 1, tool: 'ticket.search', title: '检索工单', status: 'DONE', observation: '{"total":1,"tickets":[{"id":"A-TICKET-0001","title":"VPN 掉线","status":"pending"}]}', args: { status: 'pending' } })}\n\n`,
      `event: step\ndata: ${JSON.stringify({ seq: 2, tool: 'kb.search', title: '知识库检索', status: 'DONE', observation: '{"chunks":[{"index":1,"documentName":"VPN 处置手册","score":0.88}]}', args: { query: 'vpn' } })}\n\n`,
      `event: result\ndata: ${JSON.stringify({ summary: answer })}\n\n`,
    ]
    const encoder = new TextEncoder()
    const body = new ReadableStream({ start(controller) { frames.forEach((frame) => controller.enqueue(encoder.encode(frame))); controller.close() } })
    return new HttpResponse(body, { headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' } })
  }),

  http.post('/api/agent/tasks/:id/confirm', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session = getSession(request)
    if (!session) return fail(401, 'token 无效')
    return ok({ approved: true })
  }),

  http.post('/api/agent/tasks/:id/cancel', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session = getSession(request)
    if (!session) return fail(401, 'token 无效')
    return ok(null, '任务已取消')
  }),

  http.get('/api/agent/tasks', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session = getSession(request)
    if (!session) return fail(401, 'token 无效')
    return ok([])
  }),

  http.get('/api/agent/tasks/:id', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session = getSession(request)
    if (!session) return fail(401, 'token 无效')
    return ok({ task: { id: String(params.id), goal: '示例任务', status: 'DONE', resultText: null, errorText: null, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }, steps: [] })
  }),

  http.get('/api/tickets', async ({ request }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(280)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const url = new URL(request.url)
    const query: TicketListQueryDto = {
      page: Number(url.searchParams.get('page') || 1),
      pageSize: Number(url.searchParams.get('pageSize') || 10),
      status: (url.searchParams.get('status') || undefined) as TicketListQueryDto['status'],
      priority: (url.searchParams.get('priority') || undefined) as TicketListQueryDto['priority'],
      keyword: url.searchParams.get('keyword') || undefined,
      startDate: url.searchParams.get('startDate') || undefined,
      endDate: url.searchParams.get('endDate') || undefined,
    }
    return ok(queryTickets(query))
  }),

  http.get('/api/tickets/claim-queue', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session = getSession(request)
    if (!session) return fail(401, 'token 无效')
    const permissions = buildUserProfile(session.username).permissions
    if (!permissions.includes('ticket:claim')) return fail(403, '无权限执行该操作')
    const url = new URL(request.url)
    const user = getUser(session.username)
    return ok(queryClaimQueue(user?.id || session.username, Number(url.searchParams.get('page') || 1), Number(url.searchParams.get('pageSize') || 50)))
  }),

  http.get('/api/tickets/:id', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(220)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const ticket = getTicketDetail(String(params.id))
    if (!ticket) {
      return fail(404, '工单不存在')
    }
    return ok<TicketDetailDto>(ticket)
  }),

  http.post('/api/tickets', async ({ request }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(260)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as CreateTicketDto
    if (!payload.title || !payload.description) {
      return fail(400, '标题与描述为必填项')
    }
    const created = createTicket(session.username, payload)
    return ok(created, '工单创建成功')
  }),

  http.put('/api/tickets/:id', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(240)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as UpdateTicketDto
    const updated = updateTicket(String(params.id), session.username, payload)
    if (!updated) {
      return fail(404, '工单不存在')
    }
    return ok(updated, '工单更新成功')
  }),

  http.post('/api/tickets/:id/actions', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(220)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as TicketActionDto
    try {
      const updated = actionTicket(String(params.id), session.username, payload)
      if (!updated) {
        return fail(404, '工单不存在')
      }
      return ok(updated, '工单流转成功')
    } catch (error) {
      return fail(409, (error as Error).message)
    }
  }),

  http.get('/api/tickets/:id/comments', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(160)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const comments = listTicketComments(String(params.id))
    if (!comments) {
      return fail(404, '工单不存在')
    }
    return ok(comments)
  }),

  http.post('/api/tickets/:id/comments', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(180)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as CreateCommentDto
    if (!payload.content?.trim()) {
      return fail(400, '评论内容不能为空')
    }
    const created = createTicketComment(
      String(params.id),
      session.username,
      payload
    )
    if (!created) {
      return fail(404, '工单不存在')
    }
    return ok(created, '评论创建成功')
  }),

  http.post('/api/tickets/:id/attachments', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    await delay(240)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const form = await request.formData()
    const file = form.get('file')
    if (!(file instanceof File) || !file.name) {
      return fail(400, '附件不能为空')
    }
    const attachment = uploadTicketAttachment(String(params.id), file.name, file.size)
    if (!attachment) {
      return fail(404, '工单不存在')
    }
    return ok(attachment, '附件上传成功')
  }),

  http.get('/api/tickets/:id/attachments/:attachmentId/download', async ({ request }) => {
    if (shouldPassthroughTicketBackend) {
      return passthrough()
    }
    if (!getSession(request)) {
      return fail(401, 'token 无效')
    }
    return new HttpResponse(new Blob(['Mock 附件内容'], { type: 'application/octet-stream' }), {
      headers: { 'Content-Type': 'application/octet-stream' },
    })
  }),

  http.get('/api/assets', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    await delay(220)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const url = new URL(request.url)
    const query: AssetListQueryDto = {
      page: Number(url.searchParams.get('page') || 1),
      pageSize: Number(url.searchParams.get('pageSize') || 10),
      status: (url.searchParams.get('status') || undefined) as AssetListQueryDto['status'],
      type: (url.searchParams.get('type') || undefined) as AssetListQueryDto['type'],
      keyword: url.searchParams.get('keyword') || undefined,
    }
    return ok(queryAssets(query))
  }),

  http.get('/api/assets/batch', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    await delay(180)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const url = new URL(request.url)
    const ids = (url.searchParams.get('ids') || '')
      .split(',')
      .map((id) => id.trim())
      .filter(Boolean)
    return ok(getAssetsByIds(ids))
  }),

  http.get('/api/assets/:id', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    await delay(220)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const asset = getAssetDetail(String(params.id))
    if (!asset) {
      return fail(404, '资产不存在')
    }
    const detail: AssetDetailDto = {
      ...asset,
      relatedTickets: listRelatedTicketsByAsset(asset.id),
    }
    return ok(detail)
  }),

  http.post('/api/assets', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    await delay(240)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as CreateAssetDto
    if (!payload.name || !payload.spec) {
      return fail(400, '资产名称与规格为必填项')
    }
    const asset = createAsset(payload)
    const detail: AssetDetailDto = {
      ...asset,
      relatedTickets: [],
    }
    return ok(detail, '资产入库成功')
  }),

  http.put('/api/assets/:id', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    await delay(220)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as UpdateAssetDto
    const updated = updateAsset(String(params.id), payload)
    if (!updated) {
      return fail(404, '资产不存在')
    }
    const detail: AssetDetailDto = {
      ...updated,
      relatedTickets: listRelatedTicketsByAsset(updated.id),
    }
    return ok(detail, '资产更新成功')
  }),

  http.post('/api/assets/:id/actions', async ({ request, params }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    await delay(200)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as AssetActionDto
    try {
      const updated = actionAsset(String(params.id), session.username, payload)
      if (!updated) {
        return fail(404, '资产不存在')
      }
      const detail: AssetDetailDto = {
        ...updated,
        relatedTickets: listRelatedTicketsByAsset(updated.id),
      }
      return ok(detail, '资产状态更新成功')
    } catch (error) {
      return fail(409, (error as Error).message)
    }
  }),

  http.get('/api/ops/dashboard/metrics', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    await delay(220)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const permissions = buildUserProfile(session.username).permissions
    if (!permissions.includes('dashboard:view')) {
      return fail(403, '无权限执行该操作')
    }
    const url = new URL(request.url)
    return ok(buildTicketMetrics(url.searchParams.get('startDate') || undefined, url.searchParams.get('endDate') || undefined))
  }),

  http.get('/api/kb/documents', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session=getSession(request); if(!session)return fail(401,'token 无效')
    const url=new URL(request.url),page=Number(url.searchParams.get('page')||1),pageSize=Number(url.searchParams.get('pageSize')||10),keyword=url.searchParams.get('keyword')||'',fileType=url.searchParams.get('fileType'),status=url.searchParams.get('status')
    const filtered=mockDocuments.filter(item=>(!keyword||`${item.title}${item.fileName}`.includes(keyword))&&(!fileType||item.fileType===fileType)&&(!status||item.status===status))
    return ok({list:filtered.slice((page-1)*pageSize,page*pageSize),page,pageSize,total:filtered.length})
  }),

  http.post('/api/kb/documents', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session=getSession(request); if(!session)return fail(401,'token 无效')
    const form=await request.formData(),file=form.get('file') as File,title=String(form.get('title')||file.name),id=`mock-doc-${Date.now()}`,ext=(file.name.split('.').pop()||'md') as KbDocumentDto['fileType'],now=new Date().toISOString()
    const document:KbDocumentDto={id,title,fileName:file.name,fileType:ext,fileSize:file.size,status:'PARSING',chunkCount:0,createdBy:session.username,createdAt:now,updatedAt:now};mockDocuments.unshift(document)
    window.setTimeout(()=>{document.status='READY';document.chunkCount=1;document.updatedAt=new Date().toISOString();mockChunks[id]=[{id:`${id}-chunk`,documentId:id,chunkIndex:0,content:`${file.name} 的 Mock 解析内容`,vectorId:`${id}-vector`}]},800)
    return ok(document,'文件已上传，正在解析')
  }),

  http.post('/api/kb/uploads/init', async ({ request }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session=getSession(request); if(!session)return fail(401,'token 无效')
    const body=(await request.json()) as {fileName:string;fileSize:number;contentHash:string;chunkSize?:number}
    const ready=mockDocuments.find(item=>item.contentHash===body.contentHash&&item.status==='READY')
    if(ready)return ok({instant:true,documentId:ready.id,uploaded:[]})
    const hit=[...mockUploadSessions.entries()].find(([,item])=>item.contentHash===body.contentHash)
    const sessionId=hit?hit[0]:`mock-up-${Date.now()}`
    if(!hit)mockUploadSessions.set(sessionId,{fileName:body.fileName,fileSize:body.fileSize,contentHash:body.contentHash,chunks:new Set()})
    return ok({instant:false,sessionId,uploaded:[...mockUploadSessions.get(sessionId)!.chunks].sort((a,b)=>a-b)})
  }),

  http.post('/api/kb/uploads/:sessionId/chunks', async ({ request,params }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    if(!getSession(request))return fail(401,'token 无效')
    const record=mockUploadSessions.get(String(params.sessionId));if(!record)return fail(404,'上传会话不存在或已过期')
    const form=await request.formData();if(!form.get('file'))return fail(400,'分片内容不能为空')
    record.chunks.add(Number(new URL(request.url).searchParams.get('index')||0))
    return ok(null,'分片已接收')
  }),

  http.post('/api/kb/uploads/:sessionId/merge', async ({ request,params }) => {
    if (shouldPassthroughTicketBackend) return passthrough()
    const session=getSession(request); if(!session)return fail(401,'token 无效')
    const sessionId=String(params.sessionId),record=mockUploadSessions.get(sessionId);if(!record)return fail(404,'上传会话不存在或已过期')
    const id=`mock-doc-${Date.now()}`,ext=(record.fileName.split('.').pop()||'md') as KbDocumentDto['fileType'],now=new Date().toISOString()
    const document:KbDocumentDto={id,title:record.fileName,fileName:record.fileName,fileType:ext,fileSize:record.fileSize,status:'PARSING',chunkCount:0,contentHash:record.contentHash,createdBy:session.username,createdAt:now,updatedAt:now};mockDocuments.unshift(document)
    window.setTimeout(()=>{document.status='READY';document.chunkCount=1;document.updatedAt=new Date().toISOString();mockChunks[id]=[{id:`${id}-chunk`,documentId:id,chunkIndex:0,content:`${record.fileName} 的 Mock 解析内容`,vectorId:`${id}-vector`}]},800)
    mockUploadSessions.delete(sessionId)
    return ok(document,'文件已上传，正在解析')
  }),

  http.get('/api/kb/documents/:id/chunks', async ({ request,params }) => { if(shouldPassthroughTicketBackend)return passthrough();if(!getSession(request))return fail(401,'token 无效');return ok(mockChunks[String(params.id)]||[]) }),
  http.put('/api/kb/documents/:id', async ({ request,params }) => { if(shouldPassthroughTicketBackend)return passthrough();if(!getSession(request))return fail(401,'token 无效');const item=mockDocuments.find(value=>value.id===String(params.id));if(!item)return fail(404,'文档不存在');item.title=String(((await request.json()) as {title:string}).title);item.updatedAt=new Date().toISOString();return ok(null,'标题已更新') }),
  http.post('/api/kb/documents/:id/retry', async ({ request,params }) => { if(shouldPassthroughTicketBackend)return passthrough();if(!getSession(request))return fail(401,'token 无效');const item=mockDocuments.find(value=>value.id===String(params.id));if(!item)return fail(404,'文档不存在');if(item.status!=='FAILED')return fail(409,'仅解析失败的文档可以重试');item.status='PARSING';item.errorMsg=undefined;item.updatedAt=new Date().toISOString();window.setTimeout(()=>{item.status='READY';item.chunkCount=1;item.updatedAt=new Date().toISOString();mockChunks[item.id]=[{id:`${item.id}-chunk`,documentId:item.id,chunkIndex:0,content:`${item.fileName} 的 Mock 解析内容`,vectorId:`${item.id}-vector`}]},800);return ok(item,'已重新提交解析') }),
  http.delete('/api/kb/documents/:id', async ({ request,params }) => { if(shouldPassthroughTicketBackend)return passthrough();if(!getSession(request))return fail(401,'token 无效');const index=mockDocuments.findIndex(value=>value.id===String(params.id));if(index<0)return fail(404,'文档不存在');mockDocuments.splice(index,1);delete mockChunks[String(params.id)];return ok(null,'文档已删除') }),

  http.get('/api/kb', async ({ request }) => {
    await delay(220)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const url = new URL(request.url)
    const query: KbListQueryDto = {
      page: Number(url.searchParams.get('page') || 1),
      pageSize: Number(url.searchParams.get('pageSize') || 10),
      keyword: url.searchParams.get('keyword') || undefined,
      tag: url.searchParams.get('tag') || undefined,
    }
    return ok(queryKbList(query))
  }),

  http.get('/api/kb/:id/versions', async ({ request, params }) => {
    await delay(200)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const versions = getKbVersions(String(params.id))
    if (!versions) {
      return fail(404, '文章不存在')
    }
    return ok(versions)
  }),

  http.get('/api/kb/:id', async ({ request, params }) => {
    await delay(180)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const detail = getKbDetail(String(params.id))
    if (!detail) {
      return fail(404, '文章不存在')
    }
    return ok(detail)
  }),

  http.post('/api/kb/save', async ({ request }) => {
    await delay(240)
    const session = getSession(request)
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as SaveKbDto
    if (!payload.title || !payload.content) {
      return fail(400, '标题和正文不能为空')
    }
    return ok(saveKb(session.username, payload), '保存成功')
  }),
]
