import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'
import { installSessionGuard } from './guard'
import { useSessionStore } from '@/app/stores/session'
export const router = createRouter({ history: createWebHistory(), routes })
export function installRouterGuards() { installSessionGuard(router, useSessionStore()) }
