<script setup lang="ts">
import { useI18n } from 'vue-i18n'
export interface RouteTraceStep { id: string; label: string; detail?: string; status: 'complete'|'active'|'failed'|'skipped' }
defineProps<{ steps: RouteTraceStep[] }>()
const { t } = useI18n()
</script>
<template>
  <ol
    class="route-trace"
    :aria-label="t('routeTrace.label')"
  >
    <li
      v-for="step in steps"
      :key="step.id"
      :class="`is-${step.status}`"
      :aria-current="step.status === 'active' ? 'step' : undefined"
    >
      <span
        class="node"
        aria-hidden="true"
      />
      <span class="content"><strong>{{ step.label }}</strong><small v-if="step.detail">{{ step.detail }}</small><span class="sr-only">{{ t(`routeTrace.${step.status}`) }}</span></span>
    </li>
  </ol>
</template>
<style scoped lang="scss">
.route-trace{display:flex;list-style:none;padding:0;margin:0;gap:0}.route-trace li{display:flex;position:relative;flex:1;gap:10px;align-items:flex-start}.route-trace li:not(:last-child)::after{content:'';height:2px;background:var(--border);position:absolute;left:18px;right:0;top:8px}.node{z-index:1;width:18px;height:18px;border:3px solid var(--border);border-radius:50%;background:var(--surface);flex:none}.is-complete .node,.is-active .node{border-color:var(--route)}.is-active .node{background:var(--route)}.is-failed .node{border-color:var(--danger)}.content{display:grid;gap:2px;z-index:1;background:var(--surface);padding-right:12px}.content small{color:var(--muted);font-family:'IBM Plex Mono',monospace}@media(max-width:767px){.route-trace{display:grid;gap:14px}.route-trace li::after{display:none}}@media(prefers-reduced-motion:no-preference){.is-active .node{animation:pulse 1.8s ease-in-out infinite}@keyframes pulse{50%{box-shadow:0 0 0 6px var(--route-soft)}}}
</style>
