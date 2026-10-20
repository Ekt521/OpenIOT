<script setup lang="ts">
import { computed, ref } from 'vue'
import { Activity, ArrowDownRight, ArrowUpRight, Bell, ChevronRight, Clock3, Lightbulb, Radio, Thermometer, Wifi, WifiOff, Zap } from 'lucide-vue-next'
import type { Device, PlatformState } from '../domain/types'
import { confirmationLabel } from '../domain/platform'

const props = defineProps<{ state: PlatformState }>()
const emit = defineEmits<{ openDevice: [deviceId: string]; navigate: [view: string] }>()
const period = ref('24h')
const onlineCount = computed(() => props.state.devices.filter((device) => device.enabled && device.online).length)
const activeCount = computed(() => props.state.alarms.filter((alarm) => alarm.status !== 'closed').length)
const criticalCount = computed(() => props.state.alarms.filter((alarm) => alarm.status === 'active' && alarm.level === 'critical').length)
const featuredDevices = computed(() => props.state.devices.filter((device) => device.enabled).slice(0, 4))
const recentCommands = computed(() => props.state.commands.slice(0, 4))
const envDevice = computed(() => props.state.devices.find((device) => device.state.temperature !== undefined))
const chartPoints = computed(() => {
  const history = envDevice.value?.history.slice(-12) ?? []
  if (!history.length) return []
  const width = 600
  const height = 190
  const values = history.map((point) => point.temperature ?? 0)
  const low = Math.min(...values) - 1
  const high = Math.max(...values) + 1
  return history.map((point, index) => ({
    x: 8 + index * (width - 16) / Math.max(history.length - 1, 1),
    y: 12 + (high - (point.temperature ?? 0)) * (height - 24) / Math.max(high - low, 1),
    label: new Date(point.timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    value: point.temperature,
  }))
})
const chartLine = computed(() => chartPoints.value.map((point) => `${point.x},${point.y}`).join(' '))
const chartArea = computed(() => chartPoints.value.length ? `8,190 ${chartLine.value} 592,190` : '')
const latestTemperature = computed(() => envDevice.value?.state.temperature ?? '--')
const latestHumidity = computed(() => envDevice.value?.state.humidity ?? '--')

function relativeTime(value: string | null) {
  if (!value) return '尚未上报'
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60_000))
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  return hours < 24 ? `${hours} 小时前` : `${Math.floor(hours / 24)} 天前`
}
function productName(device: Device) {
  return props.state.products.find((product) => product.id === device.productId)?.name ?? '未知产品'
}
</script>

