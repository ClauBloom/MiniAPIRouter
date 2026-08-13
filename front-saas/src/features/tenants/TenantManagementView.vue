<script setup lang="ts">
import { onMounted, ref } from 'vue'
import TenantListView from './TenantListView.vue'
import { createTenantApi } from './tenant-api'
import { useBrowserHttp } from '@/shared/api/browser-http'
import type { QuotaAdjustment, Tenant, TenantOnboarding } from './contracts'
const api=createTenantApi(useBrowserHttp());const tenants=ref<Tenant[]>([])
async function load(){tenants.value=(await api.list()).list}
async function adjust(id:number,input:QuotaAdjustment){await api.adjustQuota(id,input);await load()}
async function create(input:TenantOnboarding){await api.create(input);await load()}
onMounted(load)
</script><template>
  <TenantListView
    :tenants="tenants"
    :on-adjust-quota="adjust"
    :on-create-tenant="create"
  />
</template>
