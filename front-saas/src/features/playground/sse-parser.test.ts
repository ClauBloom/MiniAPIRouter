import { expect, it } from 'vitest'
import { createSseParser } from './sse-parser'

it('parses OpenAI deltas across arbitrary chunk boundaries', () => {
  const texts: string[] = []
  const parser = createSseParser('openai', frame => { if (frame.type === 'delta') texts.push(frame.text ?? '') })
  parser.feed('data: {"choices":[{"delta":{"content":"hel')
  parser.feed('lo"}}]}\n\n')
  parser.feed('data: {"choices":[{"delta":{"content":" world"}}]}\n\n')
  parser.end()
  expect(texts).toEqual(['hello', ' world'])
})

it('emits done on OpenAI [DONE] and error on error frames', () => {
  const events: string[] = []
  const parser = createSseParser('openai', frame => events.push(frame.type))
  parser.feed('data: {"error":{"message":"boom"}}\n\n')
  parser.feed('data: [DONE]\n\n')
  parser.end()
  expect(events).toEqual(['error', 'done'])
})

it('parses Anthropic text deltas and stop', () => {
  const texts: string[] = []
  const done: string[] = []
  const parser = createSseParser('anthropic', frame => {
    if (frame.type === 'delta') texts.push(frame.text ?? '')
    if (frame.type === 'done') done.push('done')
  })
  parser.feed('event: content_block_delta\ndata: {"type":"content_block_delta","delta":{"type":"text_delta","text":"hi"}}\n\n')
  parser.feed('event: message_stop\ndata: {"type":"message_stop"}\n\n')
  parser.end()
  expect(texts).toEqual(['hi'])
  expect(done).toEqual(['done'])
})
