import type { AlarmRule, PlatformState, User } from '../domain/types'

const TOKEN_KEY = 'openiot-token'
const BASE_URL = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080'

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

let token = localStorage.getItem(TOKEN_KEY) ?? ''

export function setToken(value: string) {
  token = value
  if (value) localStorage.setItem(TOKEN_KEY, value)
  else localStorage.removeItem(TOKEN_KEY)
}

export function hasToken(): boolean {
  return token.length > 0
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, { ...init, headers })
  } catch {
    throw new ApiError(0, '无法连接平台服务，请确认后端已启动')
  }

  if (!response.ok) {
    let message = `请求失败（HTTP ${response.status}）`
    try {
      const body = await response.json() as { message?: string }
      if (body?.message) message = body.message
    } catch {
      // 响应体不是 JSON 时保留默认提示。
    }
    throw new ApiError(response.status, message)
  }

  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export interface ProductPayload {
  name: string
  category: string
  description: string
  status: string
  properties: Array<{
    id?: string
    name: string
    key: string
    type: string
    unit: string
    min?: number
    max?: number
    options?: string[]
  }>
}

export interface DevicePayload {
  name: string
  productId: string
  code: string
  location: string
  description: string
}

export const api = {
  login(email: string, password: string) {
    return request<{ token: string; user: User }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
  },
  logout() {
    return request<void>('/api/auth/logout', { method: 'POST' })
  },
  state() {
    return request<PlatformState>('/api/state')
  },
  createProduct(payload: ProductPayload) {
    return request<PlatformState>('/api/products', { method: 'POST', body: JSON.stringify(payload) })
  },
  updateProduct(id: string, payload: ProductPayload) {
    return request<PlatformState>(`/api/products/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
  },
  deleteProduct(id: string) {
    return request<PlatformState>(`/api/products/${id}`, { method: 'DELETE' })
  },
  createDevice(payload: DevicePayload) {
    return request<PlatformState>('/api/devices', { method: 'POST', body: JSON.stringify(payload) })
  },
  setDeviceEnabled(id: string, enabled: boolean) {
    return request<PlatformState>(`/api/devices/${id}/enabled`, {
      method: 'PATCH',
      body: JSON.stringify({ enabled }),
    })
  },
  sendCommand(id: string, target: Record<string, unknown>) {
    return request<PlatformState>(`/api/devices/${id}/commands`, {
      method: 'POST',
      body: JSON.stringify({ target }),
    })
  },
  acknowledgeAlarm(id: string, note: string) {
    return request<PlatformState>(`/api/alarms/${id}/acknowledge`, {
      method: 'POST',
      body: JSON.stringify({ note }),
    })
  },
  closeAlarm(id: string, note: string) {
    return request<PlatformState>(`/api/alarms/${id}/close`, {
      method: 'POST',
      body: JSON.stringify({ note }),
    })
  },
  saveRule(rule: AlarmRule) {
    return request<PlatformState>('/api/rules', { method: 'POST', body: JSON.stringify(rule) })
  },
  setRuleEnabled(id: string, enabled: boolean) {
    return request<PlatformState>(`/api/rules/${id}/enabled`, {
      method: 'PATCH',
      body: JSON.stringify({ enabled }),
    })
  },
  saveMqtt(host: string, port: number, clientId: string) {
    return request<PlatformState>('/api/settings/mqtt', {
      method: 'PUT',
      body: JSON.stringify({ host, port, clientId }),
    })
  },
  reconnectMqtt() {
    return request<PlatformState>('/api/settings/mqtt/reconnect', { method: 'POST' })
  },
}
