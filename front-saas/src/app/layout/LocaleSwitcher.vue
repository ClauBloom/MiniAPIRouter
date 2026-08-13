<script setup lang="ts">
import { watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePreferencesStore, type Locale } from '@/app/stores/preferences'
const { t, locale } = useI18n()
const preferences = usePreferencesStore()
watch(locale, value => preferences.setLocale(value as Locale))
function toggle() { locale.value = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN' }
</script>
<template>
  <button
    class="locale-switcher"
    :aria-label="t('actions.switchLanguage')"
    @click="toggle"
  >
    {{ locale === 'zh-CN' ? 'EN' : '中' }}
  </button>
</template>
<style scoped>.locale-switcher{min-width:44px;min-height:44px;border:1px solid var(--border);border-radius:8px;background:var(--surface);color:var(--ink);font-weight:700;cursor:pointer}.locale-switcher:hover{border-color:var(--route)}</style>
