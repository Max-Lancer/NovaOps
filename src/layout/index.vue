<script setup lang="ts">
import { computed, defineAsyncComponent, h, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type MenuProps, type TabsProps } from 'ant-design-vue'
import {
  AppstoreOutlined,
  BookOutlined,
  CustomerServiceOutlined,
  DashboardOutlined,
  HomeOutlined,
  DesktopOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ToolOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
import { useAppStore } from '@/store/app'
import { useAuthStore } from '@/store/auth'
import { usePermissionStore } from '@/store/permission'
import request from '@/utils/request'
import type { MenuItemDto } from '@/types/menu'
import ThemeSettings from '@/components/theme/ThemeSettings.vue'

const ChatFloatWidget = defineAsyncComponent(() => import('@/components/chat/ChatFloatWidget.vue'))

type MenuItem = Required<MenuProps>['items'][number]

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
const permissionStore = usePermissionStore()
// 当前展开的子菜单 key 列表
const openKeys = ref<string[]>([])

// 图标映射：后端返回的 icon 字符串 → 对应的图标组件
const iconMap = {
  home: HomeOutlined,
  dashboard: DashboardOutlined,
  ticket: ToolOutlined,
  asset: DesktopOutlined,
  kb: BookOutlined,
  user: TeamOutlined,
  default: AppstoreOutlined,
}

// 把后端返回的菜单数据转换成 Ant Design 菜单组件需要的格式
const renderMenuItems = (menus: MenuItemDto[]): MenuItem[] => {
  return menus.map((menu) => {
    // 根据 icon 字符串取出对应图标组件，取不到则用默认图标
    const Icon = iconMap[menu.icon as keyof typeof iconMap] || iconMap.default
    return {
      key: menu.path,
      icon: () => h(Icon),
      label: menu.title,
      // 有子菜单则递归处理
      children: menu.children?.length ? renderMenuItems(menu.children) : undefined,
    }
  })
}

// 递归查找目标路径的所有父级路径，用于自动展开子菜单
const getParentPaths = (
  targetPath: string,
  menus: MenuItemDto[],
  chain: string[] = []
): string[] => {
  for (const menu of menus) {
    const nextChain = [...chain, menu.path]
    if (menu.path === targetPath) {
      return chain
    }
    if (menu.children?.length) {
      const result = getParentPaths(targetPath, menu.children, nextChain)
      if (result.length) {
        return result
      }
    }
  }
  return []
}

// 后端菜单数据转换为 a-menu 需要的格式
const menuItems = computed(() => renderMenuItems(permissionStore.menus))

// 详情页/编辑页在菜单中高亮其对应的列表页（详情页本身不在菜单里）
const selectedMenuPath = computed(() => {
  if (route.path.startsWith('/ops/ticket/detail')) {
    return '/ops/ticket/list'
  }
  if (route.path.startsWith('/ops/asset/detail')) {
    return '/ops/asset/list'
  }
  return route.path
})
const selectedKeys = computed(() => [selectedMenuPath.value])//就是把字符串转成只有一个元素的数组，满足 a-menu 的类型要求。

// Tab 页签相关
const tabs = computed(() => appStore.tabs)
const activeTabPath = computed(() => appStore.activeTabPath)

// 面包屑：从路由匹配记录中提取标题
const breadcrumbItems = computed(() => {
  return route.matched
    .filter((item) => item.meta.title && item.name !== 'Root')
    .map((item) => ({
      key: String(item.path),
      title: String(item.meta.title),
    }))
})

// 路由变化时：添加 Tab 页签 + 自动展开对应子菜单
watch(
  //监听 route.fullPath 的变化
  //用 () => route.fullPath 而不是直接写 route.fullPath：
  //传的是函数，每次都会重新取值
  () => route.fullPath,
  () => {
    appStore.addTabByRoute(route)
    //路由变化时，自动展开当前页面对应的子菜单
    openKeys.value = getParentPaths(selectedMenuPath.value, permissionStore.menus)
  },
  { immediate: true }
)

// 切换侧边栏折叠状态
const toggleCollapse = () => {
  appStore.setCollapsed(!appStore.collapsed)
}

// 菜单点击 → 路由跳转
// MenuProps标注事件处理函数的参数类型
//从 MenuProps 这个类型里取出 onOpenChange 这个事件的类型
const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
  void router.push(String(key))
}

// 子菜单展开/收起时更新 openKeys
const handleOpenChange: MenuProps['onOpenChange'] = (keys) => {
  openKeys.value = keys as string[]
}

// Tab 切换 → 路由跳转
const handleTabChange: TabsProps['onChange'] = (key) => {
  void router.push(String(key))
}

// Tab 关闭：从 store 移除，如果关的是当前页则跳转到另一个活跃 Tab
const handleTabEdit: TabsProps['onEdit'] = (targetKey, action) => {
  if (action === 'remove') {
    const path = String(targetKey)
    appStore.removeTabByPath(path)
    if (route.path === path) {
      void router.push(appStore.activeTabPath)
    }
  }
}

