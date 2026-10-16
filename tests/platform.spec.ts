import { describe, expect, it } from 'vitest'
import { canControl, commandStatusLabel, confirmationLabel } from '../src/domain/platform'
import type { Device } from '../src/domain/types'

// 命令执行与告警判定由后端负责，前端只保留展示相关的判断。
const online: Device = { enabled: true, online: true } as Device

describe('device display helpers', () => {
  it('only allows control when the device is enabled and online', () => {
    expect(canControl({ ...online, online: false })).toBe(false)
    expect(canControl({ ...online, enabled: false })).toBe(false)
    expect(canControl(online)).toBe(true)
  })

  it('labels every command status the backend can return', () => {
    expect(commandStatusLabel('pending')).toBe('等待确认')
    expect(commandStatusLabel('confirmed')).toBe('已确认')
    expect(commandStatusLabel('rejected')).toBe('设备拒绝')
    expect(commandStatusLabel('unknown')).toBe('结果未知')
    expect(commandStatusLabel('unreachable')).toBe('不可达')
    expect(commandStatusLabel('sent_unconfirmed')).toBe('已发送，未确认')
  })

  it('describes whether a device can confirm its real state', () => {
    expect(confirmationLabel('state')).toContain('可确认')
    expect(confirmationLabel('gateway')).toContain('仅网关发送')
  })
})
