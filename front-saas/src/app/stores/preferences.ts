import { defineStore } from 'pinia'
import { ref } from 'vue'

export type Locale = 'zh-CN' | 'en-US'
export const usePreferencesStore = defineStore('preferences', () => {
  const locale = ref<Locale>((localStorage.getItem('locale') as Locale) || 'zh-CN')
  function setLocale(value: Locale) { locale.value = value; localStorage.setItem('locale', value) }
  return { locale, setLocale }
})
