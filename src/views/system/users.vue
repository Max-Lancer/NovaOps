<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Modal, message, type TablePaginationConfig, type TableProps } from 'ant-design-vue'
import { getRolesApi, getUsersApi, getPermissionCatalogApi, createRoleApi, updateRoleApi, deleteRoleApi, resetUserPasswordApi, updateUserRoleApi, updateUserStatusApi } from '@/api/auth'
import type { RoleDto, UserListItemDto, PermissionOption } from '@/types/auth'

defineOptions({ name: 'UserManagement' })
const loading = ref(false)
const users = ref<UserListItemDto[]>([])
const roles = ref<RoleDto[]>([])
const catalog = ref<PermissionOption[]>([])
const roleOpen = ref(false)
const roleSaving = ref(false)
const editingRole = ref<RoleDto>()
const roleForm = reactive({ name: '', description: '', permissions: [] as string[] })
const permissionGroups = computed(() => catalog.value.reduce<Record<string, PermissionOption[]>>((groups, item) => { (groups[item.group] ??= []).push(item); return groups }, {}))
const loadRoles = async () => { roles.value = await getRolesApi() }
const editRole = (role?: RoleDto) => {
  editingRole.value = role
  roleForm.name = role?.name ?? ''
  roleForm.description = role?.description ?? ''
  roleForm.permissions = [...(role?.permissions ?? [])].filter(code => catalog.value.some(item => item.code === code))
  roleOpen.value = true
}
const saveRole = async () => {
  if (!roleForm.name.trim()) { message.warning('请输入角色名称'); return }
  roleSaving.value = true
  try {
    const data = { name: roleForm.name.trim(), description: roleForm.description.trim(), permissions: roleForm.permissions }
    if (editingRole.value) await updateRoleApi(editingRole.value.id, data)
    else await createRoleApi(data)
    roleOpen.value = false
    message.success('角色已保存，相关用户刷新页面后可看到最新菜单')
    await loadRoles()
  } finally { roleSaving.value = false }
}
const removeRole = (role: RoleDto) => Modal.confirm({
  title: `确认删除角色“${role.name}”？`, content: '如仍有用户使用该角色，请先调整用户身份。',
  async onOk() { await deleteRoleApi(role.id); message.success('角色已删除'); await loadRoles() },
})
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const filters = reactive<{ keyword: string; roleId?: string; enabled?: boolean }>({ keyword: '' })
const passwordOpen = ref(false)
const selectedUser = ref<UserListItemDto>()
const password = ref('123456')
const columns: TableProps['columns'] = [
  { title: '账号', dataIndex: 'username', key: 'username' },
  { title: '显示名称', dataIndex: 'displayName', key: 'displayName' },
  { title: '身份', key: 'role', width: 180 },
  { title: '状态', key: 'enabled', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'actions', width: 220 },
]

const fetchUsers = async () => {
  loading.value = true
  try {
    const data = await getUsersApi({ page: page.value, pageSize: pageSize.value, keyword: filters.keyword || undefined, roleId: filters.roleId, enabled: filters.enabled })
    users.value = data.list
    total.value = data.total
  } finally { loading.value = false }
}
const search = () => { page.value = 1; void fetchUsers() }
const changeStatus = (user: UserListItemDto) => {
  Modal.confirm({ title: user.enabled ? '确认禁用该用户？' : '确认启用该用户？', content: user.enabled ? '禁用后该用户当前访问将立即失效。' : undefined, async onOk() { await updateUserStatusApi(user.id, !user.enabled); message.success('用户状态已更新'); await fetchUsers() } })
}
const changeRole = async (user: UserListItemDto, roleId: string) => {
  await updateUserRoleApi(user.id, roleId); message.success('身份已调整并即时生效'); await fetchUsers()
}
const openReset = (user: UserListItemDto) => { selectedUser.value = user; password.value = '123456'; passwordOpen.value = true }
const resetPassword = async () => {
  if (!selectedUser.value || password.value.length < 6) return message.warning('密码至少 6 位')
  await resetUserPasswordApi(selectedUser.value.id, password.value); passwordOpen.value = false; message.success('密码已重置')
}

onMounted(async () => { await Promise.all([loadRoles(), getPermissionCatalogApi().then(data => { catalog.value = data }), fetchUsers()]) })
</script>

