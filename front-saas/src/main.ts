import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { installRouterGuards, router } from './app/router'
import { i18n } from './locales'
import './shared/styles/base.scss'

const app = createApp(App)
app.use(createPinia())
installRouterGuards()
app.use(i18n).use(router).mount('#app')
