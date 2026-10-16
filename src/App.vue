<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { Activity, Bell, ChevronDown, Home, LayoutDashboard, LogOut, Menu, Network, Package, Settings2, X } from 'lucide-vue-next'
import AlertsView from './views/AlertsView.vue'
import DashboardView from './views/DashboardView.vue'
import DevicesView from './views/DevicesView.vue'
import LoginView from './views/LoginView.vue'
import ProductsView from './views/ProductsView.vue'
import SettingsView from './views/SettingsView.vue'
import { usePlatform } from './services/usePlatform'
import type { AlarmRule, Device, Product } from './domain/types'

const platform = usePlatform()
const view = ref('dashboard')
const mobileOpen = ref(false)
const toast = ref<{ message: string; type: 'success' | 'warning' | 'error' } | null>(null)
const devicesView = ref<{ openDevice?: (device: Device) => void } | null>(null)
let toastTimer: number | undefined

const navigation = [
  { id: 'dashboard', label: '总览', group: '平台', icon: LayoutDashboard },
  { id: 'devices', label: '设备', group: '平台', icon: Home },
  { id: 'alarms', label: '告警中心', group: '平台', icon: Bell },
  { id: 'products', label: '产品与属性', group: '管理', icon: Package },
  { id: 'settings', label: '接入与设置', group: '管理', icon: Settings2 },
] as const
const platformNavigation = computed(() => navigation.filter((item) => item.group === '平台'))
const managementNavigation = computed(() => navigation.filter((item) => item.group === '管理'))
const activeAlarmCount = computed(() => platform.state.value.alarms.filter((alarm) => alarm.status === 'active').length)
const pageName = computed(() => navigation.find((item) => item.id === view.value)?.label ?? '总览')

function notify(message: string, type: 'success' | 'warning' | 'error' = 'success') {
  toast.value = { message, type }
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = null }, 3500)
}
function login(email: string, password: string) {
  if (!platform.login(email, password)) return false
  notify('欢迎回来，设备状态已同步')
  return true
}
function navigate(nextView: string) {
  view.value = nextView
  mobileOpen.value = false
}
function openDevice(deviceId: string) {
  const device = platform.state.value.devices.find((item) => item.id === deviceId)
  if (!device) return
  view.value = 'devices'
  mobileOpen.value = false
  nextTick(() => devicesView.value?.openDevice?.(device))
}
function controlDevice(deviceId: string, target: string) {
  const device = platform.state.value.devices.find((item) => item.id === deviceId)
  if (!device) return
  if (!device.enabled || !device.online) {
    platform.controlDevice(deviceId, target)
    notify(device.enabled ? '设备当前离线，命令未下发' : '设备已停用，命令未下发', 'warning')
    return
  }
  platform.controlDevice(deviceId, target)
  notify(device.confirmation === 'gateway' ? '红外网关已发送，目标设备状态未确认' : `${device.name} 正在等待设备确认`)
}
function acknowledge(alarmId: string, note: string) { platform.applyAlarm(alarmId, 'acknowledge', note); notify('告警已确认') }
function closeAlarm(alarmId: string, note: string) { platform.applyAlarm(alarmId, 'close', note); notify('告警已关闭') }
function addProduct(product: Omit<Product, 'id' | 'createdAt' | 'protocol'>) { platform.addProduct(product); notify('产品已创建') }
function updateProduct(product: Product) { platform.updateProduct(product); notify('产品配置已更新') }
function deleteProduct(productId: string) {
  const deleted = platform.deleteProduct(productId)
  notify(deleted ? '产品已删除' : '该产品仍绑定设备，无法删除', deleted ? 'success' : 'warning')
}
function saveRule(rule: AlarmRule) { platform.saveRule(rule); notify('告警规则已保存') }
function saveMqtt(host: string, port: number, clientId: string) { platform.saveMqtt(host, port, clientId); notify('MQTT 连接配置已保存') }
</script>

