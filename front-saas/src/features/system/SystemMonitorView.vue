<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createSystemApi, type SystemHealth } from './system-api'
import { useBrowserHttp } from '@/shared/api/browser-http'
const { t } = useI18n()
const api = createSystemApi(useBrowserHttp())
const health = ref<SystemHealth | null>(null)
const config = ref<Record<string, string>>({})
const edited = ref('')
async function load() { health.value = await api.health(); config.value = await api.config(); edited.value = config.value.log_retention_days ?? '30' }
async function save() { config.value = await api.updateConfig({ log_retention_days: edited.value }) }
onMounted(load)
</script>
<template>
  <section>
    <header><p>{{ t('navigation.observability') }}</p><h1>{{ t('navigation.monitor') }}</h1></header>
    <div
      v-if="health"
      class="health"
    >
      <p><span :class="health.status === 'UP' ? 'ok' : 'down'">{{ health.status }}</span></p>
      <p>{{ t('system.database') }}: {{ health.database }}</p>
      <p>{{ t('system.redis') }}: {{ health.redis }}</p>
    </div>
    <h2>{{ t('system.config') }}</h2>
    <label>{{ t('system.logRetention') }}<input
      v-model="edited"
      name="log_retention_days"
    ></label>
    <button @click="save">
      {{ t('tenants.save') }}
    </button>
  </section>
</template>
<style scoped>header p{color:var(--route);font-weight:700}header h1{font-size:32px}.health{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:20px;display:grid;gap:8px}.ok{color:var(--route);font-weight:700}.down{color:var(--danger);font-weight:700}h2{font-size:20px;margin:24px 0 12px}label{display:grid;gap:6px;max-width:320px}input{height:44px;padding:0 12px;border:1px solid var(--border);border-radius:8px}button{border:0;border-radius:8px;background:var(--route);color:#fff;padding:0 16px;height:44px;margin-top:14px}</style>
