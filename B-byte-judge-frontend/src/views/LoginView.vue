<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Eye, EyeOff, LoaderCircle, LockKeyhole, UserRound } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { clearDraftCaches } from '@/lib/draft-cache'
import BrandPanel from '@/components/BrandPanel.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const submitting = ref(false)
const remembering = ref(false)
const showPassword = ref(false)
const serverError = ref('')
const form = reactive({ account: '', password: '' })
const errors = reactive({ account: '', password: '' })

onMounted(async () => {
  try {
    form.account = localStorage.getItem('bj-admin-account') ?? ''
    remembering.value = Boolean(form.account)
  } catch {
    form.account = ''
  }
  if (!auth.ready) await auth.restore()
  if (auth.identity && route.query.relogin !== '1') await router.replace('/problems')
})

function validate(field: 'account' | 'password') {
  if (field === 'account') {
    errors.account = !form.account
      ? '请输入管理员账号'
      : !/^[A-Za-z0-9_]{4,32}$/.test(form.account)
        ? '账号为 4–32 位字母、数字或下划线'
        : ''
  } else {
    errors.password = !form.password
      ? '请输入登录密码'
      : form.password.length < 8 || form.password.length > 64
        ? '密码长度为 8–64 个字符'
        : ''
  }
}

function clearFieldError(field: 'account' | 'password') {
  serverError.value = ''
  errors[field] = ''
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 40101) return '账号或密码错误，请核对后重试。'
    if (error.code === 40302) return '此账号当前不可登录，请联系平台维护人员。'
    if (error.code === 40900) return '当前已有登录会话，请先退出后再切换账号。'
    if (error.code === 42900) {
      const seconds = (error.data as { retryAfterSeconds?: number } | null)?.retryAfterSeconds
      return seconds
        ? `登录尝试过于频繁，请 ${seconds} 秒后重试。`
        : '登录尝试过于频繁，请稍后重试。'
    }
    if (error.code === 50300) return '登录服务暂时不可用，请稍后重试。'
  }
  return error instanceof Error ? error.message : '登录失败，请稍后重试。'
}

