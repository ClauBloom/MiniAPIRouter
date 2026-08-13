export type StreamFrame = { type: 'delta' | 'done' | 'error' | 'usage' | 'event'; text?: string; raw?: unknown }
type FrameHandler = (frame: StreamFrame) => void

export function createSseParser(protocol: 'openai' | 'anthropic', onFrame: FrameHandler) {
  let buffer = ''
  function feed(chunk: string) {
    buffer += chunk
    let boundary = buffer.indexOf('\n\n')
    while (boundary !== -1) {
      const rawEvent = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      parseEvent(rawEvent)
      boundary = buffer.indexOf('\n\n')
    }
  }
  function end() {
    if (buffer.trim()) parseEvent(buffer)
    buffer = ''
  }
  function parseEvent(rawEvent: string) {
    const lines = rawEvent.split('\n')
    const eventType = lines.find(l => l.startsWith('event:'))?.slice(6).trim()
    const dataLines = lines.filter(l => l.startsWith('data:')).map(l => l.slice(5).trimStart())
    if (dataLines.length === 0) return
    const data = dataLines.join('\n')
    if (protocol === 'openai') parseOpenAi(data, onFrame)
    else parseAnthropic(eventType, data, onFrame)
  }
  return { feed, end }
}

function parseOpenAi(data: string, onFrame: FrameHandler) {
  if (data === '[DONE]') { onFrame({ type: 'done' }); return }
  try {
    const payload = JSON.parse(data)
    if (payload?.error) { onFrame({ type: 'error', text: String(payload.error.message ?? 'upstream error'), raw: payload }); return }
    if (payload?.usage) onFrame({ type: 'usage', raw: payload.usage })
    const delta = payload?.choices?.[0]?.delta?.content
    if (typeof delta === 'string' && delta) onFrame({ type: 'delta', text: delta })
  } catch { /* ignore malformed keep-alive frame */ }
}

function parseAnthropic(eventType: string | undefined, data: string, onFrame: FrameHandler) {
  try {
    const payload = JSON.parse(data)
    if (payload?.type === 'error') { onFrame({ type: 'error', text: String(payload.error?.message ?? 'upstream error'), raw: payload }); return }
    if (payload?.type === 'message_stop' || eventType === 'message_stop') { onFrame({ type: 'done' }); return }
    if (payload?.type === 'content_block_delta') {
      const delta = payload.delta
      if (delta?.type === 'text_delta' && typeof delta.text === 'string') onFrame({ type: 'delta', text: delta.text })
      else if (delta?.type === 'input_json_delta' && typeof delta.partial_json === 'string') onFrame({ type: 'delta', text: delta.partial_json })
    } else if (payload?.type === 'message_start' && payload?.message?.usage) {
      onFrame({ type: 'usage', raw: payload.message.usage })
    }
  } catch { /* ignore malformed frame */ }
}
