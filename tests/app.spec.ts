import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import App from '../src/App.vue'
import { platformRepository } from '../src/services/repository'

function mountApp() {
  return mount(App, { attachTo: document.body })
}

describe('application shell', () => {
  beforeEach(() => {
    platformRepository.reset()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.runOnlyPendingTimers()
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('logs in through the real entry screen and opens the dashboard', async () => {
    const wrapper = mountApp()

    expect(wrapper.text()).toContain('登录控制台')
    await wrapper.get('form').trigger('submit')
    vi.advanceTimersByTime(250)
    await nextTick()

    expect(wrapper.text()).toContain('早上好，林远')
    expect(wrapper.find('.sidebar').exists()).toBe(true)
  })

  it('navigates to the device page from the sidebar', async () => {
    platformRepository.update((current) => ({ ...current, user: { id: 'test-user', name: '林远', email: 'test@example.com', role: '管理员' } }))
    const wrapper = mountApp()

    await wrapper.get('.nav-item:nth-of-type(2)').trigger('click')
    await nextTick()

    expect(wrapper.text()).toContain('设备')
    expect(wrapper.find('.device-table-surface').exists()).toBe(true)
  })

  it('renders every primary page without template errors', async () => {
    platformRepository.update((current) => ({ ...current, user: { id: 'test-user', name: '林远', email: 'test@example.com', role: '管理员' } }))
    const wrapper = mountApp()

    const expectedPages = [
      { label: '总览', selector: '.dashboard-grid' },
      { label: '设备', selector: '.device-table-surface' },
      { label: '告警中心', selector: '.alert-workspace' },
      { label: '产品与属性', selector: '.product-detail' },
      { label: '接入与设置', selector: '.settings-content' },
    ]

    for (const { label, selector } of expectedPages) {
      const target = wrapper.findAll('.nav-item').find((item) => item.text().includes(label))
      expect(target, `nav item ${label} should exist`).toBeTruthy()
      await target!.trigger('click')
      await nextTick()
      expect(wrapper.find(selector).exists(), `page ${label} should render ${selector}`).toBe(true)
    }
  })
})