async function submit() {
  validate('account')
  validate('password')
  if (errors.account || errors.password || submitting.value) return
  serverError.value = ''
  submitting.value = true
  try {
    const previous = auth.identity?.userId
    const identity = await auth.login(form.account.trim(), form.password)
    if (previous && previous !== identity.userId) clearDraftCaches(previous)
    try {
      if (remembering.value) localStorage.setItem('bj-admin-account', form.account.trim())
      else localStorage.removeItem('bj-admin-account')
    } catch {
      remembering.value = false
    }
    toast.success(route.query.relogin === '1' ? '登录成功，返回编辑页确认恢复' : '登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/problems'
    await router.replace(
      redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/problems',
    )
  } catch (error) {
    serverError.value = errorMessage(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <header class="login-header page-enter">
      <div class="brand-logo">
        <span class="brand-symbol" aria-hidden="true">{ }</span>Byte Judge
      </div>
      <span class="admin-label">ADMIN</span>
    </header>
    <main class="login-main">
      <div class="brand-side page-enter"><BrandPanel /></div>
      <section class="form-side page-enter" aria-labelledby="login-title">
        <span class="corner-accent" aria-hidden="true"></span>
        <div class="login-box">
          <h2 id="login-title">管理员登录</h2>
          <p class="subtitle">使用授权账号进入控制台</p>
          <Alert v-if="serverError" variant="destructive" class="mb-5" role="alert"
            ><AlertDescription>{{ serverError }}</AlertDescription></Alert
          >
          <form novalidate @submit.prevent="submit">
            <div class="login-field">
              <Label class="field-label" for="account">账号</Label>
              <div class="input-with-icon">
                <UserRound aria-hidden="true" />
                <Input
                  id="account"
                  v-model="form.account"
                  placeholder="请输入管理员账号"
                  autocomplete="username"
                  autofocus
                  :aria-invalid="Boolean(errors.account)"
                  :aria-describedby="errors.account ? 'account-error' : undefined"
                  @blur="validate('account')"
                  @input="clearFieldError('account')"
                />
              </div>
              <p v-if="errors.account" id="account-error" class="field-error" role="alert">
                {{ errors.account }}
              </p>
            </div>
            <div class="login-field">
              <Label class="field-label" for="password">密码</Label>
              <div class="input-with-icon password-field">
                <LockKeyhole aria-hidden="true" />
                <Input
                  id="password"
                  v-model="form.password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="请输入登录密码"
                  autocomplete="current-password"
                  :aria-invalid="Boolean(errors.password)"
                  :aria-describedby="errors.password ? 'password-error' : undefined"
                  @blur="validate('password')"
                  @input="clearFieldError('password')"
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  class="password-toggle"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  :aria-pressed="showPassword"
                  @click="showPassword = !showPassword"
                  ><EyeOff v-if="showPassword" /><Eye v-else
                /></Button>
              </div>
              <p v-if="errors.password" id="password-error" class="field-error" role="alert">
                {{ errors.password }}
              </p>
            </div>
            <div class="options">
              <Label for="remember" class="remember"
                ><Checkbox id="remember" v-model="remembering" />记住账号</Label
              >
              <Popover
                ><PopoverTrigger as-child
                  ><Button type="button" variant="link" class="help-button"
                    >遇到问题？</Button
                  ></PopoverTrigger
                ><PopoverContent class="text-sm leading-7" align="end"
                  >请联系平台维护人员确认管理员权限或处理账号状态。此处仅记住账号名，登录状态由服务端会话管理。</PopoverContent
                ></Popover
              >
            </div>
            <Button
              class="submit primary-action"
              type="submit"
              :disabled="submitting"
              :aria-busy="submitting"
              ><LoaderCircle v-if="submitting" class="spin" />{{
                submitting ? '正在验证身份…' : '进入控制台'
              }}<ArrowRight v-if="!submitting"
            /></Button>
          </form>
          <p class="security"><LockKeyhole :size="14" />仅限授权管理员访问</p>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100svh;
  background: #fbfaf7;
  overflow: hidden;
}
.login-header {
  display: flex;
  align-items: center;
  gap: 22px;
  width: 100%;
  max-width: 1480px;
  margin: 0 auto;
  padding: 30px 4.5%;
}
.admin-label {
  padding-left: 22px;
  border-left: 1px solid #deddd6;
  color: #5b6471;
  font-size: 12px;
  letter-spacing: 1px;
}
.login-main {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.95fr);
  align-items: center;
  gap: clamp(36px, 6vw, 100px);
  flex: 1;
  width: 100%;
  max-width: 1370px;
  margin: 0 auto;
  padding: 0 4.5% 16px;
}
.brand-side {
  align-self: center;
  animation-delay: 80ms;
}
.form-side {
  position: relative;
  width: 100%;
  max-width: 450px;
  justify-self: end;
  animation-delay: 150ms;
}
.corner-accent {
  position: absolute;
  z-index: 0;
  top: -16px;
  right: -16px;
  width: 78px;
  height: 78px;
  border-radius: 18px;
  background: var(--lime);
  transform: rotate(2deg);
}
.login-box {
  position: relative;
  z-index: 1;
  padding: 40px 36px 28px;
  border: 1px solid #dcd9d0;
  border-radius: 26px;
  background: #fff;
  box-shadow:
    0 22px 65px -45px #363a2428,
    inset 0 1px 0 #fff;
}
h2 {
  margin: 0 0 8px;
  font-size: 30px;
  font-weight: 750;
  letter-spacing: -1px;
}
.subtitle {
  margin: 0 0 30px;
  color: #68717e;
  font-size: 14px;
}
.login-field {
  margin: 0 0 24px;
}
.input-with-icon {
  position: relative;
}
.input-with-icon > svg {
  position: absolute;
  z-index: 1;
  top: 50%;
  left: 14px;
  width: 18px;
  height: 18px;
  color: #6a717d;
  transform: translateY(-50%);
  pointer-events: none;
}
.input-with-icon :deep(input) {
  width: 100%;
  height: 46px;
  padding-left: 43px;
  border-radius: 10px;
  background: #fff;
  font-size: 13px;
  box-shadow: inset 0 1px 2px #23291b03;
}
.input-with-icon :deep(input:focus-visible) {
  border-color: var(--primary);
  outline: none;
  box-shadow: 0 0 0 3px #1f4bff12;
  --tw-ring-shadow: 0 0 #0000;
}
.password-field :deep(input) {
  padding-right: 45px;
}
.password-toggle {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 40px;
  height: 40px;
  color: #6a717d;
}
.options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -3px 0 25px;
}
.remember {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12px;
  font-weight: 400;
  cursor: pointer;
}
.help-button {
  height: 38px;
  padding: 0;
  font-size: 12px;
}
.submit {
  width: 100%;
  height: 48px;
  gap: 12px;
  border-radius: 11px;
  font-size: 14px;
}
.security {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  margin: 27px 0 0;
  padding-top: 21px;
  border-top: 1px solid var(--border);
  color: #7b818a;
  font-size: 11px;
}
@media (min-width: 1500px) {
  .login-main {
    padding-bottom: 50px;
  }
}
@media (max-width: 1000px) {
  .login-main {
    gap: 35px;
    padding-inline: 35px;
  }
  .login-box {
    padding: 32px 25px 24px;
  }
  h2 {
    font-size: 27px;
  }
}
@media (max-width: 800px) {
  .login-header {
    padding: 25px 25px;
  }
  .login-main {
    grid-template-columns: 1fr;
    gap: 30px;
    padding: 10px 25px 30px;
    max-width: 550px;
  }
  .form-side {
    justify-self: stretch;
    max-width: none;
  }
  .login-box {
    border-radius: 22px;
  }
  .brand-side {
    margin-bottom: 5px;
  }
}
</style>
