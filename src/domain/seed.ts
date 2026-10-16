import type { Device, PlatformState, Product } from './types'

const isoAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString()

function makeHistory(offset: number, base: number): Device['history'] {
  return Array.from({ length: 24 }, (_, index) => {
    const minutesAgo = (23 - index) * 15 + offset
    const wave = Math.sin(index / 3) * 1.8 + Math.cos(index / 5) * 0.7
    return {
      timestamp: isoAgo(minutesAgo),
      temperature: Number((base + wave).toFixed(1)),
      humidity: Math.round(47 + Math.cos(index / 4) * 8),
      energy: Number((0.24 + index * 0.013).toFixed(2)),
    }
  })
}

const products: Product[] = [
  {
    id: 'prod-relay', name: '智能继电器', category: '开关控制', description: '支持 MQTT 状态回报的双路继电器', status: 'active', protocol: 'MQTT', createdAt: isoAgo(24 * 60),
    properties: [
      { id: 'prop-power', name: '开关状态', key: 'power', type: 'boolean', unit: '' },
      { id: 'prop-energy', name: '累计电量', key: 'energy', type: 'number', unit: 'kWh', min: 0 },
    ],
  },
  {
    id: 'prod-ir', name: '红外控制网关', category: '红外网关', description: '控制空调等红外家电，目标状态无法回读', status: 'active', protocol: 'MQTT', createdAt: isoAgo(7 * 24 * 60),
    properties: [{ id: 'prop-gateway', name: '网关连接', key: 'online', type: 'boolean', unit: '' }],
  },
  {
    id: 'prod-env', name: '室内环境传感器', category: '环境监测', description: '周期上报温度和相对湿度', status: 'active', protocol: 'MQTT', createdAt: isoAgo(3 * 24 * 60),
    properties: [
      { id: 'prop-temp', name: '温度', key: 'temperature', type: 'number', unit: '°C', min: -20, max: 80 },
      { id: 'prop-humidity', name: '相对湿度', key: 'humidity', type: 'number', unit: '%', min: 0, max: 100 },
    ],
  },
]

const devices: Device[] = [
  {
    id: 'dev-living', productId: 'prod-relay', name: '客厅主灯', code: 'OI-240018', location: '一层 · 客厅', description: '主照明双路控制，按键与平台状态同步。', enabled: true, online: true, lastSeen: isoAgo(1), confirmation: 'state', capabilities: ['开关控制', '状态回报'], state: { power: true, energy: 18.42 }, targetState: { power: true }, history: makeHistory(0, 23.6), events: [], credential: 'oi_••••••••8f2a',
  },
  {
    id: 'dev-bedroom', productId: 'prod-relay', name: '卧室床头灯', code: 'OI-240021', location: '二层 · 主卧', description: '床头双控继电器，当前设备离线。', enabled: true, online: false, lastSeen: isoAgo(14), confirmation: 'state', capabilities: ['开关控制', '状态回报'], state: { power: false, energy: 6.31 }, targetState: { power: false }, history: makeHistory(5, 22.4), events: [], credential: 'oi_••••••••14bd',
  },
  {
    id: 'dev-ir', productId: 'prod-ir', name: '客厅空调红外', code: 'OI-IR-0007', location: '一层 · 客厅', description: '通过红外网关发送空调指令，不代表空调已执行。', enabled: true, online: true, lastSeen: isoAgo(2), confirmation: 'gateway', capabilities: ['红外控制', '网关在线'], state: { online: true, temperature: 24, mode: '制冷' }, targetState: { temperature: 24, mode: '制冷', power: true }, history: makeHistory(1, 24), events: [], credential: 'oi_••••••••a091',
  },
  {
    id: 'dev-env', productId: 'prod-env', name: '主卧环境传感器', code: 'OI-ENV-0029', location: '二层 · 主卧', description: '每五分钟上报一次温湿度。', enabled: true, online: true, lastSeen: isoAgo(3), confirmation: 'state', capabilities: ['温度', '湿度'], state: { temperature: 24.8, humidity: 58 }, targetState: {}, history: makeHistory(3, 24.3), events: [], credential: 'oi_••••••••7c66',
  },
  {
    id: 'dev-entry', productId: 'prod-relay', name: '玄关照明', code: 'OI-240035', location: '一层 · 玄关', description: '入口照明回路，计划维护中。', enabled: false, online: false, lastSeen: isoAgo(180), confirmation: 'state', capabilities: ['开关控制', '状态回报'], state: { power: false, energy: 3.82 }, targetState: { power: false }, history: makeHistory(180, 21.9), events: [], credential: 'oi_••••••••6e11',
  },
]

const rules = [
  { id: 'rule-temp', name: '室温偏高', deviceId: 'all' as const, property: 'temperature', operator: 'gt' as const, threshold: 30, level: 'warning' as const, enabled: true },
  { id: 'rule-humidity', name: '湿度过低', deviceId: 'all' as const, property: 'humidity', operator: 'lt' as const, threshold: 35, level: 'info' as const, enabled: true },
  { id: 'rule-offline', name: '设备离线', deviceId: 'all' as const, property: 'online', operator: 'offline' as const, level: 'critical' as const, enabled: true },
]

export function createInitialState(): PlatformState {
  return {
    user: null,
    products,
    devices,
    commands: [
      { id: 'cmd-1', deviceId: 'dev-living', deviceName: '客厅主灯', target: '开启', createdAt: isoAgo(34), status: 'confirmed', confirmation: 'state', message: '设备已确认开启' },
      { id: 'cmd-2', deviceId: 'dev-ir', deviceName: '客厅空调红外', target: '设置 24°C 制冷', createdAt: isoAgo(92), status: 'sent_unconfirmed', confirmation: 'gateway', message: '红外网关已发送，目标设备状态未确认' },
    ],
    rules,
    alarms: [
      { id: 'alarm-1', ruleId: 'rule-offline', ruleName: '设备离线', deviceId: 'dev-bedroom', deviceName: '卧室床头灯', message: '设备离线 14 分钟', level: 'critical', status: 'active', createdAt: isoAgo(14), note: '' },
      { id: 'alarm-2', ruleId: 'rule-temp', ruleName: '室温偏高', deviceId: 'dev-env', deviceName: '主卧环境传感器', message: '温度 31.2°C 高于阈值 30°C', level: 'warning', status: 'acknowledged', createdAt: isoAgo(205), acknowledgedAt: isoAgo(198), note: '已开启空调，观察回落情况' },
      { id: 'alarm-3', ruleId: 'rule-humidity', ruleName: '湿度过低', deviceId: 'dev-env', deviceName: '主卧环境传感器', message: '湿度 32% 低于阈值 35%', level: 'info', status: 'closed', createdAt: isoAgo(28 * 60), closedAt: isoAgo(25 * 60), note: '已补充加湿' },
    ],
    mqtt: { host: '192.168.1.20', port: 1883, clientId: 'openiot-home-console', connected: true, lastCheck: isoAgo(1) },
  }
}
