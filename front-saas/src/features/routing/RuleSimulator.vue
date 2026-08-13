<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import RouteTrace from '@/shared/ui/RouteTrace.vue'
import type { SimulationInput, SimulationResult } from './routing-api'
const props = defineProps<{ simulate: (input: SimulationInput) => Promise<SimulationResult> }>()
const { t } = useI18n()
const model = ref('*'); const intent = ref(''); const complexity = ref<number | null>(null)
const result = ref<SimulationResult | null>(null); const error = ref('')
async function run() { error.value=''; try { result.value = await props.simulate({ model: model.value || undefined, intent: intent.value || undefined, complexity: complexity.value ?? undefined }) } catch { error.value = t('errors.unknown') } }
</script>
<template>
  <section>
    <div class="controls">
      <label>{{ t('routing.model') }}<input
        v-model="model"
        name="model"
      ></label>
      <label>{{ t('routing.intent') }}<input
        v-model="intent"
        name="intent"
      ></label>
      <label>{{ t('routing.complexity') }}<input
        v-model.number="complexity"
        name="complexity"
        type="number"
      ></label>
      <button
        data-action="run-simulation"
        @click="run"
      >
        {{ t('routing.simulate') }}
      </button>
    </div>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <template v-if="result">
      <p><strong>{{ result.selected_model ?? t('routing.noModel') }}</strong> · {{ result.matched_rule_name }}</p>
      <RouteTrace :steps="result.trace.map(step => ({ id: step.id, label: step.detail, status: step.status as 'complete'|'active'|'failed'|'skipped' }))" />
      <p v-if="result.fallback_order?.length">
        {{ t('routing.fallback') }}: {{ result.fallback_order.join(', ') }}
      </p>
    </template>
  </section>
</template>
<style scoped>.controls{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin-bottom:20px}.controls label{display:grid;gap:6px}.controls input{height:44px;padding:0 12px;border:1px solid var(--border);border-radius:8px;font:inherit}.controls button{border:0;border-radius:8px;background:var(--route);color:#fff;font-weight:700;padding:0 18px}</style>
