//静态路由表：
//根路由（需登录）
//独立页面（无需登录）



import type { RouteRecordRaw } from 'vue-router'
//路由配置对象的类型
//框架提供的类型定义，用来约束你的数据结构。

export const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Root',
    component: () => import('@/layout/index.vue'),
    redirect: '/home',
    meta: {
      requiresAuth: true,
    },
    children: [
      { path: '/dashboard', redirect: '/home' },
      { path: '/ticket/list', redirect: '/ops/ticket/list' },
      { path: '/ticket/detail/:id', redirect: (to) => `/ops/ticket/detail/${String(to.params.id)}` },
      { path: '/asset/list', redirect: '/ops/asset/list' },
      { path: '/asset/detail/:id', redirect: (to) => `/ops/asset/detail/${String(to.params.id)}` },
      {
        path: '/ops/ticket/detail/:id',
        name: 'TicketDetail',
        component: () => import('@/views/ticket/detail.vue'),
        meta: {
          title: '工单详情',
          keepAlive: false,
        },
      },
      {
        path: '/ops/asset/detail/:id',
        name: 'AssetDetail',
        component: () => import('@/views/asset/detail.vue'),
        meta: {
          title: '资产详情',
          permission: 'asset:view',
          keepAlive: false,
        },
      },
      {
        path: '/agent/chat',
        name: 'AgentChat',
        component: () => import('@/views/agent/chat.vue'),
        meta: { title: '智能问答', permission: 'agent:chat', keepAlive: true },
      },
    ],
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: {
      title: '登录',
      public: true,
    },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/index.vue'),
    meta: {
      title: '注册',
      public: true,
    },
  },
  {
    path: '/verify',
    name: 'Verify',
    component: () => import('@/views/verify/index.vue'),
    meta: {
      title: '邮箱激活',
      public: true,
    },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/403/index.vue'),
    meta: {
      title: '403',
      public: true,
    },
  },
  {
    path: '/404',
    name: 'NotFoundPage',
    component: () => import('@/views/404/index.vue'),
    meta: {
      title: '404',
      public: true,
    },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/404/index.vue'),
    meta: {
      title: '404',
      public: true,
    },
  },
]
