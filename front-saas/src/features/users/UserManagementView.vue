<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ResponsiveCollection from '@/shared/ui/ResponsiveCollection.vue'
import { useBrowserHttp } from '@/shared/api/browser-http'
interface User {id:number;tenant_id:number;username:string;nickname:string|null;role:string;status:number}
const props=defineProps<{users?:User[];resetPassword?:(id:number)=>Promise<{temporary_password:string}>;changeStatus?:(id:number,status:number)=>Promise<void>}>();const {t}=useI18n();const temporary=ref('');const loaded=ref<User[]>([]);const http=useBrowserHttp()
async function load(){if(!props.users)loaded.value=(await http.get<{list:User[]}>('/api/v1/admin/users')).data.list}
async function reset(id:number){const action=props.resetPassword??(async(userId:number)=>(await http.post<{temporary_password:string}>(`/api/v1/admin/users/${userId}/reset-password`)).data);temporary.value=(await action(id)).temporary_password}
async function status(user:User){const action=props.changeStatus??(async(id:number,value:number)=>{await http.patch(`/api/v1/admin/users/${id}/status`,{status:value})});await action(user.id,user.status?0:1);await load()}
onMounted(load)
function dismiss(){temporary.value=''}
</script><template>
  <section>
    <header><p>{{ t('navigation.platform') }}</p><h1>{{ t('users.title') }}</h1></header><ResponsiveCollection>
      <template #table>
        <table>
          <thead><tr><th>{{ t('users.username') }}</th><th>{{ t('users.tenant') }}</th><th>{{ t('users.role') }}</th><th>{{ t('users.status') }}</th><th /></tr></thead><tbody>
            <tr
              v-for="user in (users??loaded)"
              :key="user.id"
            >
              <td>{{ user.nickname||user.username }}<small>{{ user.username }}</small></td><td>{{ user.tenant_id }}</td><td>{{ user.role }}</td><td>{{ user.status?t('tenants.enabled'):t('tenants.disabled') }}</td><td>
                <button
                  :data-action="`status-${user.id}`"
                  @click="status(user)"
                >
                  {{ user.status?t('tenants.disabled'):t('tenants.enabled') }}
                </button>
                <button
                  :data-action="`reset-${user.id}`"
                  @click="reset(user.id)"
                >
                  {{ t('users.reset') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </template><template #cards>
        <article
          v-for="user in (users??loaded)"
          :key="user.id"
        >
          <h2>{{ user.nickname||user.username }}</h2><p>{{ user.role }} · {{ user.tenant_id }}</p><button
            :data-action="`status-${user.id}`"
            @click="status(user)"
          >
            {{ user.status?t('tenants.disabled'):t('tenants.enabled') }}
          </button><button
            :data-action="`reset-${user.id}`"
            @click="reset(user.id)"
          >
            {{ t('users.reset') }}
          </button>
        </article>
      </template>
    </ResponsiveCollection><dialog
      v-if="temporary"
      open
      role="dialog"
    >
      <h2>{{ t('users.temporaryPassword') }}</h2><code>{{ temporary }}</code><button
        data-action="dismiss-password"
        @click="dismiss"
      >
        {{ t('users.dismiss') }}
      </button>
    </dialog>
  </section>
</template><style scoped>header p{color:var(--route);font-weight:700}header h1{font-size:32px}table{width:100%;background:var(--surface);border-collapse:collapse}th,td{padding:14px;text-align:left;border-bottom:1px solid var(--border)}small{display:block;color:var(--muted)}button{border:0;border-radius:8px;background:var(--route);color:white;padding:0 14px}article{background:white;border:1px solid var(--border);border-radius:12px;padding:18px;margin-bottom:12px}dialog{position:fixed;inset:50% auto auto 50%;transform:translate(-50%,-50%);border:1px solid var(--border);border-radius:14px;padding:28px}code{display:block;background:var(--monitor-canvas);color:var(--monitor-text);padding:14px;margin:16px 0;border-radius:8px}</style>
