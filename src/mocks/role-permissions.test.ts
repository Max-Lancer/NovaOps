import { describe, expect, it } from 'vitest'
import { deleteMockRole, roles, saveMockRole } from './db'

describe('ticket creation role permissions', () => {
  it('rejects creation permission without a reachable entry', () => {
    const count = roles.length
    expect(() => saveMockRole(undefined, { name: '测试角色', description: '', permissions: ['ticket:create'] }))
      .toThrow('请同时选择使用智能问答或访问工单')
    expect(roles).toHaveLength(count)
  })

  it.each(['agent:chat', 'ticket:view'])('accepts the explicitly selected entry %s', (entry) => {
    saveMockRole(undefined, { name: '测试角色', description: '', permissions: ['ticket:create', entry] })
    const role = roles[roles.length - 1]!
    expect(role.permissions).toEqual(['ticket:create', entry])
    deleteMockRole(role.id)
  })
})
