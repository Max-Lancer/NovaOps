import type { RouteRecordRaw } from 'vue-router'
import type { MenuItemDto } from '@/types/menu'

type RouteComponentLoader = () => Promise<unknown>


//组件名 → 懒加载函数的映射表
//后端只存组件名字符串，不存路径，所以前端需要这张表做字符串 → 实际组件的映射。
// 同时用 () => import(...) 函数形式，实现路由懒加载
const routeComponentMap: Record<string, RouteComponentLoader> = {
  HomeView: () => import('@/views/home/index.vue'),
  DashboardView: () => import('@/views/ops/dashboard/index.vue'),
  TicketListView: () => import('@/views/ticket/list.vue'),
  ClaimQueueView: () => import('@/views/ticket/claim.vue'),
  TicketDetailView: () => import('@/views/ticket/detail.vue'),
  AssetListView: () => import('@/views/asset/list.vue'),
  KbListView: () => import('@/views/kb/list.vue'),
  AgentConsoleView: () => import('@/views/agent/console.vue'),
  AgentTasksView: () => import('@/views/agent/tasks.vue'),
  UserManagementView: () => import('@/views/system/users.vue'),
}

//后端菜单数据里，有子菜单的父级节点 component 字段存的是 'RouteView'，
// 叶子节点（实际页面）存的是 'DashboardView' 这种。
//"component": "RouteView",     // ← 不是实际页面，只是个分组
//"component": "AssetListView"  // ← 这才是真正的页面

//判断：这是个真正的页面还是只是个菜单分组
const isRouteLeaf = (menu: MenuItemDto) => menu.component !== 'RouteView'


//菜单信息转换成路由信息
export const transformMenuToRoutes = (menus: MenuItemDto[]): RouteRecordRaw[] => {
  const routes: RouteRecordRaw[] = []

  const travel = (nodes: MenuItemDto[]) => {
    nodes.forEach((node) => {
      if (isRouteLeaf(node)) {
        //叶子节点：真正的页面
        //根据组件名从映射表中取出对应的懒加载函数
        const component = routeComponentMap[node.component]
        if (component) {
          routes.push({
            //后端菜单数据转换成 Vue Router 需要的路由格式
            path: node.path,
            name: node.name,
            component,
            meta: {
              title: node.title,
              permission: node.permission,
              keepAlive: node.keepAlive ?? true,
            },
          })
        }
      }

      if (node.children?.length) {
        travel(node.children)
      }
    })
  }

  travel(menus)
  return routes
  //travel(menus) 遍历完所有菜单，return routes 把生成好的路由数组返回，
}
