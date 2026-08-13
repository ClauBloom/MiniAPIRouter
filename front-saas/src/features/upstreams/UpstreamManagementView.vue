<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import UpstreamForm from './UpstreamForm.vue'
import ResponsiveCollection from '@/shared/ui/ResponsiveCollection.vue'
import { createUpstreamApi, type HealthCheckResult, type Upstream, type UpstreamInput } from './upstream-api'
import { useBrowserHttp } from '@/shared/api/browser-http'
const { t } = useI18n()
const api = createUpstreamApi(useBrowserHttp())
const upstreams = ref<Upstream[]>([])
const health = ref<Record<number, HealthCheckResult>>({})
const error = ref('')
async function load() { upstreams.value = (await api.list()).list }
async function create(input: UpstreamInput) { await api.create(input); await load() }
async function check(id: number) { try { health.value[id] = await api.healthCheck(id) } catch { error.value = t('errors.unknown') } }
onMounted(load)
</script>
<template>
  <section>
    <header><p>{{ t('navigation.routing') }}</p><h1>{{ t('navigation.upstreams') }}</h1></header>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <h2>{{ t('upstreams.new') }}</h2>
    <UpstreamForm :on-submit="create" />
    <h2>{{ t('upstreams.list') }}</h2>
    <ResponsiveCollection>
      <template #table>
        <table>
          <thead><tr><th>{{ t('upstreams.name') }}</th><th>{{ t('upstreams.provider') }}</th><th>{{ t('upstreams.key') }}</th><th>{{ t('upstreams.health') }}</th><th /></tr></thead><tbody>
            <tr
              v-for="up in upstreams"
              :key="up.id"
            >
              <td>{{ up.name }}</td><td>{{ up.provider }}</td><td><code>{{ up.api_key_masked }}</code></td><td>{{ health[up.id]?.status ?? up.health_status }}</td><td>
                <button
                  :data-action="`health-${up.id}`"
                  @click="check(up.id)"
                >
                  {{ t('upstreams.test') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </template>
      <template #cards>
        <article
          v-for="up in upstreams"
          :key="up.id"
        >
          <h3>{{ up.name }}</h3><p>{{ up.provider }} · {{ up.api_key_masked }}</p><p>{{ health[up.id]?.detail ?? up.health_status }}</p><button
            :data-action="`health-${up.id}`"
            @click="check(up.id)"
          >
            {{ t('upstreams.test') }}
          </button>
        </article>
      </template>
    </ResponsiveCollection>
  </section>
</template>
<style scoped>header p{color:var(--route);font-weight:700}header h1{font-size:32px}h2{font-size:20px;margin:28px 0 12px}table{width:100%;background:var(--surface);border-collapse:collapse}th,td{padding:14px;text-align:left;border-bottom:1px solid var(--border)}code{font-family:'IBM Plex Mono',monospace;color:var(--muted)}article{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:16px;margin-bottom:12px}button{border:0;border-radius:8px;background:var(--signal);color:#fff;padding:0 14px}</style>
