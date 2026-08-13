<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ResponsiveCollection from '@/shared/ui/ResponsiveCollection.vue'
import { useBrowserHttp } from '@/shared/api/browser-http'
interface Member { id: number; username: string; nickname: string | null; role: string; status: number }
const { t } = useI18n()
const http = useBrowserHttp()
const members = ref<Member[]>([])
async function load() { members.value = (await http.get<{ list: Member[] }>('/api/v1/tenant/members')).data.list }
onMounted(load)
</script>
<template>
  <section>
    <header><p>{{ t('navigation.routing') }}</p><h1>{{ t('members.title') }}</h1></header>
    <ResponsiveCollection>
      <template #table>
        <table>
          <thead><tr><th>{{ t('users.username') }}</th><th>{{ t('users.role') }}</th><th>{{ t('users.status') }}</th></tr></thead><tbody>
            <tr
              v-for="member in members"
              :key="member.id"
            >
              <td>{{ member.nickname || member.username }}</td><td>{{ member.role }}</td><td>{{ member.status ? t('tenants.enabled') : t('tenants.disabled') }}</td>
            </tr>
          </tbody>
        </table>
      </template>
      <template #cards>
        <article
          v-for="member in members"
          :key="member.id"
        >
          <h3>{{ member.nickname || member.username }}</h3><p>{{ member.role }}</p>
        </article>
      </template>
    </ResponsiveCollection>
  </section>
</template>
<style scoped>header p{color:var(--route);font-weight:700}header h1{font-size:32px}table{width:100%;background:var(--surface);border-collapse:collapse}th,td{padding:14px;text-align:left;border-bottom:1px solid var(--border)}article{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:16px;margin-bottom:12px}</style>
