<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import RouteTrace from '@/shared/ui/RouteTrace.vue'
import ResponsiveCollection from '@/shared/ui/ResponsiveCollection.vue'
import { createObservabilityApi, type LogItem, type RouteTrace as Trace } from './observability-api'
import { useBrowserHttp } from '@/shared/api/browser-http'
const { t } = useI18n()
const api = createObservabilityApi(useBrowserHttp())
const logs = ref<LogItem[]>([])
const trace = ref<Trace | null>(null)
async function load() { logs.value = (await api.listLogs()).list }
async function openTrace(id: number) { trace.value = await api.logRouteTrace(id) }
onMounted(load)
</script>
<template>
  <section>
    <header><p>{{ t('navigation.observability') }}</p><h1>{{ t('navigation.logs') }}</h1></header>
    <ResponsiveCollection>
      <template #table>
        <table>
          <thead><tr><th>{{ t('logs.trace') }}</th><th>{{ t('logs.model') }}</th><th>{{ t('logs.status') }}</th><th>{{ t('logs.tokens') }}</th><th /></tr></thead><tbody>
            <tr
              v-for="log in logs"
              :key="log.id"
            >
              <td><code>{{ log.trace_id }}</code></td><td>{{ log.model }}</td><td>{{ log.status }}</td><td>{{ log.total_tokens }}</td><td>
                <button
                  :data-action="`trace-${log.id}`"
                  @click="openTrace(log.id)"
                >
                  {{ t('logs.traceButton') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </template>
      <template #cards>
        <article
          v-for="log in logs"
          :key="log.id"
        >
          <h3>{{ log.model }}</h3><p>{{ log.trace_id }} · {{ log.status }}</p><button
            :data-action="`trace-${log.id}`"
            @click="openTrace(log.id)"
          >
            {{ t('logs.traceButton') }}
          </button>
        </article>
      </template>
    </ResponsiveCollection>
    <dialog
      v-if="trace"
      open
      role="dialog"
    >
      <h2>{{ t('logs.routeTrace') }}</h2>
      <RouteTrace :steps="trace.trace.map(step => ({ id: step.id, label: step.detail, status: step.status as 'complete'|'active'|'failed'|'skipped' }))" />
      <button @click="trace=null">
        {{ t('tenants.cancel') }}
      </button>
    </dialog>
  </section>
</template>
<style scoped>header p{color:var(--route);font-weight:700}header h1{font-size:32px}table{width:100%;background:var(--surface);border-collapse:collapse}th,td{padding:14px;text-align:left;border-bottom:1px solid var(--border)}code{font-family:'IBM Plex Mono',monospace;color:var(--muted);font-size:12px}article{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:16px;margin-bottom:12px}button{border:0;border-radius:8px;background:var(--signal);color:#fff;padding:0 14px}dialog{position:fixed;inset:50% auto auto 50%;transform:translate(-50%,-50%);border:1px solid var(--border);border-radius:14px;padding:28px;min-width:min(560px,92vw)}</style>
