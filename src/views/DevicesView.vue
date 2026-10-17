<script setup lang="ts">
import { computed, ref } from 'vue'
import { Activity, ChevronRight, Clock3, Eye, KeyRound, Lightbulb, Radio, RefreshCw, Search, Thermometer, Wifi, WifiOff, X, Zap } from 'lucide-vue-next'
import type { Device, PlatformState } from '../domain/types'
import { canControl, confirmationLabel } from '../domain/platform'

const props = defineProps<{ state: PlatformState }>()
const emit = defineEmits<{ control: [deviceId: string, target: Record<string, unknown>]; enabled: [deviceId: string, enabled: boolean] }>()
const query = ref('')
const statusFilter = ref('all')
const productFilter = ref('all')
const selected = ref<Device | null>(null)
const timeRange = ref('24h')
const showCredential = ref(false)
const filteredDevices = computed(() => props.state.devices.filter((device) => {
  const search = `${device.name} ${device.code} ${device.location}`.toLowerCase()
  return (!query.value || search.includes(query.value.toLowerCase()))
    && (statusFilter.value === 'all' || (statusFilter.value === 'online' ? device.online && device.enabled : !device.online || !device.enabled))
    && (productFilter.value === 'all' || device.productId === productFilter.value)
}))
const detailHistory = computed(() => selected.value?.history.slice(timeRange.value === '1h' ? -4 : timeRange.value === '24h' ? -12 : undefined) ?? [])
const historyPath = computed(() => {
  const values = detailHistory.value.map((point) => point.temperature ?? point.energy ?? 0)
  if (values.length < 2) return ''
  const min = Math.min(...values) - 1
  const max = Math.max(...values) + 1
  return values.map((value, index) => `${8 + index * 584 / (values.length - 1)},${175 - (value - min) * 140 / Math.max(max - min, 1)}`).join(' ')
})
const historyKey = computed(() => selected.value?.state.temperature !== undefined ? 'temperature' : 'energy')
const detailCommands = computed(() => props.state.commands.filter((command) => command.deviceId === selected.value?.id).slice(0, 6))
const detailEvents = computed(() => selected.value?.events.slice(0, 6) ?? [])
const selectedProduct = computed(() => props.state.products.find((product) => product.id === selected.value?.productId))
const onlineCount = computed(() => props.state.devices.filter((device) => device.enabled && device.online).length)

function productName(device: Device) { return props.state.products.find((product) => product.id === device.productId)?.name ?? '未知产品' }
function timeAgo(value: string | null) {
  if (!value) return '尚未上报'
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60_000))
  return minutes < 1 ? '刚刚' : minutes < 60 ? `${minutes} 分钟前` : `${Math.floor(minutes / 60)} 小时前`
}
function deviceMetric(device: Device) {
  if (device.controlProfile === 'tv') return { label: '网络可达', value: device.online ? '可达' : '不可达' }
  if (typeof device.state.temperature === 'number') return { label: '温度', value: `${device.state.temperature.toFixed(1)} °C` }
  if (typeof device.state.power === 'boolean') return { label: '开关状态', value: device.state.power ? '已开启' : '已关闭' }
  if (device.confirmation === 'gateway') return { label: '控制状态', value: '状态未确认' }
  return { label: '最近通信', value: timeAgo(device.lastSeen) }
}
function openDevice(device: Device) { selected.value = device; showCredential.value = false }
function control(device: Device, target: Record<string, unknown>) { emit('control', device.id, target) }
function refreshSelection() { if (selected.value) selected.value = props.state.devices.find((device) => device.id === selected.value?.id) ?? null }
defineExpose({ openDevice })
</script>

