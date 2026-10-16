<script setup lang="ts">
import { computed, ref } from 'vue'
import { AlertTriangle, Bell, Check, CheckCircle2, ChevronDown, ChevronRight, CircleHelp, Clock3, Filter, MessageSquareText, Plus, Search, Settings2, ShieldAlert, SlidersHorizontal, X } from 'lucide-vue-next'
import type { AlarmLevel, AlarmRecord, AlarmRule, PlatformState } from '../domain/types'

const props = defineProps<{ state: PlatformState }>()
const emit = defineEmits<{ acknowledge: [alarmId: string, note: string]; close: [alarmId: string, note: string]; toggleRule: [ruleId: string]; saveRule: [rule: AlarmRule]; createDemoAlarm: [ruleId: string] }>()
const tab = ref<'alarms' | 'rules'>('alarms')
const statusFilter = ref('all')
const levelFilter = ref('all')
const deviceFilter = ref('all')
const query = ref('')
const selectedAlarm = ref<AlarmRecord | null>(null)
const note = ref('')
const showRuleForm = ref(false)
const editingRule = ref<AlarmRule | null>(null)
const ruleDraft = ref<AlarmRule>(emptyRule())

const visibleAlarms = computed(() => props.state.alarms.filter((alarm) => {
  const haystack = `${alarm.ruleName} ${alarm.deviceName} ${alarm.message}`.toLowerCase()
  return (!query.value || haystack.includes(query.value.toLowerCase()))
    && (statusFilter.value === 'all' || alarm.status === statusFilter.value)
    && (levelFilter.value === 'all' || alarm.level === levelFilter.value)
    && (deviceFilter.value === 'all' || alarm.deviceId === deviceFilter.value)
}))
const openCount = computed(() => props.state.alarms.filter((alarm) => alarm.status === 'active').length)
const acknowledgedCount = computed(() => props.state.alarms.filter((alarm) => alarm.status === 'acknowledged').length)
const criticalCount = computed(() => props.state.alarms.filter((alarm) => alarm.level === 'critical' && alarm.status !== 'closed').length)

function emptyRule(): AlarmRule {
  return { id: '', name: '', deviceId: 'all', property: 'temperature', operator: 'gt', threshold: 30, level: 'warning', enabled: true }
}
function timeAgo(value: string) {
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60_000))
  return minutes < 1 ? '刚刚' : minutes < 60 ? `${minutes} 分钟前` : minutes < 24 * 60 ? `${Math.floor(minutes / 60)} 小时前` : `${Math.floor(minutes / (24 * 60))} 天前`
}
function levelLabel(level: AlarmLevel) { return level === 'critical' ? '紧急' : level === 'warning' ? '警告' : '提示' }
function statusLabel(status: AlarmRecord['status']) { return status === 'active' ? '待处理' : status === 'acknowledged' ? '已确认' : '已关闭' }
function openDetail(alarm: AlarmRecord) { selectedAlarm.value = alarm; note.value = alarm.note }
function runAction(action: 'acknowledge' | 'close') {
  if (!selectedAlarm.value) return
  const alarmId = selectedAlarm.value.id
  if (action === 'acknowledge') emit('acknowledge', alarmId, note.value)
  else emit('close', alarmId, note.value)
  selectedAlarm.value = null
  note.value = ''
}
function editRule(rule: AlarmRule) { editingRule.value = rule; ruleDraft.value = { ...rule }; showRuleForm.value = true }
function newRule() { editingRule.value = null; ruleDraft.value = emptyRule(); showRuleForm.value = true }
function saveRule() {
  if (!ruleDraft.value.name.trim()) return
  emit('saveRule', { ...ruleDraft.value, id: ruleDraft.value.id || crypto.randomUUID() })
  showRuleForm.value = false
}
function rulePropertyOptions(rule: AlarmRule) {
  if (rule.operator === 'offline') return '设备在线状态'
  return rule.property === 'humidity' ? '相对湿度' : rule.property === 'energy' ? '累计电量' : '温度'
}
defineExpose({ openDetail })
</script>

