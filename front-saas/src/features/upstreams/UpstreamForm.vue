<script setup lang="ts">
import { reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import type { UpstreamInput } from './upstream-api'
const props = defineProps<{ onSubmit: (input: UpstreamInput) => Promise<void> | void }>()
const { t } = useI18n()
const form = reactive({ name: '', provider: 'openai', api_key: '', base_url: '', mapping_display: '', mapping_real: '' })
async function submit() {
  const model_mapping: Record<string, string> = {}
  if (form.mapping_display.trim()) model_mapping[form.mapping_display.trim()] = form.mapping_real.trim()
  await props.onSubmit({ name: form.name, provider: form.provider, api_key: form.api_key, base_url: form.base_url, model_mapping })
  form.api_key = ''
}
</script>
<template>
  <form
    novalidate
    @submit.prevent="submit"
  >
    <label>{{ t('upstreams.name') }}<input
      v-model="form.name"
      name="name"
      required
    ></label>
    <label>{{ t('upstreams.provider') }}<select
      v-model="form.provider"
      name="provider"
    ><option value="openai">OpenAI</option><option value="anthropic">Anthropic</option></select></label>
    <label>{{ t('upstreams.apiKey') }}<input
      v-model="form.api_key"
      name="api_key"
      type="password"
      autocomplete="off"
    ></label>
    <label>{{ t('upstreams.baseUrl') }}<input
      v-model="form.base_url"
      name="base_url"
      type="url"
    ></label>
    <fieldset>
      <legend>{{ t('upstreams.modelMapping') }}</legend>
      <label>{{ t('upstreams.displayName') }}<input
        v-model="form.mapping_display"
        name="mapping_display"
      ></label>
      <label>{{ t('upstreams.realName') }}<input
        v-model="form.mapping_real"
        name="mapping_real"
      ></label>
    </fieldset>
    <button type="submit">
      {{ t('upstreams.save') }}
    </button>
  </form>
</template>
<style scoped>form{display:grid;gap:14px;max-width:560px}label{display:grid;gap:6px}input,select{height:44px;padding:0 12px;border:1px solid var(--border);border-radius:8px;font:inherit}fieldset{border:1px solid var(--border);border-radius:8px;display:grid;gap:12px}button{border:0;border-radius:8px;background:var(--route);color:#fff;font-weight:700;padding:0 18px;height:44px}</style>