<template>
  <div class="page-stack">
    <section class="page-heading">
      <div><div class="breadcrumb">平台 <ChevronRight :size="13" /> 设备管理</div><h1>设备</h1><p>查看家庭设备状态、数据与控制记录。</p></div>
      <div class="heading-actions"><span class="last-refresh"><RefreshCw :size="14"/> 实时更新</span></div>
    </section>
    <section class="device-toolbar surface-toolbar">
      <label class="search-control"><Search :size="16"/><input v-model="query" placeholder="搜索设备名称、编号或位置" aria-label="搜索设备"/><button v-if="query" class="clear-search" aria-label="清除搜索" @click="query = ''"><X :size="14"/></button></label>
      <select v-model="productFilter" aria-label="按产品筛选"><option value="all">所有产品</option><option v-for="product in state.products" :key="product.id" :value="product.id">{{ product.name }}</option></select>
      <select v-model="statusFilter" aria-label="按状态筛选"><option value="all">所有状态</option><option value="online">在线</option><option value="offline">离线 / 停用</option></select>
      <div class="toolbar-count">{{ filteredDevices.length }} 台设备</div>
    </section>
    <section class="surface device-table-surface">
      <div class="table-topline"><div><strong>所有设备</strong><span>{{ onlineCount }} 台在线，共 {{ state.devices.length }} 台</span></div><button class="icon-button" aria-label="刷新设备状态"><RefreshCw :size="16"/></button></div>
      <div class="table-scroll"><table class="data-table"><thead><tr><th>设备</th><th>产品</th><th>状态</th><th>当前数据</th><th>最后通信</th><th>确认能力</th><th></th></tr></thead>
        <tbody><tr v-for="device in filteredDevices" :key="device.id" class="clickable-row" @click="openDevice(device)">
          <td><div class="table-device"><span class="mini-device-icon" :class="device.confirmation === 'gateway' ? 'orange' : device.state.temperature !== undefined ? 'blue' : 'green'"><Thermometer v-if="device.state.temperature !== undefined" :size="16"/><Radio v-else-if="device.confirmation === 'gateway'" :size="16"/><Lightbulb v-else :size="16"/></span><span><strong>{{ device.name }}</strong><small>{{ device.code }} · {{ device.location }}</small></span></div></td>
          <td>{{ productName(device) }}</td>
          <td><span class="status-label" :class="device.enabled && device.online ? 'status-online' : 'status-offline'"><i></i>{{ !device.enabled ? '已停用' : device.online ? '在线' : '离线' }}</span></td>
          <td><span class="metric-cell">{{ deviceMetric(device).value }}</span><small class="cell-caption">{{ deviceMetric(device).label }}</small></td>
          <td><span class="metric-cell">{{ timeAgo(device.lastSeen) }}</span></td>
          <td><span class="capability-chip" :class="device.confirmation">{{ device.confirmation === 'state' ? '状态可确认' : '网关发送' }}</span></td>
          <td><ChevronRight class="table-chevron" :size="16"/></td>
        </tr><tr v-if="!filteredDevices.length"><td colspan="7" class="table-empty"><Search :size="18"/>没有符合条件的设备</td></tr></tbody></table></div>
    </section>

    <Teleport to="body">
      <div v-if="selected" class="modal-backdrop" @click.self="selected = null" @keydown.esc="selected = null">
        <section class="device-drawer" role="dialog" aria-modal="true" :aria-label="`${selected.name} 设备详情`">
          <header class="drawer-header"><button class="back-link" @click="selected = null">设备列表</button><div class="drawer-head-actions"><button class="icon-button" aria-label="刷新详情" @click="refreshSelection"><RefreshCw :size="16"/></button><button class="icon-button" aria-label="关闭详情" @click="selected = null"><X :size="18"/></button></div></header>
          <div class="drawer-scroll">
            <div class="detail-title-row"><span class="detail-device-icon" :class="selected.confirmation === 'gateway' ? 'orange' : selected.state.temperature !== undefined ? 'blue' : 'green'"><Thermometer v-if="selected.state.temperature !== undefined" :size="22"/><Radio v-else-if="selected.confirmation === 'gateway'" :size="22"/><Lightbulb v-else :size="22"/></span><div><div class="detail-title-meta">{{ productName(selected) }} <span>·</span> {{ selected.code }}</div><h2>{{ selected.name }}</h2></div><span class="status-label" :class="selected.enabled && selected.online ? 'status-online' : 'status-offline'"><i></i>{{ !selected.enabled ? '已停用' : selected.online ? '在线' : '离线' }}</span></div>
            <p class="detail-description">{{ selected.description }}</p>
            <div class="detail-meta-line"><span><Radio :size="14"/> {{ selected.location }}</span><span><Clock3 :size="14"/> 最近通信 {{ timeAgo(selected.lastSeen) }}</span></div>
            <section v-if="selected.controlProfile === 'tv'" class="control-section">
              <div class="subsection-heading"><div><h3>电视遥控</h3><p>只能确认指令已送达，无法读取实际开关状态</p></div><span class="capability-chip gateway">状态未确认</span></div>
              <div class="hvac-buttons">
                <button :disabled="!canControl(selected)" @click="control(selected, { key: 'power' })"><Zap :size="15"/>电源（切换）</button>
                <button :disabled="!canControl(selected)" @click="control(selected, { key: 'volumeup' })">音量 +</button>
                <button :disabled="!canControl(selected)" @click="control(selected, { key: 'volumedown' })">音量 -</button>
                <button :disabled="!canControl(selected)" @click="control(selected, { key: 'mute' })">静音</button>
                <button :disabled="!canControl(selected)" @click="control(selected, { key: 'home' })">主页</button>
                <button :disabled="!canControl(selected)" @click="control(selected, { key: 'back' })">返回</button>
                <button :disabled="!canControl(selected)" @click="control(selected, { key: 'up' })">上</button>
                <button :disabled="!canControl(selected)" @click="control(selected, { key: 'down' })">下</button>
                <button :disabled="!canControl(selected)" @click="control(selected, { key: 'left' })">左</button>
                <button :disabled="!canControl(selected)" @click="control(selected, { key: 'right' })">右</button>
                <button :disabled="!canControl(selected)" @click="control(selected, { key: 'enter' })">确定</button>
              </div>
            </section>

            <section v-if="selected.controlProfile !== 'tv' && selected.confirmation === 'state' && typeof selected.state.power === 'boolean'" class="control-section">
              <div class="subsection-heading"><div><h3>开关控制</h3><p>当前状态 {{ selected.state.power ? '开启' : '关闭' }}<span v-if="selected.targetState.power !== selected.state.power"> · 目标 {{ selected.targetState.power ? '开启' : '关闭' }}</span></p></div><span class="actual-state-indicator" :class="selected.state.power ? 'is-on' : ''"><i></i>实际状态</span></div>
              <div class="power-control"><span class="power-light" :class="selected.state.power ? 'on' : ''"><Lightbulb :size="22"/></span><span class="power-control-text"><strong>{{ selected.state.power ? '已开启' : '已关闭' }}</strong><small>设备回报的实际状态</small></span><button class="power-button" :class="selected.state.power ? 'is-on' : ''" :disabled="!canControl(selected)" :aria-pressed="selected.state.power" @click="control(selected, { power: !selected.state.power })"><span></span>{{ selected.state.power ? '关闭' : '开启' }}</button></div>
            </section>
            <section v-else-if="selected.controlProfile !== 'tv' && selected.confirmation === 'gateway'" class="control-section ir-control-section">
              <div class="subsection-heading"><div><h3>红外控制</h3><p>目标状态与家电实际状态可能不同</p></div><span class="capability-chip gateway">仅网关发送</span></div>
              <div class="infrared-controls"><span class="hvac-state"><span class="hvac-icon"><Activity :size="19"/></span><span><strong>{{ selected.targetState.power ? `${selected.targetState.temperature}°C · ${selected.targetState.mode}` : '目标：关闭' }}</strong><small>家电当前状态未确认</small></span></span><div class="hvac-buttons"><button :disabled="!canControl(selected)" @click="control(selected, { power: true })"><Lightbulb :size="15"/>开机</button><button :disabled="!canControl(selected)" @click="control(selected, { temperature: 24, mode: '制冷' })"><Thermometer :size="15"/>24° 制冷</button><button :disabled="!canControl(selected)" @click="control(selected, { power: false })"><Zap :size="15"/>关机</button></div></div>
              <p class="inline-warning"><span>!</span> 红外命令只确认网关已发送，无法证明空调已执行。</p>
            </section>
            <section v-if="selected.state.temperature !== undefined || selected.state.energy !== undefined" class="detail-section"><div class="subsection-heading"><div><h3>实时数据</h3><p>最近一次设备上报</p></div><span class="data-freshness"><i></i>实时</span></div><div class="telemetry-grid"><div v-if="selected.state.temperature !== undefined" class="telemetry-item"><Thermometer :size="16"/><span>温度</span><strong>{{ Number(selected.state.temperature).toFixed(1) }}<small>°C</small></strong></div><div v-if="selected.state.humidity !== undefined" class="telemetry-item"><Activity :size="16"/><span>相对湿度</span><strong>{{ selected.state.humidity }}<small>%</small></strong></div><div v-if="selected.state.energy !== undefined" class="telemetry-item"><Zap :size="16"/><span>累计电量</span><strong>{{ Number(selected.state.energy).toFixed(2) }}<small>kWh</small></strong></div></div></section>
            <section v-if="selected.history.length" class="detail-section"><div class="subsection-heading"><div><h3>历史趋势</h3><p>{{ historyKey === 'temperature' ? '室内温度 · °C' : '累计电量 · kWh' }}</p></div><div class="segmented-control small"><button v-for="option in [{id:'1h',label:'1 小时'},{id:'24h',label:'24 小时'},{id:'7d',label:'7 天'}]" :key="option.id" :class="{selected:timeRange===option.id}" @click="timeRange=option.id">{{ option.label }}</button></div></div><div class="detail-chart"><svg viewBox="0 0 600 190" role="img" aria-label="设备历史数据趋势"><line v-for="y in [20,65,110,155]" :key="y" x1="8" :y1="y" x2="592" :y2="y" class="chart-gridline"/><polyline :points="historyPath" class="chart-line"/><circle v-if="detailHistory.length" :cx="592" :cy="Number(historyPath.split(' ').at(-1)?.split(',')[1] ?? 0)" r="4" class="chart-endpoint"/></svg><div class="detail-chart-legend"><span class="legend-mark"></span>{{ historyKey === 'temperature' ? '温度' : '累计电量' }} <strong>{{ historyKey === 'temperature' ? `${selected.history.at(-1)?.temperature ?? '--'} °C` : `${selected.history.at(-1)?.energy ?? '--'} kWh` }}</strong></div></div></section>
            <section class="detail-section"><div class="subsection-heading"><div><h3>设备接入</h3><p>MQTT 身份凭据</p></div><KeyRound :size="17" class="muted-icon"/></div><div class="credential-row"><code>{{ showCredential ? selected.credential.replace('••••••••', 'device_secret_') : selected.credential }}</code><button class="icon-button" :aria-label="showCredential ? '隐藏设备密钥' : '显示设备密钥'" @click="showCredential=!showCredential"><Eye v-if="!showCredential" :size="15"/><span v-else>隐藏</span></button></div><div class="topic-row"><span>遥测主题</span><code>openiot/{{ selected.code }}/telemetry</code></div><div class="topic-row"><span>控制主题</span><code>openiot/{{ selected.code }}/command</code></div></section>
            <section class="detail-section"><div class="subsection-heading"><div><h3>命令记录</h3><p>{{ confirmationLabel(selected.confirmation) }}</p></div></div><div v-if="detailCommands.length" class="detail-record-list"><div v-for="command in detailCommands" :key="command.id" class="detail-record"><span class="record-dot" :class="command.status"></span><span><strong>{{ command.target }}</strong><small>{{ command.message }}</small></span><time>{{ timeAgo(command.createdAt) }}</time></div></div><div v-else class="detail-empty">暂无命令记录</div></section>
            <section class="detail-section"><div class="subsection-heading"><div><h3>设备事件</h3><p>上线、数据与通信记录</p></div></div><div v-if="detailEvents.length" class="event-list"><div v-for="event in detailEvents" :key="event.id" class="event-row"><span class="event-dot" :class="event.type"></span><span>{{ event.message }}</span><time>{{ timeAgo(event.timestamp) }}</time></div></div><div v-else class="detail-empty">暂无新增事件</div></section>
            <section class="detail-section device-admin-section"><div><div><h3>设备状态</h3><p>{{ selected.enabled ? '当前已启用' : '当前已停用' }}</p></div><label class="switch-control"><input type="checkbox" :checked="selected.enabled" @change="emit('enabled', selected.id, ($event.target as HTMLInputElement).checked)"><span></span><b>{{ selected.enabled ? '启用' : '停用' }}</b></label></div></section>
          </div>
        </section>
      </div>
    </Teleport>
  </div>
</template>
