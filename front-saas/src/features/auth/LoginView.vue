<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import LoginForm from './components/LoginForm.vue'
import RouteTrace from '@/shared/ui/RouteTrace.vue'
import type { LoginInput } from '@/shared/api/contracts'
import { useSessionStore } from '@/app/stores/session'
const router=useRouter(), route=useRoute(); const {t}=useI18n(); const session=useSessionStore(); const pending=ref(false); const error=ref('')
function safeRedirect(value: unknown){return typeof value==='string'&&value.startsWith('/')&&!value.startsWith('//')?value:'/overview'}
async function submit(input:LoginInput){pending.value=true;error.value='';try{await session.login(input);await router.replace(safeRedirect(route.query.redirect))}catch{error.value=t('errors.unauthorized')}finally{pending.value=false}}
const steps=[{id:'request',label:'Request',status:'complete' as const},{id:'intent',label:'Intent',detail:'coding_review',status:'complete' as const},{id:'rule',label:'Rule',status:'active' as const},{id:'model',label:'Model',status:'skipped' as const}]
</script>
<template>
  <main class="login">
    <section class="story">
      <p class="eyebrow">
        {{ t('brand.console') }}
      </p><h1>{{ t('auth.welcome') }}</h1><RouteTrace :steps="steps" />
    </section><section class="panel">
      <div><b>{{ t('brand.name') }}</b><h2>{{ t('auth.signIn') }}</h2></div><p
        v-if="error"
        role="alert"
      >
        {{ error }}
      </p><LoginForm
        :pending="pending"
        :on-submit="submit"
      />
    </section>
  </main>
</template>
<style scoped>.login{min-height:100vh;display:grid;grid-template-columns:1.25fr minmax(360px,.75fr);padding:5vw;gap:6vw;align-items:center;background:var(--canvas)}.story h1{font:600 clamp(38px,6vw,76px)/1.02 'Space Grotesk',sans-serif;max-width:800px}.eyebrow{color:var(--route);font-weight:700;letter-spacing:.12em;text-transform:uppercase}.panel{background:var(--surface);border:1px solid var(--border);border-radius:18px;padding:36px;max-width:480px;width:100%;box-shadow:0 24px 60px rgba(16,37,30,.08)}[role=alert]{color:var(--danger)}@media(max-width:767px){.login{display:flex;flex-direction:column;padding:24px 16px}.story h1{font-size:34px}.panel{padding:24px}.story :deep(.route-trace){margin-top:28px}}</style>