<template>
  <div class="page-stack">
    <section class="page-heading">
      <div><div class="breadcrumb">平台 <ChevronRight :size="13" /> 运维管理</div><h1>告警中心</h1><p>集中处理设备离线、属性超限和未确认状态。</p></div>
      <div class="heading-actions"><button class="secondary-button" @click="tab = 'rules'; newRule()"><Plus :size="16" /> 新建告警规则</button></div>
    </section>
    <section class="alert-overview-grid">
      <button class="alert-overview-card red" :class="{ selected: tab === 'alarms' && statusFilter === 'active' }" @click="tab = 'alarms'; statusFilter = 'active'"><span class="overview-icon"><ShieldAlert :size="18" /></span><span>待处理</span><strong>{{ openCount }}</strong><small>需要人工处理</small></button>
      <button class="alert-overview-card amber" :class="{ selected: tab === 'alarms' && statusFilter === 'acknowledged' }" @click="tab = 'alarms'; statusFilter = 'acknowledged'"><span class="overview-icon"><CircleHelp :size="18" /></span><span>已确认</span><strong>{{ acknowledgedCount }}</strong><small>等待问题关闭</small></button>
      <button class="alert-overview-card slate" :class="{ selected: tab === 'alarms' && levelFilter === 'critical' }" @click="tab = 'alarms'; levelFilter = levelFilter === 'critical' ? 'all' : 'critical'"><span class="overview-icon"><AlertTriangle :size="18" /></span><span>紧急告警</span><strong>{{ criticalCount }}</strong><small>在线设备风险</small></button>
    </section>
    <section class="surface alert-workspace">
      <header class="workspace-tabs"><div class="tab-list" role="tablist"><button :class="{ active: tab === 'alarms' }" role="tab" :aria-selected="tab === 'alarms'" @click="tab = 'alarms'">告警记录 <span>{{ props.state.alarms.length }}</span></button><button :class="{ active: tab === 'rules' }" role="tab" :aria-selected="tab === 'rules'" @click="tab = 'rules'">告警规则 <span>{{ props.state.rules.length }}</span></button></div><span class="workspace-status"><i></i>规则实时生效</span></header>
      <template v-if="tab === 'alarms'">
        <div class="workspace-toolbar"><label class="search-control"><Search :size="16"/><input v-model="query" placeholder="搜索告警、设备或规则" aria-label="搜索告警"/><button v-if="query" class="clear-search" aria-label="清除搜索" @click="query = ''"><X :size="14"/></button></label><select v-model="statusFilter" aria-label="告警状态"><option value="all">所有状态</option><option value="active">待处理</option><option value="acknowledged">已确认</option><option value="closed">已关闭</option></select><select v-model="levelFilter" aria-label="告警等级"><option value="all">所有等级</option><option value="critical">紧急</option><option value="warning">警告</option><option value="info">提示</option></select><select v-model="deviceFilter" aria-label="告警设备"><option value="all">所有设备</option><option v-for="device in state.devices" :key="device.id" :value="device.id">{{ device.name }}</option></select><span class="toolbar-count"><Filter :size="14"/> {{ visibleAlarms.length }} 条记录</span></div>
        <div class="alarm-list"><button v-for="alarm in visibleAlarms" :key="alarm.id" class="alarm-list-row" @click="openDetail(alarm)"><span class="alarm-level-icon" :class="alarm.level"><ShieldAlert v-if="alarm.level === 'critical'" :size="17"/><AlertTriangle v-else :size="17"/></span><span class="alarm-main"><span class="alarm-name-line"><strong>{{ alarm.ruleName }}</strong><span class="level-tag" :class="alarm.level">{{ levelLabel(alarm.level) }}</span></span><small>{{ alarm.deviceName }} · {{ alarm.message }}</small></span><span class="alarm-state-column"><span class="alarm-status" :class="alarm.status"><i></i>{{ statusLabel(alarm.status) }}</span><small>{{ timeAgo(alarm.createdAt) }}</small></span><ChevronRight :size="16" class="row-chevron" /></button><div v-if="!visibleAlarms.length" class="empty-state"><CheckCircle2 :size="26"/><strong>没有符合条件的告警</strong><span>试试调整筛选条件，或等待新的设备事件。</span></div></div>
      </template>
      <template v-else>
        <div class="rule-toolbar"><div><strong>自动告警规则</strong><span>规则会根据设备上报数据生成站内告警记录。</span></div><button class="secondary-button" @click="newRule()"><Plus :size="16" /> 新建规则</button></div>
        <div class="rules-list"><div v-for="rule in state.rules" :key="rule.id" class="rule-row"><span class="rule-icon" :class="rule.level"><SlidersHorizontal :size="17" /></span><span class="rule-main"><strong>{{ rule.name }}</strong><small>{{ rulePropertyOptions(rule) }} · {{ rule.operator === 'offline' ? '设备离线' : rule.operator === 'gt' ? `大于 ${rule.threshold}` : `小于 ${rule.threshold}` }}</small></span><span class="rule-device">{{ rule.deviceId === 'all' ? '所有设备' : state.devices.find((device) => device.id === rule.deviceId)?.name }}</span><label class="switch-control compact"><input type="checkbox" :checked="rule.enabled" @change="emit('toggleRule', rule.id)"><span></span><b>{{ rule.enabled ? '启用' : '停用' }}</b></label><button class="row-action-button" @click="editRule(rule)">编辑</button><button class="icon-button" aria-label="生成测试告警" data-tooltip="生成测试告警" @click="emit('createDemoAlarm', rule.id)"><Bell :size="15"/></button></div></div>
      </template>
    </section>

    <Teleport to="body">
      <div v-if="selectedAlarm" class="modal-backdrop" @click.self="selectedAlarm = null">
        <section class="modal-card alarm-detail-modal" role="dialog" aria-modal="true" aria-label="告警详情"><header class="modal-header"><div><div class="modal-kicker">告警详情 · {{ timeAgo(selectedAlarm.createdAt) }}</div><h2>{{ selectedAlarm.ruleName }}</h2></div><button class="icon-button" aria-label="关闭" @click="selectedAlarm = null"><X :size="18"/></button></header><div class="alarm-detail-summary"><span class="alarm-level-icon large" :class="selectedAlarm.level"><ShieldAlert :size="21"/></span><div><strong>{{ selectedAlarm.message }}</strong><span>{{ selectedAlarm.deviceName }} · 发生于 {{ new Date(selectedAlarm.createdAt).toLocaleString('zh-CN') }}</span></div><span class="alarm-status" :class="selectedAlarm.status"><i></i>{{ statusLabel(selectedAlarm.status) }}</span></div><div class="detail-facts"><div><span>规则等级</span><strong>{{ levelLabel(selectedAlarm.level) }}</strong></div><div><span>设备</span><strong>{{ selectedAlarm.deviceName }}</strong></div><div><span>触发时间</span><strong>{{ timeAgo(selectedAlarm.createdAt) }}</strong></div></div><label class="form-field"><span>处理备注</span><textarea v-model="note" rows="3" placeholder="记录检查、处理或复核结果"></textarea></label><div v-if="selectedAlarm.note" class="existing-note"><MessageSquareText :size="15"/><span>历史备注：{{ selectedAlarm.note }}</span></div><footer class="modal-footer"><button class="secondary-button" @click="selectedAlarm = null">稍后处理</button><button v-if="selectedAlarm.status === 'active'" class="primary-button" @click="runAction('acknowledge')"><Check :size="16"/> 确认告警</button><button v-if="selectedAlarm.status !== 'closed'" class="primary-button dark" @click="runAction('close')"><CheckCircle2 :size="16"/> 关闭告警</button></footer></section>
      </div>
      <div v-if="showRuleForm" class="modal-backdrop" @click.self="showRuleForm = false"><section class="modal-card form-modal" role="dialog" aria-modal="true" aria-label="告警规则"><header class="modal-header"><div><div class="modal-kicker">规则配置</div><h2>{{ editingRule ? '编辑告警规则' : '新建告警规则' }}</h2></div><button class="icon-button" aria-label="关闭" @click="showRuleForm = false"><X :size="18"/></button></header><div class="form-grid"><label class="form-field full"><span>规则名称</span><input v-model="ruleDraft.name" class="form-control" placeholder="例如：客厅温度偏高"/></label><label class="form-field"><span>监测设备</span><select v-model="ruleDraft.deviceId" class="form-control"><option value="all">所有设备</option><option v-for="device in state.devices" :key="device.id" :value="device.id">{{ device.name }}</option></select></label><label class="form-field"><span>属性</span><select v-model="ruleDraft.property" class="form-control"><option value="temperature">温度</option><option value="humidity">相对湿度</option><option value="energy">累计电量</option><option value="online">在线状态</option></select></label><label class="form-field"><span>比较条件</span><select v-model="ruleDraft.operator" class="form-control"><option value="gt">大于阈值</option><option value="lt">小于阈值</option><option value="offline">设备离线</option></select></label><label v-if="ruleDraft.operator !== 'offline'" class="form-field"><span>阈值</span><input v-model.number="ruleDraft.threshold" type="number" class="form-control" placeholder="请输入数值"/></label><label class="form-field"><span>告警等级</span><select v-model="ruleDraft.level" class="form-control"><option value="critical">紧急</option><option value="warning">警告</option><option value="info">提示</option></select></label><label class="form-check full"><input v-model="ruleDraft.enabled" type="checkbox"/><span>保存后立即启用这条规则</span></label></div><footer class="modal-footer"><button class="secondary-button" @click="showRuleForm = false">取消</button><button class="primary-button" :disabled="!ruleDraft.name.trim()" @click="saveRule"><Check :size="16"/> 保存规则</button></footer></section></div>
    </Teleport>
  </div>
</template>
