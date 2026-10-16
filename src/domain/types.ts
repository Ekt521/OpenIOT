export type PropertyType = 'number' | 'string' | 'boolean' | 'enum'
export type DeviceStatus = 'online' | 'offline'
export type ConfirmationCapability = 'state' | 'gateway'
export type CommandStatus = 'pending' | 'confirmed' | 'unknown' | 'unreachable' | 'sent_unconfirmed'
export type AlarmStatus = 'active' | 'acknowledged' | 'closed'
export type AlarmLevel = 'critical' | 'warning' | 'info'

export interface User {
  id: string
  name: string
  email: string
  role: '管理员' | '运维人员'
}

export interface PropertyDefinition {
  id: string
  name: string
  key: string
  type: PropertyType
  unit: string
  min?: number
  max?: number
  options?: string[]
}

export interface Product {
  id: string
  name: string
  category: string
  description: string
  status: 'active' | 'draft'
  protocol: 'MQTT'
  properties: PropertyDefinition[]
  createdAt: string
}

export interface TelemetryPoint {
  timestamp: string
  temperature?: number
  humidity?: number
  power?: boolean
  energy?: number
}

export interface DeviceEvent {
  id: string
  type: 'online' | 'offline' | 'telemetry' | 'command' | 'config'
  message: string
  timestamp: string
}

export interface Device {
  id: string
  productId: string
  name: string
  code: string
  location: string
  description: string
  enabled: boolean
  online: boolean
  lastSeen: string
  confirmation: ConfirmationCapability
  capabilities: string[]
  state: Record<string, string | number | boolean>
  targetState: Record<string, string | number | boolean>
  history: TelemetryPoint[]
  events: DeviceEvent[]
  credential: string
}

export interface CommandRecord {
  id: string
  deviceId: string
  deviceName: string
  target: string
  createdAt: string
  status: CommandStatus
  confirmation: ConfirmationCapability
  message: string
}

export interface AlarmRule {
  id: string
  name: string
  deviceId: string | 'all'
  property: string
  operator: 'gt' | 'lt' | 'offline'
  threshold?: number
  level: AlarmLevel
  enabled: boolean
}

export interface AlarmRecord {
  id: string
  ruleId: string
  ruleName: string
  deviceId: string
  deviceName: string
  message: string
  level: AlarmLevel
  status: AlarmStatus
  createdAt: string
  acknowledgedAt?: string
  closedAt?: string
  note: string
}

export interface PlatformState {
  user: User | null
  products: Product[]
  devices: Device[]
  commands: CommandRecord[]
  rules: AlarmRule[]
  alarms: AlarmRecord[]
  mqtt: {
    host: string
    port: number
    clientId: string
    connected: boolean
    lastCheck: string
  }
}

export interface NavigationItem {
  id: string
  label: string
  icon: string
  group: '平台' | '管理'
}
