const Chat = (() => {
  let messages = [];
  let streaming = false;
  let abortController = null;
  let currentStreamingEl = null;

  function init() {
    const input = document.getElementById('chat-input');
    const sendBtn = document.getElementById('chat-send');
    const stopBtn = document.getElementById('chat-stop');
    const modelSelect = document.getElementById('chat-model');

    sendBtn.addEventListener('click', send);
    stopBtn.addEventListener('click', stop);
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        send();
      }
    });

    loadModels();
  }

  async function loadModels() {
    const modelSelect = document.getElementById('chat-model');
    try {
      const data = await API.get('/api/v1/config/api-keys?page=1&page_size=100');
      const keys = data.list || [];
      const models = [];
      const seen = new Set();
      keys.forEach(key => {
        Object.keys(key.model_mapping || {}).forEach(name => {
          if (!seen.has(name)) {
            seen.add(name);
            models.push(name);
          }
        });
      });

      if (models.length === 0) {
        modelSelect.innerHTML = '<option value="">⚠ 请先在配置页添加 API Key</option>';
        const empty = document.getElementById('chat-empty');
        if (empty) {
          empty.querySelector('.chat-empty-icon').textContent = '⚙';
          empty.querySelector('.chat-empty-text').innerHTML = '尚未配置 API Key<br>请先切换到「配置」页面添加';
        }
        return;
      }

      modelSelect.innerHTML = '<option value="">-- 选择模型 --</option>';
      models.forEach(m => {
        const opt = document.createElement('option');
        opt.value = m;
        opt.textContent = m;
        modelSelect.appendChild(opt);
      });
    } catch (e) {
      console.error('Failed to load models:', e);
    }
  }

  async function send() {
    const input = document.getElementById('chat-input');
    const modelSelect = document.getElementById('chat-model');
    const content = input.value.trim();
    const model = modelSelect.value;

    if (!content) return;
    if (!model) { showToast('请先选择模型', 'error'); return; }
    if (streaming) return;

    messages.push({ role: 'user', content });
    input.value = '';

    renderMessages();
    appendAssistantPlaceholder();

    streaming = true;
    document.getElementById('chat-send').style.display = 'none';
    document.getElementById('chat-stop').style.display = 'inline-block';

    abortController = new AbortController();
    const assistantIdx = messages.length;

    try {
      const token = API.getToken();
      const res = await fetch('/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Authorization': 'Bearer ' + token,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          model,
          messages: messages.map(m => ({ role: m.role, content: m.content })),
          stream: true,
        }),
        signal: abortController.signal,
      });

      if (!res.ok) {
        const errText = await res.text();
        let errMsg = errText;
        try {
          const errJson = JSON.parse(errText);
          if (errJson.error && errJson.error.message) errMsg = errJson.error.message;
          else if (errJson.message) errMsg = errJson.message;
        } catch (e2) {}
        if (res.status === 503) errMsg = '无可用上游，请先在「配置」页面添加 API Key';
        throw new Error(errMsg);
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let assistantContent = '';
      let usage = null;
      let fallbackOccurred = false;

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('event: fallback_signal')) {
            fallbackOccurred = true;
          } else if (line.startsWith('event: usage_stats')) {
            // next data line has usage
          } else if (line.startsWith('data: ')) {
            const data = line.slice(6);
            if (data === '[DONE]') continue;
            try {
              const chunk = JSON.parse(data);
              if (chunk.choices && chunk.choices[0]) {
                const delta = chunk.choices[0].delta;
                if (delta && delta.content) {
                  assistantContent += delta.content;
                  updateAssistantMessage(assistantIdx, assistantContent, { streaming: true, fallback: fallbackOccurred });
                }
              }
              if (chunk.usage) {
                usage = chunk.usage;
              }
              if (chunk.type === 'usage_stats') {
                usage = chunk;
              }
            } catch (e) {
              // might be usage_stats event data
              try {
                const parsed = JSON.parse(data);
                if (parsed.type === 'usage_stats') usage = parsed;
              } catch (e2) {}
            }
          }
        }
      }

      messages.push({ role: 'assistant', content: assistantContent });
      updateAssistantMessage(assistantIdx, assistantContent, { streaming: false, fallback: fallbackOccurred, usage });
    } catch (e) {
      if (e.name === 'AbortError') {
        messages.push({ role: 'assistant', content: '（已停止生成）' });
        updateAssistantMessage(assistantIdx, '（已停止生成）', { streaming: false });
      } else {
        appendErrorMessage(e.message);
      }
    } finally {
      streaming = false;
      currentStreamingEl = null;
      document.getElementById('chat-send').style.display = 'inline-block';
      document.getElementById('chat-stop').style.display = 'none';
      abortController = null;
    }
  }

  function stop() {
    if (abortController) abortController.abort();
  }

  function renderMessages() {
    const container = document.getElementById('chat-messages');
    const empty = document.getElementById('chat-empty');
    if (empty) empty.remove();

    const lastMsg = messages[messages.length - 1];
    if (lastMsg && lastMsg.role === 'user') {
      const msgEl = createMessageEl('user', lastMsg.content);
      container.appendChild(msgEl);
    }
    container.scrollTop = container.scrollHeight;
  }

  function appendAssistantPlaceholder() {
    const container = document.getElementById('chat-messages');
    const msgEl = document.createElement('div');
    msgEl.className = 'msg msg-assistant';
    msgEl.innerHTML = `
      <div class="msg-avatar">AI</div>
      <div class="msg-bubble"><span class="spinner"></span></div>
    `;
    container.appendChild(msgEl);
    currentStreamingEl = msgEl;
    container.scrollTop = container.scrollHeight;
  }

  function updateAssistantMessage(idx, content, opts = {}) {
    const el = currentStreamingEl;
    if (!el) return;

    let html = '';
    if (opts.fallback) {
      html += '<div class="fallback-hint">⚡ 上游切换 (Fallback)</div>';
    }
    html += renderMarkdown(content);
    if (!opts.streaming && opts.usage) {
      const u = opts.usage;
      html += `<div class="chat-usage">
        <span>📦 ${u.prompt_tokens || 0} + ${u.completion_tokens || 0} = ${u.total_tokens || 0} tokens</span>
        ${u.latency_ms ? `<span>⏱ ${u.latency_ms}ms</span>` : ''}
        ${u.fallback_count ? `<span>🔄 fallback ×${u.fallback_count}</span>` : ''}
      </div>`;
    }

    el.querySelector('.msg-bubble').innerHTML = html;
    const container = document.getElementById('chat-messages');
    container.scrollTop = container.scrollHeight;
  }

  function appendErrorMessage(msg) {
    const container = document.getElementById('chat-messages');
    const msgEl = document.createElement('div');
    msgEl.className = 'msg msg-error';
    msgEl.innerHTML = `
      <div class="msg-avatar">!</div>
      <div class="msg-bubble">${escapeHtml(msg)}</div>
    `;
    container.appendChild(msgEl);
    container.scrollTop = container.scrollHeight;
  }

  function createMessageEl(role, content) {
    const el = document.createElement('div');
    el.className = 'msg msg-' + role;
    const avatar = role === 'user' ? '你' : 'AI';
    el.innerHTML = `
      <div class="msg-avatar">${avatar}</div>
      <div class="msg-bubble">${renderMarkdown(content)}</div>
    `;
    return el;
  }

  function clear() {
    messages = [];
    const container = document.getElementById('chat-messages');
    container.innerHTML = `<div class="chat-empty" id="chat-empty">
      <div class="chat-empty-icon">💬</div>
      <div class="chat-empty-text">开始对话吧</div>
    </div>`;
  }

  return { init, clear, loadModels };
})();
