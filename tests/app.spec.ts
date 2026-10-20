import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import App from '../src/App.vue'
import type { PlatformState } from '../src/domain/types'

function stateFor(user: PlatformState['user']): PlatformState {
  return {
    user,
    products: [],
    devices: [],
    commands: [],
    rules: [],
    alarms: [],
    mqtt: { host: 'localhost', port: 1883, clientId: 'openiot-platform', connected: true, lastCheck: new Date().toISOString(), lastError: null },
  }
}

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as unknown as Response
}

describe('application shell', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    localStorage.clear()
    vi.useFakeTimers()
    fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path.endsWith('/api/auth/login')) {
        return jsonResponse({ token: 'test-token', user: { id: 'user-admin', name: '测试管理员', email: 'admin@example.com', role: '管理员' } })
      }
      if (path.endsWith('/api/state')) {
        return jsonResponse(stateFor({ id: 'user-admin', name: '测试管理员', email: 'admin@example.com', role: '管理员' }))
      }
      return jsonResponse({ message: '未预期的请求' }, 404)
    })
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
  })

  it('logs in through the backend and opens the dashboard', async () => {
    const wrapper = mount(App, { attachTo: document.body })

    expect(wrapper.text()).toContain('登录控制台')
    expect((wrapper.get('input[type="email"]').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('input[type="password"]').element as HTMLInputElement).value).toBe('')

    await wrapper.get('input[type="email"]').setValue('admin@example.com')
    await wrapper.get('input[type="password"]').setValue('test-password')
    await wrapper.get('form').trigger('submit')
    vi.advanceTimersByTime(300)
    await flushPromises()
    await nextTick()

    const loginCall = fetchMock.mock.calls.find((call) => String(call[0]).endsWith('/api/auth/login'))
    expect(loginCall, '前端应调用后端登录接口').toBeTruthy()
    expect(wrapper.text()).toContain('早上好，测试管理员')
  })

  it('renders every primary page from backend state', async () => {
    localStorage.setItem('openiot-token', 'test-token')
    const wrapper = mount(App, { attachTo: document.body })
    await flushPromises()
    await nextTick()

    const expectedPages = [
      { label: '总览', selector: '.dashboard-grid' },
      { label: '设备', selector: '.device-table-surface' },
      { label: '告警中心', selector: '.alert-workspace' },
      { label: '产品与属性', selector: '.product-layout' },
      { label: '接入与设置', selector: '.settings-content' },
    ]

    for (const { label, selector } of expectedPages) {
      const target = wrapper.findAll('.nav-item').find((item) => item.text().includes(label))
      expect(target, `导航项 ${label} 应存在`).toBeTruthy()
      await target!.trigger('click')
      await nextTick()
      expect(wrapper.find(selector).exists(), `页面 ${label} 应渲染 ${selector}`).toBe(true)
    }
  })
})
