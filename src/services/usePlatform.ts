import { computed, ref } from 'vue'
import { api, ApiError, hasToken, setToken } from './api'
import type { AlarmRule, CommandRecord, PlatformState, Product, PropertyDefinition } from '../domain/types'

// 平台状态来自后端接口，不再有本地演示数据。
// 所有写操作都用后端返回的最新快照替换本地状态，避免前后端不一致。
const POLL_INTERVAL_MILLIS = 3000

function emptyState(): PlatformState {
  return {
    user: null,
    products: [],
    devices: [],
    commands: [],
    rules: [],
    alarms: [],
    mqtt: { host: '', port: 1883, clientId: '', connected: false, lastCheck: new Date().toISOString(), lastError: null },
  }
}

const snapshot = ref<PlatformState | null>(null)
const loginError = ref('')
let pollTimer: number | undefined

export function usePlatform() {
  const state = computed<PlatformState>(() => snapshot.value ?? emptyState())
  const isAuthenticated = computed(() => snapshot.value?.user != null)

  async function refresh() {
    const next = await api.state()
    snapshot.value = next
    return next
  }

  function start() {
    stop()
    pollTimer = window.setInterval(() => {
      if (!hasToken()) return
      // 后台轮询失败不打扰用户，下一次仍会继续尝试。
      void refresh().catch(() => undefined)
    }, POLL_INTERVAL_MILLIS)

    if (hasToken()) {
      void refresh().catch(() => {
        setToken('')
        snapshot.value = null
      })
    }
  }

  function stop() {
    if (pollTimer !== undefined) {
      window.clearInterval(pollTimer)
      pollTimer = undefined
    }
  }

  async function login(email: string, password: string): Promise<boolean> {
    loginError.value = ''
    try {
      const result = await api.login(email, password)
      setToken(result.token)
      await refresh()
      return true
    } catch (error) {
      snapshot.value = null
      loginError.value = error instanceof ApiError ? error.message : '登录失败，请稍后重试'
      return false
    }
  }

  async function logout() {
    try {
      await api.logout()
    } catch {
      // 令牌已失效时忽略，本地状态照样清空。
    }
    setToken('')
    snapshot.value = null
  }

  async function mutate(action: () => Promise<PlatformState>) {
    snapshot.value = await action()
    return snapshot.value
  }

  function toPropertyPayload(property: PropertyDefinition) {
    return {
      id: property.id || undefined,
      name: property.name,
      key: property.key,
      type: property.type,
      unit: property.unit,
      min: property.min,
      max: property.max,
      options: property.options,
    }
  }

  function toProductPayload(product: Omit<Product, 'id' | 'createdAt' | 'protocol'>) {
    return {
      name: product.name,
      category: product.category,
      description: product.description,
      status: product.status,
      properties: product.properties.map(toPropertyPayload),
    }
  }

  /** 下发目标状态命令，返回该设备最新的命令记录。 */
  async function controlDevice(
    deviceId: string,
    target: Record<string, unknown>,
  ): Promise<CommandRecord | null> {
    const next = await mutate(() => api.sendCommand(deviceId, target))
    return next.commands.find((command) => command.deviceId === deviceId) ?? null
  }

  async function setDeviceEnabled(deviceId: string, enabled: boolean) {
    await mutate(() => api.setDeviceEnabled(deviceId, enabled))
  }

  async function addProduct(product: Omit<Product, 'id' | 'createdAt' | 'protocol'>) {
    await mutate(() => api.createProduct(toProductPayload(product)))
  }

  async function updateProduct(product: Product) {
    await mutate(() => api.updateProduct(product.id, toProductPayload(product)))
  }

  async function deleteProduct(productId: string) {
    await mutate(() => api.deleteProduct(productId))
  }

  async function addDevice(input: {
    name: string
    productId: string
    code: string
    location: string
    description: string
  }) {
    await mutate(() => api.createDevice(input))
  }

  async function saveRule(rule: AlarmRule) {
    await mutate(() => api.saveRule(rule))
  }

  async function setRuleEnabled(ruleId: string, enabled: boolean) {
    await mutate(() => api.setRuleEnabled(ruleId, enabled))
  }

  async function applyAlarm(alarmId: string, action: 'acknowledge' | 'close', note: string) {
    await mutate(() => (action === 'acknowledge'
      ? api.acknowledgeAlarm(alarmId, note)
      : api.closeAlarm(alarmId, note)))
  }

  async function saveMqtt(host: string, port: number, clientId: string) {
    await mutate(() => api.saveMqtt(host, port, clientId))
  }

  async function reconnectMqtt() {
    await mutate(() => api.reconnectMqtt())
  }

  return {
    state,
    isAuthenticated,
    loginError,
    login,
    logout,
    start,
    stop,
    refresh,
    controlDevice,
    setDeviceEnabled,
    addProduct,
    updateProduct,
    deleteProduct,
    addDevice,
    saveRule,
    setRuleEnabled,
    applyAlarm,
    saveMqtt,
    reconnectMqtt,
  }
}