// 用户下拉操作：目前只有退出登录
const handleUserAction = async ({ key }: { key: string }) => {
  if (key !== 'logout') {
    return
  }
  // 先取消所有在途请求再清状态，避免残留请求返回 401 又把会话"救活"
  request.cancelAll()
  authStore.logout()
  permissionStore.resetDynamicRoutes(router)
  appStore.resetTabs()
  await router.replace('/login')
}

const openAgentChat = () => {
  void router.push('/agent/chat')
}
</script>

<template>
  <a-layout class="layout-shell">
    <a-layout-sider
      :collapsed="appStore.collapsed"
      collapsible
      :trigger="null"
      width="220"
      class="layout-sider"
    >
      <div class="logo">{{ appStore.collapsed ? 'NO' : 'NovaOps' }}</div>
      <a-menu
        theme="dark"
        mode="inline"
        :selected-keys="selectedKeys"
        :open-keys="openKeys"
        :items="menuItems"
        @open-change="handleOpenChange"
        @click="handleMenuClick"
      />
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="layout-header">
        <div class="header-left">
          <a-button type="text" class="fold-btn" @click="toggleCollapse">
            <MenuUnfoldOutlined v-if="appStore.collapsed" />
            <MenuFoldOutlined v-else />
          </a-button>
          <a-breadcrumb :items="breadcrumbItems" />
        </div>
        <div class="header-right">
          <a-space size="middle">
            <ThemeSettings />
            <a-tooltip v-if="permissionStore.hasPermission('agent:chat')" title="进入全屏智能问答">
              <a-button
                class="header-agent-btn"
                aria-label="智能问答"
                @click="openAgentChat"
              >
                <CustomerServiceOutlined class="agent-icon" />
                <span class="agent-btn-text">AI 助手</span>
                <span class="agent-pulse"></span>
              </a-button>
            </a-tooltip>
            <a-dropdown>
              <a class="user-link" @click.prevent>
                {{ authStore.user?.displayName || authStore.user?.username }}
              </a>
              <template #overlay>
                <a-menu @click="handleUserAction">
                  <a-menu-item key="logout">退出登录</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </a-space>
        </div>
      </a-layout-header>
      <a-layout-content class="layout-content">
        <div class="tabs-wrap">
          <a-tabs
            type="editable-card"
            hide-add
            size="small"
            :active-key="activeTabPath"
            @change="handleTabChange"
            @edit="handleTabEdit"
          >
            <a-tab-pane
              v-for="tab in tabs"
              :key="tab.path"
              :tab="tab.title"
              :closable="tab.closable"
            />
          </a-tabs>
        </div>
        <section class="page-wrap">
          <RouterView v-slot="{ Component }">
            <keep-alive :include="appStore.cachedTabNames">
              <component :is="Component" :key="route.name || route.path" />
            </keep-alive>
          </RouterView>
        </section>
      </a-layout-content>
    </a-layout>

    <!-- 全局右下角客服悬浮助手 -->
    <ChatFloatWidget />
  </a-layout>
</template>

<style scoped>
.layout-shell {
  min-height: 100vh;
}

.layout-sider {
  overflow: auto;
}

.logo {
  height: 56px;
  margin: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  display: grid;
  place-items: center;
}

.layout-header {
  background: var(--nova-surface);
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--nova-border);
  padding: 0 16px 0 8px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fold-btn {
  width: 40px;
}

/* 显眼高质感的 Header AI 问答按钮 */
.header-agent-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px 0 10px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--nova-primary) 35%, var(--nova-border));
  background: linear-gradient(135deg, color-mix(in srgb, var(--nova-primary) 12%, var(--nova-surface)), color-mix(in srgb, var(--nova-primary) 4%, var(--nova-surface)));
  color: var(--nova-primary);
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 2px 8px color-mix(in srgb, var(--nova-primary) 14%, transparent);
  transition: all 0.25s ease;
}

.header-agent-btn:hover {
  border-color: var(--nova-primary);
  background: linear-gradient(135deg, color-mix(in srgb, var(--nova-primary) 20%, var(--nova-surface)), color-mix(in srgb, var(--nova-primary) 8%, var(--nova-surface)));
  color: var(--nova-primary);
  transform: translateY(-1px);
  box-shadow: 0 4px 14px color-mix(in srgb, var(--nova-primary) 28%, transparent);
}

.agent-icon {
  font-size: 14px;
}

.agent-btn-text {
  letter-spacing: 0.02em;
}

.agent-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.25);
  animation: pulse-glow 2s infinite ease-in-out;
}

@keyframes pulse-glow {
  0%, 100% {
    transform: scale(1);
    opacity: 0.8;
  }
  50% {
    transform: scale(1.35);
    opacity: 1;
    box-shadow: 0 0 8px rgba(34, 197, 94, 0.6);
  }
}

.user-link {
  color: var(--nova-text);
}

.layout-content {
  padding: 12px;
  background: var(--nova-bg);
}

.tabs-wrap {
  background: var(--nova-surface);
  border-radius: 8px;
  padding: 8px 12px 0;
}

.page-wrap {
  margin-top: 12px;
  min-height: calc(100vh - 138px);
}
</style>
