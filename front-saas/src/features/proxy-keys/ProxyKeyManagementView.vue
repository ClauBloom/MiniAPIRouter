<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createProxyKeyApi, type ProxyKey } from './proxy-key-api'
import { useBrowserHttp } from '@/shared/api/browser-http'
const { t } = useI18n()
const api = createProxyKeyApi(useBrowserHttp())
const keys = ref<ProxyKey[]>([])
const revealed = ref<string | null>(null)
const remembered = ref(false)
async function generate() {
  const created = await api.generate()
  if (created.api_key) {
    revealed.value = created.api_key
    if (remembered.value) sessionStorage.setItem('proxy_key', created.api_key)
  }
  await load()
}
function dismiss() { revealed.value = null }
async function load() { keys.value = await api.list() }
onMounted(load)
</script>
<template>
  <section>
    <header><p>{{ t('navigation.routing') }}</p><h1>{{ t('proxyKeys.title') }}</h1></header>
    <label><input
      v-model="remembered"
      type="checkbox"
    > {{ t('proxyKeys.remember') }}</label>
    <button
      data-action="generate"
      @click="generate"
    >
      {{ t('proxyKeys.generate') }}
    </button>
    <dialog
      v-if="revealed"
      open
      role="dialog"
    >
      <h2>{{ t('proxyKeys.warning') }}</h2>
      <code>{{ revealed }}</code>
      <button
        data-action="dismiss"
        @click="dismiss"
      >
        {{ t('tenants.cancel') }}
      </button>
    </dialog>
    <h2>{{ t('proxyKeys.list') }}</h2>
    <ul>
      <li
        v-for="(key, index) in keys"
        :key="index"
      >
        <code>{{ key.api_key_masked }}</code><span>{{ key.created_at }}</span>
      </li>
    </ul>
  </section>
</template>
<style scoped>header p{color:var(--route);font-weight:700}header h1{font-size:32px}h2{font-size:20px;margin:24px 0 12px}button{border:0;border-radius:8px;background:var(--route);color:#fff;padding:0 16px;height:44px}label{display:flex;gap:8px;align-items:center;margin:16px 0}dialog{position:fixed;inset:50% auto auto 50%;transform:translate(-50%,-50%);border:1px solid var(--border);border-radius:14px;padding:28px}dialog code{display:block;background:var(--monitor-canvas);color:var(--monitor-text);padding:14px;margin:16px 0;border-radius:8px}ul{list-style:none;padding:0}li{display:flex;justify-content:space-between;padding:12px;border-bottom:1px solid var(--border)}li span{color:var(--muted)}</style>
