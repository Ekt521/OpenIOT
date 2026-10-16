<script setup lang="ts">
import { ref } from 'vue'
import { Activity, ArrowRight, Eye, EyeOff, LockKeyhole, Radio, ShieldCheck } from 'lucide-vue-next'

const emit = defineEmits<{ login: [email: string, password: string] }>()
const email = ref('lin.yuan@example.com')
const password = ref('openiot')
const remember = ref(true)
const showPassword = ref(false)
const error = ref('')
const busy = ref(false)

function submit() {
  error.value = ''
  if (!email.value.trim() || !email.value.includes('@')) { error.value = '请输入有效的邮箱地址'; return }
  if (password.value.length < 4) { error.value = '密码至少需要 4 位'; return }
  busy.value = true
  window.setTimeout(() => {
    busy.value = false
    emit('login', email.value, password.value)
  }, 250)
}
</script>

<template>
  <main class="login-page">
    <section class="login-showcase"><div class="showcase-top"><span class="brand-mark large"><Activity :size="21" stroke-width="2.5" /></span><strong>OpenIOT</strong></div><div class="showcase-copy"><div class="eyebrow light"><span class="live-dot"></span> 家庭设备控制台</div><h1>让每一台设备，<br /><em>清晰地工作。</em></h1><p>在一个安静、可靠的空间里，查看设备状态、控制家庭环境，并及时处理异常。</p></div><div class="showcase-signal"><div class="signal-line"><span class="signal-node active"></span><span></span><span class="signal-node active"></span><span></span><span class="signal-node"></span></div><div><strong>本地优先</strong><span>你的数据留在家庭网络内</span></div><div><strong>状态可见</strong><span>每一次操作都有确认结果</span></div></div></section><section class="login-form-panel"><div class="login-form-wrap"><div class="mobile-brand"><span class="brand-mark"><Activity :size="19" /></span><strong>OpenIOT</strong></div><div class="login-heading"><span class="form-overline">欢迎回来</span><h2>登录控制台</h2><p>登录后管理你家的设备与数据。</p></div><form @submit.prevent="submit"><label class="login-field"><span>邮箱地址</span><div class="input-with-icon"><Radio :size="17"/><input v-model="email" type="email" autocomplete="email" placeholder="name@example.com" /></div></label><label class="login-field"><span>密码</span><div class="input-with-icon"><LockKeyhole :size="17"/><input v-model="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入密码" /><button type="button" class="field-icon-button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="16"/><Eye v-else :size="16"/></button></div></label><div class="form-options"><label class="check-label"><input v-model="remember" type="checkbox"/><span>记住我</span></label><button type="button" class="forgot-button">忘记密码？</button></div><p v-if="error" class="form-error" role="alert">{{ error }}</p><button class="login-submit" type="submit" :disabled="busy"><span>{{ busy ? '正在验证…' : '进入控制台' }}</span><ArrowRight :size="17" /></button></form><div class="login-security"><ShieldCheck :size="15"/><span>家庭局域网访问 · 已启用基础身份保护</span></div><p class="login-demo-hint">演示账号可直接使用预填信息登录</p></div><footer class="login-footer"><span>OpenIOT MVP · v0.1.0</span><span>本地部署</span></footer></section>
  </main>
</template>
