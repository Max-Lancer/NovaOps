//封装一个统一的 HTTP 请求工具
import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { message } from 'ant-design-vue'
import type { AuthTokenDto } from '@/types/auth'
import type { ApiResponse } from '@/types/api'
import type { RequestConfig } from './types'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './token'

interface PendingRequest {
  resolve: (token: string) => void
  reject: (error: unknown) => void
}

type RequestInternalConfig = InternalAxiosRequestConfig & RequestConfig

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
const controllers = new Map<string, AbortController>()//key:请求id，value:AbortController,核心能力调用abort方法去中断axios请求
const refreshQueue: PendingRequest[] = []//初始化等待请求数组
let isRefreshing = false//标记是否有在进行token刷新

const http: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
})

//将新token同步到Pinia状态管理
const syncStoreTokens = async (accessToken: string, refreshToken: string) => {
  const { useAuthStore } = await import('@/store/auth')
  const { pinia } = await import('@/store')
  const authStore = useAuthStore(pinia)
  authStore.setTokens(accessToken, refreshToken)
}

// 清除认证状态，把 store 里存的 token、用户信息等全部清掉。
const clearAuthState = async () => {
  const { useAuthStore } = await import('@/store/auth')
  const { pinia } = await import('@/store')
  const authStore = useAuthStore(pinia)
  authStore.clearAuth()
}

//同时处理成功和失败两种结果：
// 失败时的调用：flushRefreshQueue(error) — 只传 error，token 用默认值 ''
// 成功：flushRefreshQueue(null, tokens.accessToken) — 传 null 表示没错误，再传新 token
//刷新 token 之后，统一处理队列里的等待请求
const flushRefreshQueue = (error: unknown, token = '') => {
  refreshQueue.forEach((request) => {
    if (error) {
      request.reject(error)
      return
    }
    request.resolve(token)
  })
  refreshQueue.length = 0
}

//拿到请求详细配置信息；末尾拼接自增序号，
//避免同毫秒发出的两个同 URL 请求生成相同 id 导致先到的请求无法取消
let requestSequence = 0
const getRequestId = (config: RequestConfig) => {
  const method = (config.method || 'get').toUpperCase()
  const url = config.url || ''
  return config.requestId || `${method}:${url}:${Date.now()}:${requestSequence++}`
}

//请求完成后，无论失败/成功，都不再需要controller去取消请求
const clearControllerByConfig = (config: RequestConfig) => {
  if (config.requestId) {
    controllers.delete(config.requestId)
  }
}

const setAuthorizationHeader = (config: RequestConfig, accessToken: string) => {
  //axios 内部有时把 headers 处理成 AxiosHeaders 类实例，有时还是原始对象，两种设置值的方式不同。
  if (config.headers instanceof AxiosHeaders) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
    return
  }

  // 处理 headers 是普通对象的情况，用扩展运算符 ... 合并进去
  config.headers = {
    ...config.headers,
    Authorization: `Bearer ${accessToken}`,
  }
}

//会话彻底失效时的兜底跳转：带上当前位置，登录成功后能回到原页面
const redirectToLogin = () => {
  const current = window.location.pathname + window.location.search
  if (current.startsWith('/login')) {
    window.location.replace('/login')
    return
  }
  window.location.replace(`/login?redirect=${encodeURIComponent(current)}`)
}

