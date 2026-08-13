import { createHttpClient } from './http'
import { useSessionStore } from '@/app/stores/session'
import { useTenantContextStore } from '@/app/stores/tenant-context'
import { i18n } from '@/locales'
export function useBrowserHttp(){const session=useSessionStore();const tenant=useTenantContextStore();return createHttpClient({getAccessToken:()=>session.accessToken,refresh:()=>session.refresh(),clear:()=>session.clear()},{getTenantOverride:()=>tenant.overrideTenantId,getLocale:()=>i18n.global.locale.value})}