<template>
  <div class="page-stack">
    <section class="welcome-row">
      <div>
        <div class="eyebrow"><span class="live-dot"></span> 家庭设备运行正常</div>
        <h1>早上好，{{ state.user?.name || '管理员' }}</h1>
        <p>这是你家设备今天的运行概况。</p>
      </div>
      <div class="date-stamp"><Clock3 :size="15" /><span>{{ new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }) }}</span></div>
    </section>

    <section class="metric-grid" aria-label="设备运行指标">
      <button class="metric-card metric-total" @click="emit('navigate', 'devices')">
        <span class="metric-icon"><Radio :size="18" /></span><span class="metric-label">设备总数</span>
        <strong>{{ state.devices.length }}<small> 台</small></strong><span class="metric-foot">来自 {{ state.products.length }} 个产品</span>
      </button>
      <button class="metric-card metric-online" @click="emit('navigate', 'devices')">
        <span class="metric-icon"><Wifi :size="18" /></span><span class="metric-label">当前在线</span>
        <strong>{{ onlineCount }}<small> / {{ state.devices.filter((device) => device.enabled).length }} 台</small></strong><span class="metric-foot"><span class="metric-trend"><ArrowUpRight :size="13" />稳定连接</span></span>
      </button>
      <button class="metric-card metric-alarm" @click="emit('navigate', 'alarms')">
        <span class="metric-icon"><Bell :size="18" /></span><span class="metric-label">待处理告警</span>
        <strong>{{ activeCount }}<small> 条</small></strong><span class="metric-foot"><span v-if="criticalCount" class="critical-text">{{ criticalCount }} 条紧急</span><span v-else>暂无紧急事件</span></span>
      </button>
      <button class="metric-card metric-env" @click="envDevice && emit('openDevice', envDevice.id)">
        <span class="metric-icon"><Thermometer :size="18" /></span><span class="metric-label">室内温度</span>
        <strong>{{ latestTemperature }}<small> °C</small></strong><span class="metric-foot">湿度 {{ latestHumidity }}%</span>
      </button>
    </section>

    <section class="dashboard-grid">
      <article class="surface chart-panel">
        <header class="section-header">
          <div><div class="section-kicker">环境监测</div><h2>室内温度</h2></div>
          <div class="segmented-control" role="group" aria-label="图表时间范围">
            <button v-for="option in [{ id: '1h', label: '1 小时' }, { id: '24h', label: '24 小时' }, { id: '7d', label: '7 天' }]" :key="option.id" :class="{ selected: period === option.id }" @click="period = option.id">{{ option.label }}</button>
          </div>
        </header>
        <div class="chart-summary"><strong>{{ latestTemperature }}<small>°C</small></strong><span class="chart-delta"><ArrowDownRight :size="15" />较昨日低 0.8°</span><span class="chart-subtitle">主卧环境传感器 · 每 5 分钟更新</span></div>
        <div class="chart-wrap">
          <div class="y-labels"><span>28°</span><span>26°</span><span>24°</span><span>22°</span></div>
          <svg class="trend-chart" viewBox="0 0 600 200" role="img" aria-label="过去 24 小时室内温度趋势">
            <defs><linearGradient id="chart-fill" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stop-color="#18a999" stop-opacity=".22"/><stop offset="100%" stop-color="#18a999" stop-opacity="0"/></linearGradient></defs>
            <line v-for="y in [15, 70, 125, 180]" :key="y" x1="8" :y1="y" x2="592" :y2="y" class="chart-gridline" />
            <polygon :points="chartArea" fill="url(#chart-fill)" />
            <polyline :points="chartLine" class="chart-line" />
            <circle v-for="(point, index) in chartPoints" :key="index" :cx="point.x" :cy="point.y" r="3.5" class="chart-point"><title>{{ point.label }} · {{ point.value }}°C</title></circle>
          </svg>
        </div>
        <div class="chart-xlabels"><span v-for="(point, index) in chartPoints.filter((_, index) => index % 3 === 0 || index === chartPoints.length - 1)" :key="index">{{ point.label }}</span></div>
      </article>

      <article class="surface home-panel">
        <header class="section-header"><div><div class="section-kicker">空间状态</div><h2>家庭设备</h2></div><button class="text-action" @click="emit('navigate', 'devices')">查看全部 <ChevronRight :size="15" /></button></header>
        <div class="device-compact-list">
          <button v-for="device in featuredDevices" :key="device.id" class="device-compact" @click="emit('openDevice', device.id)">
            <span class="device-type-icon" :class="device.productId === 'prod-ir' ? 'icon-orange' : device.state.temperature ? 'icon-blue' : 'icon-green'"><Thermometer v-if="device.state.temperature !== undefined" :size="18"/><Radio v-else-if="device.confirmation === 'gateway'" :size="18"/><Lightbulb v-else :size="18"/></span>
            <span class="device-compact-info"><strong>{{ device.name }}</strong><small>{{ device.location }} · {{ productName(device) }}</small></span>
            <span class="state-col"><span class="status-label" :class="device.online && device.enabled ? 'status-online' : 'status-offline'"><i></i>{{ device.enabled ? (device.online ? '在线' : '离线') : '已停用' }}</span><small v-if="device.confirmation === 'gateway'">状态未确认</small><small v-else>{{ relativeTime(device.lastSeen) }}</small></span>
            <ChevronRight class="row-chevron" :size="15" />
          </button>
        </div>
        <div class="home-panel-footer"><span><Wifi :size="14" /> MQTT Broker</span><span class="connection-indicator" :class="state.mqtt.connected ? 'connected' : 'disconnected'"><i></i>{{ state.mqtt.connected ? '连接正常' : '连接中断' }}</span></div>
      </article>
    </section>

    <section class="bottom-grid">
      <article class="surface command-panel">
        <header class="section-header"><div><div class="section-kicker">最近操作</div><h2>控制命令</h2></div><button class="text-action" @click="emit('navigate', 'devices')">设备控制 <ChevronRight :size="15" /></button></header>
        <div v-if="recentCommands.length" class="command-list">
          <div v-for="command in recentCommands" :key="command.id" class="command-row">
            <span class="command-symbol" :class="command.status"><Zap :size="15" /></span>
            <span class="command-info"><strong>{{ command.deviceName }}<small> · {{ command.target }}</small></strong><small>{{ relativeTime(command.createdAt) }} · {{ confirmationLabel(command.confirmation) }}</small></span>
            <span class="command-status" :class="command.status">{{ command.status === 'confirmed' ? '已确认' : command.status === 'pending' ? '处理中' : command.status === 'unreachable' ? '不可达' : command.status === 'unknown' ? '结果未知' : '已发送' }}</span>
          </div>
        </div>
        <div v-else class="empty-inline"><Activity :size="17"/>暂无控制记录</div>
      </article>
      <article class="surface alert-panel">
        <header class="section-header"><div><div class="section-kicker">需要关注</div><h2>最新告警</h2></div><button class="text-action" @click="emit('navigate', 'alarms')">告警中心 <ChevronRight :size="15" /></button></header>
        <button v-for="alarm in state.alarms.filter((item) => item.status !== 'closed').slice(0, 2)" :key="alarm.id" class="alert-row" @click="emit('navigate', 'alarms')">
          <span class="alert-mark" :class="alarm.level"><WifiOff v-if="alarm.ruleName === '设备离线'" :size="16"/><Activity v-else :size="16"/></span>
          <span class="alert-info"><strong>{{ alarm.ruleName }}</strong><small>{{ alarm.deviceName }} · {{ alarm.message }}</small></span>
          <span class="alert-time">{{ relativeTime(alarm.createdAt) }}</span>
        </button>
        <div v-if="!activeCount" class="empty-inline">当前没有待处理告警</div>
      </article>
    </section>
  </div>
</template>
