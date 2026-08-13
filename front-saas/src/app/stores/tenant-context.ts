import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useTenantContextStore = defineStore('tenant-context', () => {
  const overrideTenantId = ref<number | null>(null)
  const overrideTenantName = ref<string | null>(null)
  function enter(tenantId: number, tenantName?: string) { overrideTenantId.value = tenantId; overrideTenantName.value = tenantName ?? null }
  function leave() { overrideTenantId.value = null; overrideTenantName.value = null }
  return { overrideTenantId, overrideTenantName, enter, leave }
})
