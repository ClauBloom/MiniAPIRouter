<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import RuleSimulator from './RuleSimulator.vue'
import ResponsiveCollection from '@/shared/ui/ResponsiveCollection.vue'
import { createRoutingApi, type IntentItem, type SimulationInput } from './routing-api'
import { useBrowserHttp } from '@/shared/api/browser-http'
const { t } = useI18n()
const api = createRoutingApi(useBrowserHttp())
const intents = ref<IntentItem[]>([])
const models = ref<Array<{ id: number; display_name: string; upstream_name: string }>>([])
onMounted(async () => { intents.value = (await api.listIntents()).list; models.value = await api.listModels() })
async function simulate(input: SimulationInput) { return api.simulate(input) }
</script>
<template>
  <section>
    <header><p>{{ t('navigation.routing') }}</p><h1>{{ t('navigation.rules') }}</h1></header>
    <RuleSimulator :simulate="simulate" />
    <h2>{{ t('navigation.intents') }}</h2>
    <ResponsiveCollection>
      <template #table>
        <table>
          <thead><tr><th>{{ t('routing.intent') }}</th><th>{{ t('routing.model') }}</th></tr></thead><tbody>
            <tr
              v-for="intent in intents"
              :key="intent.id"
            >
              <td>{{ intent.intent_name || intent.label }}</td><td>{{ Object.keys(intent.model_weights || {}).join(', ') }}</td>
            </tr>
          </tbody>
        </table>
      </template>
      <template #cards>
        <article
          v-for="intent in intents"
          :key="intent.id"
        >
          <h3>{{ intent.intent_name || intent.label }}</h3><p>{{ Object.entries(intent.model_weights || {}).map(([m,w]) => `${m}×${w}`).join(' · ') }}</p>
        </article>
      </template>
    </ResponsiveCollection>
    <h2>{{ t('navigation.upstreams') }}</h2>
    <p
      v-for="model in models"
      :key="model.id"
    >
      {{ model.display_name }} <span>→ {{ model.upstream_name }}</span>
    </p>
  </section>
</template>
<style scoped>header p{color:var(--route);font-weight:700}header h1{font-size:32px}h2{font-size:20px;margin:32px 0 12px}table{width:100%;background:var(--surface);border-collapse:collapse}th,td{padding:14px;text-align:left;border-bottom:1px solid var(--border)}article{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:16px;margin-bottom:12px}article p{color:var(--muted)}p span{color:var(--muted)}</style>
