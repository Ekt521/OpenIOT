import { beforeEach, describe, expect, it } from 'vitest'
import { createInitialState } from '../src/domain/seed'
import { canControl, createCommand, markCommandUnknown, resolveCommand, ruleMatches, updateAlarmStatus } from '../src/domain/platform'
import type { AlarmRecord, Device } from '../src/domain/types'

const now = new Date('2026-10-13T12:00:00.000Z')
let living: Device

beforeEach(() => {
  living = structuredClone(createInitialState().devices.find((device) => device.id === 'dev-living')!)
})

describe('device command lifecycle', () => {
  it('does not send commands to offline or disabled devices', () => {
    const offline = { ...living, online: false }
    expect(canControl(offline)).toBe(false)
    expect(createCommand(offline, '开启', now)).toMatchObject({ status: 'unreachable', message: '设备当前离线，命令未下发' })
    expect(createCommand({ ...living, enabled: false }, '开启', now).status).toBe('unreachable')
  })

  it('keeps an infrared command explicitly unconfirmed', () => {
    const infrared = createInitialState().devices.find((device) => device.confirmation === 'gateway')!
    const command = createCommand(infrared, '设置 24°C 制冷', now)
    expect(command.status).toBe('sent_unconfirmed')
    expect(command.message).toContain('状态未确认')
  })

  it('only confirms an acknowledged target-state command and updates actual state', () => {
    const command = createCommand(living, '关闭', now)
    const result = resolveCommand(command, living, now)
    expect(result.command.status).toBe('confirmed')
    expect(result.device.state.power).toBe(false)
    expect(result.device.targetState.power).toBe(false)
    expect(resolveCommand({ ...command, status: 'unknown' }, living, now).device.state.power).toBe(true)
  })

  it('marks a timed-out command as unknown rather than failed', () => {
    expect(markCommandUnknown(createCommand(living, '开启', now))).toMatchObject({ status: 'unknown', message: expect.stringContaining('结果未知') })
  })
})

describe('alarm lifecycle', () => {
  it('matches numeric and offline rules only when enabled', () => {
    const rule = createInitialState().rules[0]
    expect(ruleMatches(rule, 31, true)).toBe(true)
    expect(ruleMatches(rule, 28, true)).toBe(false)
    expect(ruleMatches({ ...rule, enabled: false }, 31, true)).toBe(false)
    expect(ruleMatches(createInitialState().rules[2], undefined, false)).toBe(true)
  })

  it('records acknowledgement and closure notes without reopening closed alarms', () => {
    const alarm: AlarmRecord = { id: 'a', ruleId: 'r', ruleName: '规则', deviceId: 'd', deviceName: '设备', message: '异常', level: 'warning', status: 'active', createdAt: now.toISOString(), note: '' }
    const acknowledged = updateAlarmStatus(alarm, 'acknowledge', '已处理', now)
    expect(acknowledged).toMatchObject({ status: 'acknowledged', note: '已处理', acknowledgedAt: now.toISOString() })
    const closed = updateAlarmStatus(acknowledged, 'close', '复核正常', now)
    expect(closed).toMatchObject({ status: 'closed', note: '复核正常', closedAt: now.toISOString() })
    expect(updateAlarmStatus(closed, 'acknowledge', '重新确认', now)).toEqual(closed)
  })
})