<template>
  <div class="user-page">
    <a-card title="角色与权限" class="role-card">
      <template #extra><a-button type="primary" @click="editRole()">新增角色</a-button></template>
      <p>系统管理员角色受保护；系统预设角色不能删除。部分业务操作需同时授予对应页面访问权限。</p>
      <a-row :gutter="16">
        <a-col v-for="role in roles" :key="role.id" :xs="24" :lg="8">
          <a-card size="small" class="role-item"><a-statistic :title="role.name" :value="role.permissions.length" suffix="项权限" /><p>{{ role.description }}</p><a-space wrap><a-tag v-for="code in role.permissions" :key="code">{{ catalog.find(item => item.code === code)?.name || code }}</a-tag></a-space>
            <div class="role-actions" v-if="role.id !== 'role-admin'"><a-button size="small" @click="editRole(role)">编辑</a-button><a-button v-if="!['role-staff', 'role-guest', 'role-member'].includes(role.id)" size="small" danger @click="removeRole(role)">删除</a-button></div>
          </a-card>
        </a-col>
      </a-row>
    </a-card>
    <a-card title="用户管理">
      <a-form layout="inline" class="filters">
        <a-form-item><a-input v-model:value="filters.keyword" allow-clear placeholder="搜索账号或名称" @press-enter="search" /></a-form-item>
        <a-form-item label="身份"><a-select v-model:value="filters.roleId" allow-clear style="width: 150px" :options="roles.map(r => ({ label: r.name, value: r.id }))" /></a-form-item>
        <a-form-item label="状态"><a-select v-model:value="filters.enabled" allow-clear style="width: 120px" :options="[{ label: '启用', value: true }, { label: '禁用', value: false }]" /></a-form-item>
        <a-form-item><a-button type="primary" @click="search">查询</a-button></a-form-item>
      </a-form>
      <a-table :columns="columns" :data-source="users" :loading="loading" row-key="id" :pagination="{ current: page, pageSize, total, showSizeChanger: true }" @change="(p: TablePaginationConfig) => { page = p.current || 1; pageSize = p.pageSize || 10; fetchUsers() }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'role'"><a-select :value="record.roleId" style="width: 140px" :options="roles.map(r => ({ label: r.name, value: r.id }))" @change="(value: string) => changeRole(record as UserListItemDto, value)" /></template>
          <template v-else-if="column.key === 'enabled'"><a-tag :color="record.enabled ? 'success' : 'error'">{{ record.enabled ? '启用' : '禁用' }}</a-tag></template>
          <template v-else-if="column.key === 'actions'"><a-space><a-button size="small" @click="changeStatus(record as UserListItemDto)">{{ record.enabled ? '禁用' : '启用' }}</a-button><a-button size="small" @click="openReset(record as UserListItemDto)">重置密码</a-button></a-space></template>
        </template>
      </a-table>
    </a-card>
    <a-modal v-model:open="roleOpen" :title="editingRole ? '编辑角色' : '新增角色'" :confirm-loading="roleSaving" width="720px" @ok="saveRole">
      <a-form layout="vertical"><a-form-item label="角色名称" required><a-input v-model:value="roleForm.name" :maxlength="100" /></a-form-item>
        <a-form-item label="角色说明"><a-input v-model:value="roleForm.description" :maxlength="255" /></a-form-item>
        <a-form-item label="权限配置"><p>勾选已有页面与操作；部分业务操作需要对应的页面访问权限。</p>
          <a-checkbox-group v-model:value="roleForm.permissions" class="permission-groups"><section v-for="(items, group) in permissionGroups" :key="group"><strong>{{ group }}</strong><div><a-checkbox v-for="item in items" :key="item.code" :value="item.code">{{ item.name }} <small>{{ item.code }}</small></a-checkbox></div></section></a-checkbox-group>
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="passwordOpen" title="重置密码" ok-text="确认重置" @ok="resetPassword"><a-input-password v-model:value="password" placeholder="至少 6 位" /></a-modal>
  </div>
</template>

<style scoped>
.user-page { display: grid; gap: 16px; }.role-item { height: 100%; }.role-item p { min-height: 44px; color: var(--nova-text-secondary); }.filters { margin-bottom: 18px; }.role-actions { display: flex; gap: 8px; margin-top: 16px; }.permission-groups { display: grid; gap: 14px; width: 100%; }.permission-groups section div { display: flex; flex-wrap: wrap; gap: 8px 16px; margin-top: 6px; }.permission-groups small { color: var(--nova-text-secondary); }
</style>
