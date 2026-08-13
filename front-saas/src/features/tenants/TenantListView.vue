<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ResponsiveCollection from '@/shared/ui/ResponsiveCollection.vue'
import { useTenantContextStore } from '@/app/stores/tenant-context'
import type { QuotaAdjustment, Tenant, TenantOnboarding } from './contracts'
const props=defineProps<{tenants:Tenant[];onAdjustQuota?:(id:number,input:QuotaAdjustment)=>Promise<void>|void;onCreateTenant?:(input:TenantOnboarding)=>Promise<void>|void}>()
const {t}=useI18n();const context=useTenantContextStore();const editing=ref<Tenant|null>(null);const limit=ref(0);const reason=ref('');const error=ref('');const creating=ref(false);const onboarding=ref<TenantOnboarding>({tenant_code:'',tenant_name:'',plan:'free',quota_limit:1000000,max_rps:10,admin_username:'',admin_password:''})
function openQuota(item:Tenant){editing.value=item;limit.value=item.quota_limit;reason.value='';error.value=''}
async function submitQuota(){if(!reason.value.trim()){error.value=t('tenants.reasonRequired');return}if(limit.value<=0){error.value=t('tenants.quotaPositive');return}if(editing.value&&props.onAdjustQuota)await props.onAdjustQuota(editing.value.id,{quota_limit:limit.value,reason:reason.value.trim()});editing.value=null}
async function submitCreate(){if(props.onCreateTenant)await props.onCreateTenant({...onboarding.value});onboarding.value.admin_password='';creating.value=false}
</script>
<template>
  <section>
    <header class="page-head">
      <div><p>{{ t('navigation.platform') }}</p><h1>{{ t('tenants.title') }}</h1></div><button
        data-action="create-tenant"
        @click="creating=true"
      >
        {{ t('tenants.create') }}
      </button>
    </header><div
      v-if="context.overrideTenantId"
      class="context-bar"
      role="status"
    >
      <span>{{ t('status.viewingTenant',{name:context.overrideTenantName}) }}</span><button
        data-action="leave-context"
        @click="context.leave()"
      >
        {{ t('tenants.leave') }}
      </button>
    </div><ResponsiveCollection>
      <template #table>
        <table>
          <thead><tr><th>{{ t('tenants.name') }}</th><th>{{ t('tenants.plan') }}</th><th>{{ t('tenants.quota') }}</th><th>{{ t('tenants.status') }}</th><th>{{ t('tenants.actions') }}</th></tr></thead><tbody>
            <tr
              v-for="item in tenants"
              :key="item.id"
            >
              <td><b>{{ item.tenant_name }}</b><small>{{ item.tenant_code }}</small></td><td>{{ item.plan }}</td><td>{{ item.quota_used }} / {{ item.quota_limit }}</td><td>{{ item.status?t('tenants.enabled'):t('tenants.disabled') }}</td><td>
                <button
                  :data-action="`quota-${item.id}`"
                  @click="openQuota(item)"
                >
                  {{ t('tenants.adjustQuota') }}
                </button><button
                  :data-action="`enter-${item.id}`"
                  @click="context.enter(item.id,item.tenant_name)"
                >
                  {{ t('tenants.enter') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </template><template #cards>
        <article
          v-for="item in tenants"
          :key="item.id"
        >
          <h2>{{ item.tenant_name }}</h2><p>{{ item.tenant_code }} · {{ item.plan }}</p><p>{{ item.quota_used }} / {{ item.quota_limit }}</p><div>
            <button
              :data-action="`quota-${item.id}`"
              @click="openQuota(item)"
            >
              {{ t('tenants.adjustQuota') }}
            </button><button
              :data-action="`enter-${item.id}`"
              @click="context.enter(item.id,item.tenant_name)"
            >
              {{ t('tenants.enter') }}
            </button>
          </div>
        </article>
      </template>
    </ResponsiveCollection><dialog :open="creating">
      <h2>{{ t('tenants.create') }}</h2><label>{{ t('tenants.code') }}<input
        v-model="onboarding.tenant_code"
        name="tenant_code"
      ></label><label>{{ t('tenants.name') }}<input
        v-model="onboarding.tenant_name"
        name="tenant_name"
      ></label><label>{{ t('users.username') }}<input
        v-model="onboarding.admin_username"
        name="admin_username"
      ></label><label>{{ t('auth.password') }}<input
        v-model="onboarding.admin_password"
        name="admin_password"
        type="password"
      ></label><button
        data-submit="create-tenant"
        @click="submitCreate"
      >
        {{ t('tenants.save') }}
      </button><button @click="creating=false">
        {{ t('tenants.cancel') }}
      </button>
    </dialog><dialog :open="!!editing">
      <h2>{{ t('tenants.adjustQuota') }}</h2><label>{{ t('tenants.quota') }}<input
        v-model.number="limit"
        type="number"
        min="1"
      ></label><label>{{ t('tenants.reason') }}<textarea v-model="reason" /></label><p
        v-if="error"
        role="alert"
      >
        {{ error }}
      </p><button
        data-submit="quota"
        @click="submitQuota"
      >
        {{ t('tenants.save') }}
      </button><button @click="editing=null">
        {{ t('tenants.cancel') }}
      </button>
    </dialog>
  </section>
</template>
<style scoped>.page-head{display:flex;justify-content:space-between;align-items:end;margin-bottom:24px}.page-head p{color:var(--route);font-weight:700}.page-head h1{font-size:32px;margin:4px 0}.page-head button,.context-bar button,td button,article button,dialog button{border:0;border-radius:8px;padding:0 14px;background:var(--route);color:white}.context-bar{display:flex;justify-content:space-between;align-items:center;background:var(--route-soft);padding:10px 14px;border-radius:10px;margin-bottom:18px}table{width:100%;border-collapse:collapse;background:var(--surface);border:1px solid var(--border)}th,td{text-align:left;padding:14px;border-bottom:1px solid var(--border)}td small{display:block;color:var(--muted)}td button+button,article button+button{margin-left:8px;background:var(--signal)}article{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:18px;margin-bottom:12px}dialog{position:fixed;inset:50% auto auto 50%;transform:translate(-50%,-50%);border:1px solid var(--border);border-radius:14px;padding:24px;min-width:min(420px,90vw);z-index:40}dialog label{display:grid;gap:6px;margin:14px 0}dialog input,dialog textarea{font:inherit;padding:10px;border:1px solid var(--border);border-radius:8px}dialog [role=alert]{color:var(--danger)}@media(max-width:767px){.page-head h1{font-size:25px}}
</style>