<template>
  <LoginView v-if="!platform.state.value.user" @login="login" />
  <div v-else class="app-shell">
    <div class="mobile-overlay" :class="{ visible: mobileOpen }" @click="mobileOpen = false"></div>
    <aside class="sidebar" :class="{ open: mobileOpen }">
      <div class="brand"><span class="brand-mark"><Activity :size="19" stroke-width="2.5" /></span><span><strong>OpenIOT</strong><small>家庭设备控制台</small></span><button class="sidebar-close icon-button" aria-label="关闭导航" @click="mobileOpen = false"><X :size="17" /></button></div>
      <div class="home-context"><span class="home-context-icon"><Home :size="15" /></span><span><strong>林远的家</strong><small>本地家庭网络</small></span><ChevronDown :size="15" /></div>
      <nav class="main-nav" aria-label="主导航">
        <div class="nav-group"><span class="nav-group-label">平台</span><button v-for="item in platformNavigation" :key="item.id" class="nav-item" :class="{ active: view === item.id }" @click="navigate(item.id)"><component :is="item.icon" :size="17" /><span>{{ item.label }}</span><b v-if="item.id === 'alarms' && activeAlarmCount">{{ activeAlarmCount }}</b></button></div>
        <div class="nav-group"><span class="nav-group-label">管理</span><button v-for="item in managementNavigation" :key="item.id" class="nav-item" :class="{ active: view === item.id }" @click="navigate(item.id)"><component :is="item.icon" :size="17" /><span>{{ item.label }}</span></button></div>
      </nav>
      <div class="sidebar-bottom"><div class="broker-mini"><span class="connection-indicator" :class="platform.state.value.mqtt.connected ? 'connected' : 'disconnected'"><i></i></span><span><strong>MQTT Broker</strong><small>{{ platform.state.value.mqtt.connected ? '连接正常' : '连接中断' }}</small></span><Network :size="15" /></div><button class="user-menu" @click="navigate('settings')"><span class="avatar">{{ platform.state.value.user.name.slice(0, 1) }}</span><span><strong>{{ platform.state.value.user.name }}</strong><small>{{ platform.state.value.user.role }}</small></span><ChevronDown :size="14" /></button><button class="logout-button" @click="platform.logout()"><LogOut :size="15" />退出登录</button></div>
    </aside>
    <main class="main-content">
      <header class="topbar"><button class="mobile-menu icon-button" aria-label="打开导航" @click="mobileOpen = true"><Menu :size="20" /></button><div class="topbar-context"><span>林远的家</span><ChevronDown :size="14" /></div><div class="topbar-right"><span class="topbar-connection"><i></i>局域网已连接</span><button class="topbar-bell" aria-label="查看告警" @click="navigate('alarms')"><Bell :size="18" /><span v-if="activeAlarmCount">{{ activeAlarmCount }}</span></button><span class="topbar-divider"></span><span class="topbar-user"><span class="avatar">{{ platform.state.value.user.name.slice(0, 1) }}</span>{{ platform.state.value.user.name }}</span></div></header>
      <div class="content-wrap"><div class="content-titlebar"><span class="page-location">OpenIOT <span>/</span> {{ pageName }}</span><span class="sync-state"><i></i>数据已同步</span></div>
        <DashboardView v-if="view === 'dashboard'" :state="platform.state.value" @navigate="navigate" @open-device="openDevice" />
        <DevicesView v-else-if="view === 'devices'" ref="devicesView" :state="platform.state.value" @control="controlDevice" @enabled="platform.setDeviceEnabled" />
        <AlertsView v-else-if="view === 'alarms'" :state="platform.state.value" @acknowledge="acknowledge" @close="closeAlarm" @toggle-rule="platform.toggleRule" @save-rule="saveRule" @create-demo-alarm="platform.createDemoAlarm" />
        <ProductsView v-else-if="view === 'products'" :state="platform.state.value" @add-product="addProduct" @update-product="updateProduct" @delete-product="deleteProduct" @add-device="platform.addDevice" />
        <SettingsView v-else :state="platform.state.value" @save-mqtt="saveMqtt" @toggle-mqtt="platform.toggleMqtt" />
      </div>
    </main>
    <Transition name="toast"><div v-if="toast" class="toast-message" :class="toast.type"><span class="toast-icon"><Activity v-if="toast.type === 'success'" :size="16"/><Bell v-else :size="16"/></span><span>{{ toast.message }}</span><button class="toast-close" aria-label="关闭提示" @click="toast = null"><X :size="14"/></button></div></Transition>
  </div>
</template>
