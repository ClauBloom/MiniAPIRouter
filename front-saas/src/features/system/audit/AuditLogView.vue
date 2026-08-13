<script setup lang="ts">import {onMounted,ref} from 'vue';import {useI18n} from 'vue-i18n';import {useBrowserHttp} from '@/shared/api/browser-http';interface Event{id:number;action:string;actor_user_id:number|null;target_tenant_id:number|null;trace_id:string|null}const props=defineProps<{events?:Event[]}>();const{t}=useI18n();const loaded=ref<Event[]>([]);const http=useBrowserHttp();onMounted(async()=>{if(!props.events)loaded.value=(await http.get<{list:Event[]}>('/api/v1/admin/audit-logs')).data.list})</script><template>
  <section>
    <p>{{ t('navigation.platform') }}</p><h1>{{ t('audit.title') }}</h1><article
      v-for="event in events??loaded"
      :key="event.id"
    >
      <b>{{ event.action }}</b><span>{{ t('audit.actor') }} #{{ event.actor_user_id }}</span><span>{{ t('audit.target') }} #{{ event.target_tenant_id }}</span><code>{{ event.trace_id }}</code>
    </article>
  </section>
</template><style scoped>section>p{color:var(--route);font-weight:700}h1{font-size:32px}article{display:grid;grid-template-columns:2fr 1fr 1fr 1fr;gap:12px;background:var(--surface);padding:15px;border-bottom:1px solid var(--border)}@media(max-width:767px){article{grid-template-columns:1fr}article span{color:var(--muted)}}</style>
