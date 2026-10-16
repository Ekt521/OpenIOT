import type { CommandStatus, ConfirmationCapability, Device } from './types'

export function canControl(device: Device): boolean {
  return device.enabled && device.online
}

// 命令执行、告警判定等业务规则由后端负责，前端只保留展示相关的判断。
export function commandStatusLabel(status: CommandStatus): string {
  const labels: Record<CommandStatus, string> = {
    pending: '等待确认', confirmed: '已确认', rejected: '设备拒绝', unknown: '结果未知', unreachable: '不可达', sent_unconfirmed: '已发送，未确认',
  }
  return labels[status]
}

export function confirmationLabel(capability: ConfirmationCapability): string {
  return capability === 'state' ? '设备状态可确认' : '仅网关发送确认'
}
