<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LoginInput } from '@/shared/api/contracts'
const props = defineProps<{ pending: boolean; onSubmit: (input: LoginInput) => Promise<void> | void }>()
const { t } = useI18n()
const form = reactive({ username: '', password: '', tenant_code: '' })
const errors = ref<Record<string,string>>({})
async function submit() {
  errors.value = {}
  if (!form.username.trim()) errors.value.username = t('auth.username')
  if (!form.password) errors.value.password = t('auth.password')
  if (Object.keys(errors.value).length) return
  const input: LoginInput = { username: form.username.trim(), password: form.password }
  if (form.tenant_code.trim()) input.tenant_code = form.tenant_code.trim()
  await props.onSubmit(input)
}
</script>
<template>
  <form
    novalidate
    @submit.prevent="submit"
  >
    <label for="username">{{ t('auth.username') }}</label>
    <input
      id="username"
      v-model="form.username"
      name="username"
      autocomplete="username"
      :aria-invalid="!!errors.username"
      aria-describedby="username-error"
    >
    <small
      v-if="errors.username"
      id="username-error"
      data-field-error="username"
    >{{ errors.username }}</small>
    <label for="password">{{ t('auth.password') }}</label>
    <input
      id="password"
      v-model="form.password"
      name="password"
      type="password"
      autocomplete="current-password"
      :aria-invalid="!!errors.password"
      aria-describedby="password-error"
    >
    <small
      v-if="errors.password"
      id="password-error"
      data-field-error="password"
    >{{ errors.password }}</small>
    <label for="tenant-code">{{ t('auth.tenantCode') }}</label>
    <input
      id="tenant-code"
      v-model="form.tenant_code"
      name="tenant_code"
      autocomplete="organization"
    >
    <small>{{ t('auth.tenantHint') }}</small>
    <button
      type="submit"
      :disabled="pending"
    >
      {{ t('auth.signIn') }}
    </button>
  </form>
</template>
<style scoped>
form{display:grid;gap:8px}label{font-weight:600;margin-top:8px}input{height:46px;border:1px solid var(--border);border-radius:8px;padding:0 12px;font:inherit;background:var(--surface)}small{color:var(--muted)}small[data-field-error]{color:var(--danger)}button{margin-top:18px;border:0;border-radius:8px;background:var(--route);color:white;font-weight:700;padding:0 18px}button:disabled{opacity:.55}
</style>