const handleUnauthorized = async (config: RequestConfig) => {
  if (config.skipAuthRefresh || config._retry) {
    await clearAuthState()//async 函数，里面有 await import(...) 操作。必须等它做完，store 才真正清干净了
    clearTokens()
    redirectToLogin()
    //这个函数是 async 的，async 函数永远返回 Promise
    //认证失败,把这个错误抛给业务层去处理。
    return Promise.reject(new Error('Unauthorized'))
  }

  config._retry = true

  //已经有别的请求在刷新 token → 把当前请求的 resolve/reject 存进队列，
  //等那个刷新完成的请求调用 flushRefreshQueue 唤醒，自己不要重复刷新
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      refreshQueue.push({
        resolve: (token) => {
          setAuthorizationHeader(config, token)
          resolve(http(config))
        },
        reject: (error) => reject(error),
      })
    })
  }

  isRefreshing = true
  try {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
      throw new Error('Missing refresh token')
    }
    //data: AuthTokenDto
    const response = await axios.post<ApiResponse<AuthTokenDto>>(
      `${API_BASE_URL}/auth/refresh`,
      { refreshToken },
      { timeout: 10000 }
    )
    if (response.data.code !== 0) {
      throw new Error(response.data.message || 'Refresh failed')
    }
    const tokens = response.data.data
    setTokens(tokens.accessToken, tokens.refreshToken)//localStorage
    await syncStoreTokens(tokens.accessToken, tokens.refreshToken)
    flushRefreshQueue(null, tokens.accessToken)//用新token重发排队的请求

    //触发刷新的请求自己直接用新 token 重发，不能也入队——
    //flushRefreshQueue 刚刚执行完，队列不会再被唤醒，入队会永远挂起
    setAuthorizationHeader(config, tokens.accessToken)
    return http(config)
  } catch (error) {
    //一旦捕获到error
    flushRefreshQueue(error)//全部拒绝请求
    await clearAuthState()
    clearTokens()
    redirectToLogin()
    throw error
  } finally {
    isRefreshing = false
  }
}

//请求拦截器
const onRequest = (config: InternalAxiosRequestConfig) => {
  const requestConfig = config as RequestInternalConfig
  const accessToken = getAccessToken()
  if (accessToken) {
    //设置授权头
    setAuthorizationHeader(requestConfig, accessToken)
  }

  //给没有取消能力的请求，补上一个 AbortController
  if (!requestConfig.signal) {
    const requestId = getRequestId(requestConfig)
    const controller = new AbortController()// 创建取消控制器
    requestConfig.requestId = requestId
    requestConfig.signal = controller.signal//把 signal 赋给请求，这样 axios 就能监听取消信号
    controllers.set(requestId, controller)
  }

  return requestConfig
}

//响应拦截器，拦截成功的响应
const onResponse = async (response: AxiosResponse<ApiResponse<unknown>>) => {
  const config = response.config as RequestConfig
  clearControllerByConfig(config)

  // blob 响应（附件下载）不带 ApiResponse 包装，必须原样返回，不能按 code 解包
  if (config.responseType === 'blob') {
    return response.data as unknown
  }

  const { code, message: msg, data } = response.data
  if (code === 0) {
    return data
  }
  if (code === 401) {
    return handleUnauthorized(config)
  }
  if (!config.skipErrorToast) {
    message.error(msg || 'Request failed')
  }
  return Promise.reject(new Error(msg || 'Request failed'))
}

//拦截失败响应
const onResponseError = async (error: AxiosError<ApiResponse<unknown>>) => {
  const config = (error.config || {}) as RequestConfig
  clearControllerByConfig(config)

  if (axios.isCancel(error)) {
    return Promise.reject(error)
  }

  const responseCode = error.response?.data?.code
  if (responseCode === 401) {
    return handleUnauthorized(config)
  }

  if (!config.skipErrorToast) {
    message.error(error.response?.data?.message || error.message || 'Network error')
  }
  return Promise.reject(error)
}

//注册响应、请求拦截器
http.interceptors.request.use(onRequest)
http.interceptors.response.use(onResponse as never, onResponseError as never)

//把 http（axios 实例）包了一层，对外暴露统一的 request 对象
const request = {
  get<T>(url: string, config?: RequestConfig) {
    return http.get<ApiResponse<T>, T>(url, config)
  },
  post<T, D = unknown>(url: string, data?: D, config?: RequestConfig) {
    return http.post<ApiResponse<T>, T, D>(url, data, config)
  },
  put<T, D = unknown>(url: string, data?: D, config?: RequestConfig) {
    return http.put<ApiResponse<T>, T, D>(url, data, config)
  },
  delete<T>(url: string, config?: RequestConfig) {
    return http.delete<ApiResponse<T>, T>(url, config)
  },
  request<T>(config: RequestConfig) {
    return http.request<ApiResponse<T>, T>(config)
  },
  cancelRequest(requestId: string) {
    const controller = controllers.get(requestId)
    if (controller) {
      controller.abort()
      controllers.delete(requestId)
    }
  },
  cancelAll() {
    controllers.forEach((controller) => controller.abort())
    controllers.clear()
  },
}

export default request
