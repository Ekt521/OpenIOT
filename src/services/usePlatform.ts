import { computed, ref } from 'vue'
import { platformRepository } from './repository'
import { createCommand, makeAlarm, resolveCommand, updateAlarmStatus } from '../domain/platform'
import type { AlarmRule, Device, DeviceEvent, PlatformState, Product, PropertyDefinition, User } from '../domain/types'

const state = ref<PlatformState>(platformRepository.get())
platformRepository.subscribe((next) => { state.value = next })

export function usePlatform() {
  const onlineDevices = computed(() => state.value.devices.filter((device) => device.enabled && device.online))
  const activeAlarms = computed(() => state.value.alarms.filter((alarm) => alarm.status !== 'closed'))

  function update(mutator: (current: PlatformState) => PlatformState) {
    platformRepository.update(mutator)
  }

  function login(email: string, password: string): boolean {
    if (!email.trim() || password.length < 4) return false
    const user: User = { id: 'user-admin', name: email.toLowerCase().startsWith('ops') ? '陈晓' : '林远', email, role: '管理员' }
    update((current) => ({ ...current, user }))
    return true
  }

  function logout() {
    update((current) => ({ ...current, user: null }))
  }

  function controlDevice(deviceId: string, target: string) {
    let commandId = ''
    update((current) => {
      const device = current.devices.find((entry) => entry.id === deviceId)
      if (!device) return current
      const command = createCommand(device, target)
      commandId = command.id
      const event: DeviceEvent = { id: crypto.randomUUID(), type: 'command', message: command.message, timestamp: command.createdAt }
      const nextDevice = { ...device, targetState: target === '开启' || target === '关闭' ? { ...device.targetState, power: target === '开启' } : device.targetState, events: [event, ...device.events].slice(0, 30) }
      return {
        ...current,
        devices: current.devices.map((entry) => entry.id === deviceId ? nextDevice : entry),
        commands: [command, ...current.commands].slice(0, 100),
      }
    })
    if (commandId) {
      window.setTimeout(() => {
        update((current) => {
          const command = current.commands.find((entry) => entry.id === commandId)
          const device = current.devices.find((entry) => entry.id === deviceId)
          if (!command || !device || command.status !== 'pending') return current
          const result = resolveCommand(command, device)
          return {
            ...current,
            commands: current.commands.map((entry) => entry.id === commandId ? result.command : entry),
            devices: current.devices.map((entry) => entry.id === deviceId ? result.device : entry),
          }
        })
      }, 1200)
    }
  }

  function makeEvent(type: DeviceEvent['type'], message: string): DeviceEvent {
    return { id: crypto.randomUUID(), type, message, timestamp: new Date().toISOString() }
  }

  function setDeviceEnabled(deviceId: string, enabled: boolean) {
    update((current) => ({
      ...current,
      devices: current.devices.map((device) => device.id === deviceId
        ? { ...device, enabled, online: enabled ? device.online : false, events: [makeEvent('config', enabled ? '设备已启用' : '设备已停用'), ...device.events].slice(0, 30) }
        : device),
    }))
  }

  function addProduct(input: Omit<Product, 'id' | 'createdAt' | 'protocol'>) {
    const product: Product = { ...input, id: crypto.randomUUID(), createdAt: new Date().toISOString(), protocol: 'MQTT' }
    update((current) => ({ ...current, products: [product, ...current.products] }))
  }

  function updateProduct(product: Product) {
    update((current) => ({ ...current, products: current.products.map((item) => item.id === product.id ? product : item) }))
  }

  function deleteProduct(productId: string): boolean {
    if (state.value.devices.some((device) => device.productId === productId)) return false
    update((current) => ({ ...current, products: current.products.filter((product) => product.id !== productId) }))
    return true
  }

  function addDevice(input: Pick<Device, 'name' | 'productId' | 'code' | 'location' | 'description'>) {
    const product = state.value.products.find((item) => item.id === input.productId)
    if (!product) return
    const stateValues: Device['state'] = {}
    product.properties.forEach((property: PropertyDefinition) => {
      if (property.type === 'boolean') stateValues[property.key] = false
      else if (property.type === 'number') stateValues[property.key] = 0
      else stateValues[property.key] = ''
    })
    const device: Device = {
      ...input,
      id: crypto.randomUUID(),
      enabled: true,
      online: false,
      lastSeen: new Date().toISOString(),
      confirmation: product.category === '红外网关' ? 'gateway' : 'state',
      capabilities: product.properties.map((property) => property.name),
      state: stateValues,
      targetState: {},
      history: [],
      events: [{ id: crypto.randomUUID(), type: 'config', message: '设备已创建，等待首次连接', timestamp: new Date().toISOString() }],
      credential: `oi_${crypto.randomUUID().replace(/-/g, '').slice(0, 16)}`,
    }
    update((current) => ({ ...current, devices: [device, ...current.devices] }))
  }

  function saveRule(rule: AlarmRule) {
    update((current) => ({ ...current, rules: current.rules.some((item) => item.id === rule.id)
      ? current.rules.map((item) => item.id === rule.id ? rule : item)
      : [rule, ...current.rules] }))
  }

  function toggleRule(ruleId: string) {
    update((current) => ({ ...current, rules: current.rules.map((rule) => rule.id === ruleId ? { ...rule, enabled: !rule.enabled } : rule) }))
  }

  function applyAlarm(alarmId: string, action: 'acknowledge' | 'close', note: string) {
    update((current) => ({ ...current, alarms: current.alarms.map((alarm) => alarm.id === alarmId ? updateAlarmStatus(alarm, action, note) : alarm) }))
  }

  function createDemoAlarm(ruleId: string) {
    const rule = state.value.rules.find((item) => item.id === ruleId)
    if (!rule) return
    const device = state.value.devices.find((item) => rule.deviceId === 'all' || item.id === rule.deviceId)
    if (!device) return
    const alarm = makeAlarm(rule, device)
    update((current) => ({ ...current, alarms: [alarm, ...current.alarms] }))
  }

  function saveMqtt(host: string, port: number, clientId: string) {
    update((current) => ({ ...current, mqtt: { ...current.mqtt, host, port, clientId, lastCheck: new Date().toISOString() } }))
  }

  function toggleMqtt() {
    update((current) => ({ ...current, mqtt: { ...current.mqtt, connected: !current.mqtt.connected, lastCheck: new Date().toISOString() } }))
  }

  return { state, onlineDevices, activeAlarms, login, logout, controlDevice, setDeviceEnabled, addProduct, updateProduct, deleteProduct, addDevice, saveRule, toggleRule, applyAlarm, createDemoAlarm, saveMqtt, toggleMqtt, update }
}
