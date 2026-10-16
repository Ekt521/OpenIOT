import type { AlarmRecord, AlarmRule, CommandRecord, CommandStatus, ConfirmationCapability, Device, DeviceEvent } from './types'

export function canControl(device: Device): boolean {
  return device.enabled && device.online
}

export function createCommand(device: Device, target: string, now = new Date()): CommandRecord {
  if (!device.enabled || !device.online) {
    return {
      id: crypto.randomUUID(),
      deviceId: device.id,
      deviceName: device.name,
      target,
      createdAt: now.toISOString(),
      status: 'unreachable',
      confirmation: device.confirmation,
      message: device.enabled ? '设备当前离线，命令未下发' : '设备已停用，命令未下发',
    }
  }

  if (device.confirmation === 'gateway') {
    return {
      id: crypto.randomUUID(),
      deviceId: device.id,
      deviceName: device.name,
      target,
      createdAt: now.toISOString(),
      status: 'sent_unconfirmed',
      confirmation: device.confirmation,
      message: '红外网关已发送，目标设备状态未确认',
    }
  }

  return {
    id: crypto.randomUUID(),
    deviceId: device.id,
    deviceName: device.name,
    target,
    createdAt: now.toISOString(),
    status: 'pending',
    confirmation: device.confirmation,
    message: '等待设备执行确认',
  }
}

export function resolveCommand(command: CommandRecord, device: Device, now = new Date()): { command: CommandRecord; device: Device } {
  if (command.status !== 'pending' || command.confirmation !== 'state') return { command, device }
  const targetPower = command.target === '开启'
  return {
    command: { ...command, status: 'confirmed', message: `设备已确认${command.target}` },
    device: {
      ...device,
      state: { ...device.state, power: targetPower },
      targetState: { ...device.targetState, power: targetPower },
      lastSeen: now.toISOString(),
      history: [...device.history, { timestamp: now.toISOString(), power: targetPower }].slice(-240),
      events: [{ id: crypto.randomUUID(), type: 'command', message: `设备确认${command.target}`, timestamp: now.toISOString() } satisfies DeviceEvent, ...device.events].slice(0, 30),
    },
  }
}

export function markCommandUnknown(command: CommandRecord): CommandRecord {
  if (command.status !== 'pending') return command
  return { ...command, status: 'unknown', message: '等待超时，执行结果未知，可重发目标状态命令' }
}

export function ruleMatches(rule: AlarmRule, value: string | number | boolean | undefined, online: boolean): boolean {
  if (!rule.enabled) return false
  if (rule.operator === 'offline') return !online
  if (typeof value !== 'number' || rule.threshold === undefined) return false
  return rule.operator === 'gt' ? value > rule.threshold : value < rule.threshold
}

export function makeAlarm(rule: AlarmRule, device: Device, now = new Date()): AlarmRecord {
  const detail = rule.operator === 'offline'
    ? '设备已离线'
    : `${rule.property} ${rule.operator === 'gt' ? '高于' : '低于'}阈值 ${rule.threshold}`
  return {
    id: crypto.randomUUID(),
    ruleId: rule.id,
    ruleName: rule.name,
    deviceId: device.id,
    deviceName: device.name,
    message: detail,
    level: rule.level,
    status: 'active',
    createdAt: now.toISOString(),
    note: '',
  }
}

export function updateAlarmStatus(alarm: AlarmRecord, action: 'acknowledge' | 'close', note: string, now = new Date()): AlarmRecord {
  if (action === 'acknowledge') {
    if (alarm.status !== 'active') return alarm
    return { ...alarm, status: 'acknowledged', acknowledgedAt: now.toISOString(), note: note.trim() || alarm.note }
  }
  if (alarm.status === 'closed') return alarm
  return { ...alarm, status: 'closed', closedAt: now.toISOString(), note: note.trim() || alarm.note }
}

export function commandStatusLabel(status: CommandStatus): string {
  const labels: Record<CommandStatus, string> = {
    pending: '等待确认', confirmed: '已确认', unknown: '结果未知', unreachable: '不可达', sent_unconfirmed: '已发送，未确认',
  }
  return labels[status]
}

export function confirmationLabel(capability: ConfirmationCapability): string {
  return capability === 'state' ? '设备状态可确认' : '仅网关发送确认'
}
