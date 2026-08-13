<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Playground, { type StreamFn } from './Playground.vue'
import { createSseParser } from './sse-parser'
const { t } = useI18n()
const stream: StreamFn = input => {
  const controller = new AbortController()
  const startedAt = performance.now()
  void (async () => {
    try {
      const response = await fetch('/v1/chat/completions', {
        method: 'POST', signal: controller.signal,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${input.apiKey}` },
        body: JSON.stringify({ model: input.model, stream: true, messages: [{ role: 'user', content: input.prompt }] }),
      })
      if (!response.ok) throw new Error(response.status >= 500 ? 'upstream' : 'server')
      const parser = createSseParser('openai', frame => {
        if (frame.type === 'delta') input.handlers.onDelta(frame.text ?? '')
        else if (frame.type === 'done') input.handlers.onDone()
        else if (frame.type === 'error') { input.handlers.onError('server') }
        else if (frame.type === 'usage') input.handlers.onMeta({ usage: 1 })
      })
      const reader = response.body?.getReader()
      if (!reader) { input.handlers.onDone(); return }
      const decoder = new TextDecoder()
      let ttftSent = false
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        if (!ttftSent) { input.handlers.onMeta({ ttft_ms: Math.round(performance.now() - startedAt) }); ttftSent = true }
        parser.feed(decoder.decode(value, { stream: true }))
      }
      parser.end()
      input.handlers.onDone()
    } catch (error) {
      if ((error as Error).name === 'AbortError') input.handlers.onError('cancelled')
      else input.handlers.onError('network')
    }
  })()
  return { abort: () => controller.abort() }
}
</script>
<template>
  <section>
    <header><p>{{ t('navigation.workspace') }}</p><h1>{{ t('navigation.playground') }}</h1></header>
    <Playground :stream="stream" />
  </section>
</template>
<style scoped>header p{color:var(--route);font-weight:700}header h1{font-size:32px}</style>
