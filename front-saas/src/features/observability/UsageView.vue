<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createObservabilityApi, type UsageSummary } from './observability-api'
import { useBrowserHttp } from '@/shared/api/browser-http'
const { t } = useI18n()
const api = createObservabilityApi(useBrowserHttp())
const summary = ref<UsageSummary | null>(null)
const models = ref<Array<{ model: string; cnt: number; tokens: number }>>([])
async function load() { summary.value = await api.usageSummary(); models.value = await api.usageByModel() }
onMounted(load)
</script>
<template>
  <section>
    <header><p>{{ t('navigation.observability') }}</p><h1>{{ t('navigation.usage') }}</h1></header>
    <div
      v-if="summary"
      class="metrics"
    >
      <div><b>{{ summary.total_requests }}</b><span>{{ t('usage.requests') }}</span></div>
      <div><b>{{ summary.total_tokens }}</b><span>{{ t('usage.tokens') }}</span></div>
      <div><b>{{ Math.round(summary.success_rate * 100) }}%</b><span>{{ t('usage.successRate') }}</span></div>
    </div>
    <h2>{{ t('usage.byModel') }}</h2>
    <p
      v-for="model in models"
      :key="model.model"
    >
      {{ model.model }}: {{ model.cnt }} {{ t('usage.requests') }} · {{ model.tokens }} {{ t('usage.tokens') }}
    </p>
    <p class="summary-text">
      {{ t('usage.accessibleSummary', { total: summary?.total_requests ?? 0 }) }}
    </p>
  </section>
</template>
<style scoped>header p{color:var(--route);font-weight:700}header h1{font-size:32px}.metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:14px}.metrics div{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:20px}.metrics b{display:block;font-size:32px}.metrics span{color:var(--muted)}h2{font-size:20px;margin:28px 0 12px}.summary-text{color:var(--muted);margin-top:20px}</style>
