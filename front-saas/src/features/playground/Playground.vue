<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import RouteTrace from '@/shared/ui/RouteTrace.vue'
import { renderSafeMarkdown } from './safe-markdown'
export interface StreamHandlers { onDelta(text: string): void; onDone(): void; onError(state: string): void; onMeta(meta: { ttft_ms?: number; usage?: number }): void }
export type StreamFn = (input: { prompt: string; model: string; protocol: 'openai' | 'anthropic'; apiKey: string; handlers: StreamHandlers }) => { abort(): void }
const props = defineProps<{ stream: StreamFn }>()
const { t } = useI18n()
const prompt = ref(''); const model = ref(''); const protocol = ref<'openai' | 'anthropic'>('openai'); const apiKey = ref(sessionStorage.getItem('proxy_key') ?? '')
const conversation = ref(''); const status = ref<'idle' | 'streaming' | 'done' | 'error'>('idle')
const errorState = ref<string | null>(null); const ttft = ref<number | null>(null); const usage = ref<number | null>(null)
const trace = ref<Array<{ id: string; label: string; status: 'complete' | 'active' | 'failed' | 'skipped' }>>([])
let activeAbort: { abort(): void } | null = null
function send() {
  conversation.value = ''; status.value = 'streaming'; errorState.value = null; ttft.value = null; usage.value = null
  trace.value = [{ id: 'request', label: t('playground.request'), status: 'complete' }, { id: 'stream', label: t('playground.streaming'), status: 'active' }]
  const startedAt = performance.now()
  activeAbort = props.stream({
    prompt: prompt.value, model: model.value, protocol: protocol.value, apiKey: apiKey.value,
    handlers: {
      onDelta(text) { conversation.value += text },
      onDone() { status.value = 'done'; trace.value.push({ id: 'done', label: t('playground.complete'), status: 'complete' }) },
      onError(state) { status.value = 'error'; errorState.value = state; trace.value[trace.value.length - 1] = { id: 'stream', label: t('playground.failed'), status: 'failed' } },
      onMeta(meta) { if (meta.ttft_ms) ttft.value = meta.ttft_ms; if (meta.usage) usage.value = meta.usage },
    },
  })
  if (ttft.value === null) ttft.value = Math.round(performance.now() - startedAt)
}
function cancel() { activeAbort?.abort(); status.value = 'error'; errorState.value = 'cancelled' }
</script>
<template>
  <section class="playground">
    <div class="composer">
      <label>{{ t('playground.model') }}<input
        v-model="model"
        name="model"
      ></label>
      <label>{{ t('playground.protocol') }}<select
        v-model="protocol"
        name="protocol"
      ><option value="openai">OpenAI</option><option value="anthropic">Anthropic</option></select></label>
      <label>{{ t('playground.apiKey') }}<input
        v-model="apiKey"
        name="api_key"
        type="password"
        autocomplete="off"
      ></label>
      <label>{{ t('playground.prompt') }}<textarea
        v-model="prompt"
        name="prompt"
      /></label>
      <div class="actions">
        <button
          data-action="send"
          :disabled="status === 'streaming'"
          @click="send"
        >
          {{ t('playground.send') }}
        </button>
        <button
          v-if="status === 'streaming'"
          data-action="cancel"
          @click="cancel"
        >
          {{ t('playground.cancel') }}
        </button>
      </div>
    </div>
    <RouteTrace :steps="trace" />
    <p v-if="ttft !== null">
      {{ t('playground.ttft') }}: {{ ttft }}ms
    </p>
    <p v-if="usage !== null">
      {{ t('playground.usage') }}: {{ usage }}
    </p>
    <p
      v-if="errorState"
      role="alert"
    >
      {{ t(`playground.error.${errorState}`) }}
    </p>
    <!-- eslint-disable-next-line vue/no-v-html -- sanitized by renderSafeMarkdown -->
    <div
      v-else
      role="log"
      class="output"
      v-html="renderSafeMarkdown(conversation)"
    />
  </section>
</template>
<style scoped>.playground{display:grid;gap:18px}.composer{display:grid;gap:12px;max-width:720px}.composer label{display:grid;gap:6px}.composer input,.composer select,.composer textarea{font:inherit;padding:10px 12px;border:1px solid var(--border);border-radius:8px}.composer textarea{min-height:120px}.actions{display:flex;gap:10px}.actions button{border:0;border-radius:8px;background:var(--route);color:#fff;font-weight:700;padding:0 18px;height:44px}.actions button[data-action="cancel"]{background:var(--danger)}.output{white-space:pre-wrap;font:14px/1.6 Inter,sans-serif}</style>
