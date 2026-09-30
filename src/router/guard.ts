import type { Router } from 'vue-router'
import { pinia } from '@/store'
import { useAuthStore } from '@/store/auth'
import { usePermissionStore } from '@/store/permission'


//设置路由守卫
export const setupRouterGuard = (router: Router) => {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore(pinia)
    const permissionStore = usePermissionStore(pinia)
    const isPublicRoute = Boolean(to.meta.public)//路由配置里定义的，值可能是 true、undefined 等

    if (to.name === 'NotFound' && !authStore.isAuthenticated) {
      return {
        path: '/login',
        query: { redirect: to.fullPath },
        //登录成功后，跳回用户原本想去的页面
      }
    }

    if (!isPublicRoute && !authStore.isAuthenticated) {
      return {
        path: '/login',
        query: { redirect: to.fullPath },
      }
    }

    if (to.path === '/login' && authStore.isAuthenticated) {
      return '/home'
    }

//用户虽授权，但store中没有用户信息，尝试拉取用户信息，
// 失败的话就登出，然后重置动态路由，重定向到login
    if (authStore.isAuthenticated && !authStore.user) {
      try {
        await authStore.me()
      } catch {
        authStore.logout()
        permissionStore.resetDynamicRoutes(router)
        return '/login'
      }
    }

    //先等路由注册完，再返回目标路径让 Vue Router 重新走一遍守卫
    if (authStore.isAuthenticated && !permissionStore.isRouteReady) {
      try {
        await permissionStore.generateRoutes(router)
      } catch {
        //菜单接口失败时不能让导航无声卡死：退回登录页并保留目标路径
        authStore.logout()
        permissionStore.resetDynamicRoutes(router)
        return { path: '/login', query: { redirect: to.fullPath } }
      }
      return to.fullPath
    }

    //路由的 meta 里定义的权限码，用户想访问需要有对应的码才行
    const permissionCode = to.meta.permission as string | undefined
    if (permissionCode && !permissionStore.hasPermission(permissionCode)) {
      return '/403'
    }

    return true
  })
}
