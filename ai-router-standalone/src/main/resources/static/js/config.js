const Config = (() => {
  let keys = [];
  let rules = [];
  let intents = [];

  function init() {
    document.getElementById('btn-add-key').addEventListener('click', () => showKeyModal());
    document.getElementById('btn-add-rule').addEventListener('click', () => showRuleModal());
    document.getElementById('btn-add-intent').addEventListener('click', () => showIntentModal());
    document.getElementById('btn-clear-chat').addEventListener('click', () => {
      Chat.clear();
      Chat.loadModels();
    });
  }

  async function load() {
    await Promise.all([loadKeys(), loadRules(), loadIntents()]);
  }

  async function loadKeys() {
    try {
      const data = await API.get('/api/v1/config/api-keys?page=1&page_size=100');
      keys = data.list || [];
      renderKeys();
    } catch (e) {
      showToast('加载 API Key 失败: ' + e.message, 'error');
    }
  }

  async function loadRules() {
    try {
      const data = await API.get('/api/v1/config/route-rules?page=1&page_size=100');
      rules = data.list || [];
      renderRules();
    } catch (e) {
      showToast('加载路由规则失败: ' + e.message, 'error');
    }
  }

  async function loadIntents() {
    try {
      const data = await API.get('/api/v1/config/intents');
      intents = data.list || [];
      renderIntents();
    } catch (e) {
      showToast('加载意图配置失败: ' + e.message, 'error');
    }
  }

  function renderKeys() {
    const container = document.getElementById('key-list');
    if (keys.length === 0) {
      container.innerHTML = '<div class="empty-state">暂无 API Key，点击右上角添加</div>';
      return;
    }
    container.innerHTML = keys.map(k => `
      <div class="key-item">
        <div class="key-info">
          <div class="key-name">${escapeHtml(k.name)}</div>
          <div class="key-meta">
            ${escapeHtml(k.provider)} · ${escapeHtml(k.protocol)} · ${Object.entries(k.model_mapping || {}).map(([n, r]) => n === r ? escapeHtml(n) : `${escapeHtml(n)}→${escapeHtml(r)}`).join(', ')}
            <br>${escapeHtml(k.base_url)} · Key: ${escapeHtml(k.api_key_masked)}
          </div>
        </div>
        <div class="flex items-center gap-8">
          <span class="badge ${k.status === 1 ? 'badge-green' : 'badge-gray'}">${k.status === 1 ? '启用' : '禁用'}</span>
          <span class="badge ${k.health_status === 'healthy' ? 'badge-green' : k.health_status === 'down' ? 'badge-red' : k.health_status === 'degraded' ? 'badge-orange' : 'badge-gray'}">${escapeHtml(k.health_status)}</span>
          <button class="btn btn-sm" onclick="Config.toggleKey(${k.id}, ${k.status === 1 ? 0 : 1})">${k.status === 1 ? '禁用' : '启用'}</button>
          <button class="btn btn-sm" onclick="Config.showKeyModal(${k.id})">编辑</button>
          <button class="btn btn-sm btn-danger" onclick="Config.deleteKey(${k.id})">删除</button>
        </div>
      </div>
    `).join('');
  }

  function renderRules() {
    const container = document.getElementById('rule-list');
    if (rules.length === 0) {
      container.innerHTML = '<div class="empty-state">暂无路由规则</div>';
      return;
    }
    container.innerHTML = rules.map(r => {
      const targetNames = (r.target_keys || []).map(t => escapeHtml(t.name)).join(', ');
      return `
        <div class="rule-item">
          <div class="rule-header">
            <div>
              <span class="rule-name">${escapeHtml(r.rule_name)}</span>
              <span class="badge ${r.enabled ? 'badge-green' : 'badge-gray'} ml-8">${r.enabled ? '启用' : '禁用'}</span>
            </div>
            <div class="flex gap-8">
              <button class="btn btn-sm" onclick="Config.toggleRule(${r.id}, ${!r.enabled})">${r.enabled ? '禁用' : '启用'}</button>
              <button class="btn btn-sm" onclick="Config.showRuleModal(${r.id})">编辑</button>
              <button class="btn btn-sm btn-danger" onclick="Config.deleteRule(${r.id})">删除</button>
            </div>
          </div>
          <div class="rule-meta">
            匹配: ${escapeHtml(r.match_type)} = ${escapeHtml(r.match_pattern)} · 策略: ${escapeHtml(r.strategy)}
            ${r.intent_model ? ' · 意图模型: ' + escapeHtml(r.intent_model) : ''}
            <br>目标 Key: ${targetNames || '<span class="text-muted">自动使用全部</span>'}
            ${r.fallback_enabled ? ' · Fallback: ✅ (max=' + (r.max_fallback||0) + ')' : ' · Fallback: ❌'}
          </div>
        </div>
      `;
    }).join('');
  }

  function makeModelRow(name, real) {
    return `<div class="form-row model-mapping-row" style="gap:8px;align-items:center">
      <input class="model-name-input" value="${escapeHtml(name)}" placeholder="名称" style="flex:1" pattern="[a-z0-9.-]+">
      <span class="text-muted">→</span>
      <input class="model-real-input" value="${escapeHtml(real)}" placeholder="真实模型名" style="flex:2">
      <button class="btn btn-sm btn-danger" onclick="this.parentElement.remove()">✕</button>
    </div>`;
  }

  function addModelRow() {
    const container = document.getElementById('key-model-mapping');
    const row = document.createElement('div');
    row.className = 'form-row model-mapping-row';
    row.style.cssText = 'gap:8px;align-items:center';
    row.innerHTML = `
      <input class="model-name-input" value="" placeholder="名称" style="flex:1" pattern="[a-z0-9.-]+">
      <span class="text-muted">→</span>
      <input class="model-real-input" value="" placeholder="真实模型名" style="flex:2">
      <button class="btn btn-sm btn-danger" onclick="this.parentElement.remove()">✕</button>
    `;
    container.appendChild(row);
  }

  function showKeyModal(id) {
    const key = id ? keys.find(k => k.id === id) : null;
    const overlay = document.getElementById('modal-overlay');
    overlay.querySelector('.modal').innerHTML = `
      <div class="modal-header">
        <span class="modal-title">${key ? '编辑' : '添加'} API Key</span>
        <button class="btn btn-sm btn-icon" onclick="Config.closeModal()">✕</button>
      </div>
      <div class="modal-body">
        <div class="form-group">
          <label>名称</label>
          <input id="key-name" value="${key ? escapeHtml(key.name) : ''}" placeholder="DeepSeek 主账号">
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>供应商</label>
            <input id="key-provider" list="provider-suggestions" value="${key ? escapeHtml(key.provider) : ''}" placeholder="deepseek / 自定义" autocomplete="off">
            <datalist id="provider-suggestions">
              ${[...new Set([...(keys.map(k => k.provider).filter(Boolean)), 'deepseek','openai','anthropic','azure','gemini'])]
                .map(p => `<option value="${escapeHtml(p)}">`).join('')}
            </datalist>
          </div>
          <div class="form-group">
            <label>协议</label>
            <select id="key-protocol">
              <option value="openai" ${!key || key.protocol === 'openai' ? 'selected' : ''}>openai</option>
              <option value="anthropic" ${key && key.protocol === 'anthropic' ? 'selected' : ''}>anthropic</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label>API Key ${key ? '(留空不修改)' : ''}</label>
          <input id="key-apikey" type="password" placeholder="sk-xxx">
        </div>
        <div class="form-group">
          <label>Base URL <span class="text-muted">(完整请求地址)</span></label>
          <input id="key-baseurl" value="${key ? escapeHtml(key.base_url) : 'https://api.deepseek.com'}" placeholder="https://api.deepseek.com">
        </div>
        <div class="form-group">
          <label>模型映射 <span class="text-muted">(名称 -> 真实模型名)</span></label>
          <div id="key-model-mapping">${key && key.model_mapping ? Object.entries(key.model_mapping).map(([n, r]) => makeModelRow(n, r)).join('') : makeModelRow('', '')}</div>
          <button class="btn btn-sm" onclick="Config.addModelRow()">+ 添加模型映射</button>
          <div class="form-hint">名称 = 对外暴露的模型标识，支持小写字母、数字、-、.　真实模型名 = 发送给上游 API 的实际模型名</div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>优先级</label>
            <input id="key-priority" type="number" value="${key ? key.priority : 0}" min="0">
          </div>
          <div class="form-group">
            <label>超时(ms)</label>
            <input id="key-timeout" type="number" value="${key ? key.timeout_ms : 30000}">
          </div>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn" onclick="Config.closeModal()">取消</button>
        <button class="btn btn-primary" onclick="Config.saveKey(${id || ''})">保存</button>
      </div>
    `;
    overlay.classList.add('active');
  }

  async function saveKey(id) {
    const modelMapping = {};
    document.querySelectorAll('#key-model-mapping .model-mapping-row').forEach(row => {
      const name = row.querySelector('.model-name-input').value.trim();
      const real = row.querySelector('.model-real-input').value.trim();
      if (name && real) modelMapping[name] = real;
    });
    const body = {
      name: document.getElementById('key-name').value,
      provider: document.getElementById('key-provider').value,
      protocol: document.getElementById('key-protocol').value,
      base_url: document.getElementById('key-baseurl').value,
      model_mapping: modelMapping,
      priority: parseInt(document.getElementById('key-priority').value) || 0,
      timeout_ms: parseInt(document.getElementById('key-timeout').value) || 30000,
    };
    const apiKey = document.getElementById('key-apikey').value;
    if (apiKey) body.api_key = apiKey;

    try {
      if (id) {
        await API.put('/api/v1/config/api-keys/' + id, body);
        showToast('Key 已更新');
      } else {
        await API.post('/api/v1/config/api-keys', body);
        showToast('Key 已创建');
      }
      closeModal();
      await loadKeys();
      Chat.loadModels();
    } catch (e) {
      showToast('保存失败: ' + e.message, 'error');
    }
  }

  async function deleteKey(id) {
    if (!confirm('确定删除此 API Key?')) return;
    try {
      await API.del('/api/v1/config/api-keys/' + id);
      showToast('Key 已删除');
      await loadKeys();
      Chat.loadModels();
    } catch (e) {
      showToast('删除失败: ' + e.message, 'error');
    }
  }

  async function toggleKey(id, status) {
    try {
      await API.patch('/api/v1/config/api-keys/' + id + '/status', { status });
      showToast(status === 1 ? '已启用' : '已禁用');
      await loadKeys();
    } catch (e) {
      showToast('操作失败: ' + e.message, 'error');
    }
  }

  function showRuleModal(id) {
    const rule = id ? rules.find(r => r.id === id) : null;
    const overlay = document.getElementById('modal-overlay');
    const keyOptions = keys.map(k =>
      `<option value="${k.id}" ${rule && (rule.target_key_ids||[]).includes(k.id) ? 'selected' : ''}>${escapeHtml(k.name)}</option>`
    ).join('');

    const allModels = [...new Set(keys.flatMap(k => Object.keys(k.model_mapping || {})))];
    const intentModelOptions = allModels.map(m =>
      `<option value="${escapeHtml(m)}" ${rule && rule.intent_model === m ? 'selected' : ''}>${escapeHtml(m)}</option>`
    ).join('');

    overlay.querySelector('.modal').innerHTML = `
      <div class="modal-header">
        <span class="modal-title">${rule ? '编辑' : '添加'}路由规则</span>
        <button class="btn btn-sm btn-icon" onclick="Config.closeModal()">✕</button>
      </div>
      <div class="modal-body">
        <div class="form-group">
          <label>规则名称</label>
          <input id="rule-name" value="${rule ? escapeHtml(rule.rule_name) : ''}" placeholder="DeepSeek 路由">
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>匹配类型</label>
            <select id="rule-match-type" onchange="Config.onMatchTypeChange()">
              <option value="model" ${!rule || rule.match_type === 'model' ? 'selected' : ''}>model (模型名通配)</option>
              <option value="intent" ${rule && rule.match_type === 'intent' ? 'selected' : ''}>intent (意图路由)</option>
            </select>
          </div>
          <div class="form-group">
            <label>匹配模式</label>
            <input id="rule-match-pattern" value="${rule ? escapeHtml(rule.match_pattern) : '*'}" placeholder="deepseek-v4*">
          </div>
        </div>
        <div class="form-group">
          <label>目标 Key (不选则自动使用全部)</label>
          <select id="rule-target-keys" multiple style="min-height:80px">${keyOptions}</select>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>路由策略</label>
            <select id="rule-strategy">
              ${['weight','round_robin','priority','least_conn'].map(s =>
                `<option value="${s}" ${!rule || rule.strategy === s ? 'selected' : ''}>${s}</option>`
              ).join('')}
            </select>
          </div>
          <div class="form-group" id="intent-model-group" style="display:none">
            <label>意图评估模型</label>
            <select id="rule-intent-model">
              <option value="">-- 选择模型 --</option>
              ${intentModelOptions}
            </select>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Fallback</label>
            <select id="rule-fallback">
              <option value="true" ${!rule || rule.fallback_enabled ? 'selected' : ''}>启用</option>
              <option value="false" ${rule && !rule.fallback_enabled ? 'selected' : ''}>禁用</option>
            </select>
          </div>
          <div class="form-group">
            <label>最大 Fallback 次数</label>
            <input id="rule-max-fallback" type="number" value="${rule ? (rule.max_fallback||0) : 2}" min="0">
          </div>
          <div class="form-group">
            <label>优先级</label>
            <input id="rule-priority" type="number" value="${rule ? (rule.priority||0) : 0}" min="0">
          </div>
          <div class="form-group">
            <label>启用</label>
            <select id="rule-enabled">
              <option value="true" ${!rule || rule.enabled ? 'selected' : ''}>启用</option>
              <option value="false" ${rule && !rule.enabled ? 'selected' : ''}>禁用</option>
            </select>
          </div>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn" onclick="Config.closeModal()">取消</button>
        <button class="btn btn-primary" onclick="Config.saveRule(${id || ''})">保存</button>
      </div>
    `;
    overlay.classList.add('active');
    onMatchTypeChange();
  }

  function onMatchTypeChange() {
    const matchType = document.getElementById('rule-match-type').value;
    const intentGroup = document.getElementById('intent-model-group');
    if (intentGroup) intentGroup.style.display = matchType === 'intent' ? '' : 'none';
  }

  async function saveRule(id) {
    const targetKeys = Array.from(document.getElementById('rule-target-keys').selectedOptions).map(o => parseInt(o.value));
    const body = {
      rule_name: document.getElementById('rule-name').value,
      match_type: document.getElementById('rule-match-type').value,
      match_pattern: document.getElementById('rule-match-pattern').value,
      target_key_ids: targetKeys,
      strategy: document.getElementById('rule-strategy').value,
      fallback_enabled: document.getElementById('rule-fallback').value === 'true',
      max_fallback: parseInt(document.getElementById('rule-max-fallback').value) || 0,
      priority: parseInt(document.getElementById('rule-priority').value) || 0,
      enabled: document.getElementById('rule-enabled').value === 'true',
    };
    const intentModel = document.getElementById('rule-intent-model');
    if (intentModel && intentModel.value) body.intent_model = intentModel.value;

    try {
      if (id) {
        await API.put('/api/v1/config/route-rules/' + id, body);
        showToast('规则已更新');
      } else {
        await API.post('/api/v1/config/route-rules', body);
        showToast('规则已创建');
      }
      closeModal();
      await loadRules();
    } catch (e) {
      showToast('保存失败: ' + e.message, 'error');
    }
  }

  async function deleteRule(id) {
    if (!confirm('确定删除此路由规则?')) return;
    try {
      await API.del('/api/v1/config/route-rules/' + id);
      showToast('规则已删除');
      await loadRules();
    } catch (e) {
      showToast('删除失败: ' + e.message, 'error');
    }
  }

  async function toggleRule(id, enabled) {
    try {
      await API.patch('/api/v1/config/route-rules/' + id + '/enabled', { enabled });
      showToast(enabled ? '已启用' : '已禁用');
      await loadRules();
    } catch (e) {
      showToast('操作失败: ' + e.message, 'error');
    }
  }

  function renderIntents() {
    const container = document.getElementById('intent-list');
    if (intents.length === 0) {
      container.innerHTML = '<div class="empty-state">暂无意图配置，点击右上角添加</div>';
      return;
    }
    container.innerHTML = intents.map(i => {
      const targetModelNames = (i.target_models || []).join(', ');
      const weightsStr = i.model_weights && Object.keys(i.model_weights).length > 0
        ? Object.entries(i.model_weights).map(([m, w]) => `${m}:${w}`).join(', ')
        : '<span class="text-muted">继承模型评分</span>';
      const isDefault = i.is_default;
      const itemClass = isDefault ? 'rule-item intent-default' : 'rule-item';
      const inheritBadge = isDefault
        ? '<span class="badge badge-blue ml-8">默认模板</span>'
        : (i.customized
            ? '<span class="badge badge-orange ml-8">已自定义</span>'
            : '<span class="badge badge-gray ml-8">继承默认</span>');
      const deleteBtn = isDefault ? '' : `<button class="btn btn-sm btn-danger" onclick="Config.deleteIntent(${i.id})">删除</button>`;
      const resetBtn = i.customized ? `<button class="btn btn-sm" onclick="Config.resetIntentToDefault(${i.id})">跟随默认</button>` : '';
      return `
        <div class="${itemClass}">
          <div class="rule-header">
            <div>
              <span class="rule-name">${escapeHtml(i.name)} <span class="text-muted">(${escapeHtml(i.label)})</span></span>
              <span class="badge ${i.enabled ? 'badge-green' : 'badge-gray'} ml-8">${i.enabled ? '启用' : '禁用'}</span>
              ${inheritBadge}
            </div>
            <div class="flex gap-8">
              <button class="btn btn-sm" onclick="Config.showIntentModal(${i.id})">编辑</button>
              ${resetBtn}
              ${deleteBtn}
            </div>
          </div>
          <div class="rule-meta">
            ${escapeHtml(i.description || '')}
            <br>目标模型: ${targetModelNames || '<span class="text-muted">自动使用全部（继承 Auto）</span>'}
            <br>模型权重: ${weightsStr}
          </div>
        </div>
      `;
    }).join('');
  }

  function showIntentModal(id) {
    const intent = id ? intents.find(i => i.id === id) : null;
    const overlay = document.getElementById('modal-overlay');
    const selectedModels = intent ? (intent.target_models || []) : [];
    const modelWeights = intent ? (intent.model_weights || {}) : {};

    overlay.querySelector('.modal').innerHTML = `
      <div class="modal-header">
        <span class="modal-title">${intent ? '编辑' : '添加'}意图</span>
        <button class="btn btn-sm btn-icon" onclick="Config.closeModal()">✕</button>
      </div>
      <div class="modal-body">
        ${intent && intent.is_default ? '<div class="form-hint">编辑默认意图路由将同步到所有「继承默认」的意图配置</div>' : ''}
        <div class="form-row">
          <div class="form-group">
            <label>标签 (label)</label>
            <input id="intent-label" value="${intent ? escapeHtml(intent.label) : ''}" placeholder="reasoning" ${intent && intent.is_default ? 'readonly' : ''}>
          </div>
          <div class="form-group">
            <label>名称</label>
            <input id="intent-name" value="${intent ? escapeHtml(intent.name) : ''}" placeholder="推理思考">
          </div>
        </div>
        <div class="form-group">
          <label>描述</label>
          <input id="intent-description" value="${intent ? escapeHtml(intent.description||'') : ''}" placeholder="逻辑分析、数学计算、复杂推理">
        </div>
        <div class="form-group">
          <label>目标模型 (权重 0-100，留空=继承意图评估模型评分)</label>
          <div id="intent-target-list"></div>
          <div id="intent-add-container" style="margin-top:8px"></div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>排序</label>
            <input id="intent-sort-order" type="number" value="${intent ? (intent.sort_order||0) : 0}" min="0">
          </div>
          <div class="form-group">
            <label>启用</label>
            <select id="intent-enabled">
              <option value="true" ${!intent || intent.enabled ? 'selected' : ''}>启用</option>
              <option value="false" ${intent && !intent.enabled ? 'selected' : ''}>禁用</option>
            </select>
          </div>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn" onclick="Config.closeModal()">取消</button>
        <button class="btn btn-primary" onclick="Config.saveIntent(${id || ''})">保存</button>
      </div>
    `;
    overlay.classList.add('active');

    intentTargetModels = [...selectedModels];
    renderIntentTargetList(modelWeights);
  }

  let intentTargetModels = [];

  function renderIntentTargetList(modelWeights) {
    const container = document.getElementById('intent-target-list');
    if (!container) return;
    if (intentTargetModels.length === 0) {
      container.innerHTML = '<div class="text-muted" style="padding:8px 0">未选择目标模型，将继承 Auto Route 使用全部</div>';
    } else {
      container.innerHTML = intentTargetModels.map(modelName => {
        const k = findKeyForModel(modelName);
        const w = modelWeights[modelName] || '';
        const keyLabel = k ? `${escapeHtml(k.name)} (${escapeHtml(k.provider)})` : '<span class="text-muted">未找到所属 Key</span>';
        return `<div class="form-row intent-target-row" data-model-name="${escapeHtml(modelName)}" style="gap:8px;align-items:center">
          <div style="flex:2;min-width:0">
            <div class="text-sm">${escapeHtml(modelName)}</div>
            <div class="text-muted text-sm">${keyLabel}</div>
          </div>
          <input class="intent-weight-input" data-model-name="${escapeHtml(modelName)}" type="number" min="0" max="100" value="${w}" placeholder="留空" style="width:100px">
          <button class="btn btn-sm btn-danger" onclick="Config.removeIntentTarget('${escapeHtml(modelName)}')">✕</button>
        </div>`;
      }).join('');
    }
    renderIntentAddContainer();
  }

  /** 通过 model_mapping 反查模型所属的 Key（前端缓存） */
  function findKeyForModel(modelName) {
    return keys.find(k => {
      const mm = k.model_mapping || {};
      return Object.keys(mm).includes(modelName);
    });
  }

  function renderIntentAddContainer() {
    const container = document.getElementById('intent-add-container');
    if (!container) return;
    // 收集所有 Key 中的模型名，排除已选
    const allModels = [];
    keys.forEach(k => {
      Object.keys(k.model_mapping || {}).forEach(name => {
        if (!intentTargetModels.includes(name)) {
          allModels.push({ name, keyName: k.name, provider: k.provider });
        }
      });
    });
    if (allModels.length === 0) {
      container.innerHTML = '';
      return;
    }
    container.innerHTML = `<select id="intent-add-select" style="width:auto;display:inline-block">
      ${allModels.map(m => `<option value="${escapeHtml(m.name)}">${escapeHtml(m.name)} (${escapeHtml(m.keyName)})</option>`).join('')}
    </select>
    <button class="btn btn-sm" onclick="Config.addIntentTarget()">+ 添加</button>`;
  }

  function addIntentTarget() {
    const sel = document.getElementById('intent-add-select');
    if (!sel) return;
    const modelName = sel.value;
    if (modelName && !intentTargetModels.includes(modelName)) {
      intentTargetModels.push(modelName);
      renderIntentTargetList({});
    }
  }

  function removeIntentTarget(modelName) {
    intentTargetModels = intentTargetModels.filter(n => n !== modelName);
    renderIntentTargetList({});
  }

  async function saveIntent(id) {
    const targetModels = [...intentTargetModels];
    const modelWeights = {};
    document.querySelectorAll('.intent-weight-input').forEach(input => {
      const v = input.value.trim();
      if (v !== '') modelWeights[input.dataset.modelName] = parseInt(v);
    });
    const body = {
      label: document.getElementById('intent-label').value.trim(),
      name: document.getElementById('intent-name').value.trim(),
      description: document.getElementById('intent-description').value.trim(),
      target_models: targetModels,
      model_weights: modelWeights,
      sort_order: parseInt(document.getElementById('intent-sort-order').value) || 0,
      enabled: document.getElementById('intent-enabled').value === 'true',
    };
    if (!body.label || !body.name) { showToast('标签和名称不能为空', 'error'); return; }

    try {
      if (id) {
        await API.put('/api/v1/config/intents/' + id, body);
        showToast('意图已更新');
      } else {
        await API.post('/api/v1/config/intents', body);
        showToast('意图已创建');
      }
      closeModal();
      await loadIntents();
    } catch (e) {
      showToast('保存失败: ' + e.message, 'error');
    }
  }

  async function deleteIntent(id) {
    if (!confirm('确定删除此意图配置?')) return;
    try {
      await API.del('/api/v1/config/intents/' + id);
      showToast('意图已删除');
      await loadIntents();
    } catch (e) {
      showToast('删除失败: ' + e.message, 'error');
    }
  }

  async function resetIntentToDefault(id) {
    if (!confirm('确定重置为跟随默认?')) return;
    try {
      await API.patch('/api/v1/config/intents/' + id + '/reset-to-default', {});
      showToast('已重置为跟随默认');
      await loadIntents();
    } catch (e) {
      showToast('操作失败: ' + e.message, 'error');
    }
  }

  function closeModal() {
    document.getElementById('modal-overlay').classList.remove('active');
  }

  return { init, load, loadKeys, loadRules, loadIntents, showKeyModal, saveKey, deleteKey, toggleKey,
           showRuleModal, saveRule, deleteRule, toggleRule, showIntentModal, saveIntent, deleteIntent,
           resetIntentToDefault, addModelRow, addIntentTarget, removeIntentTarget, closeModal, onMatchTypeChange };
})();
