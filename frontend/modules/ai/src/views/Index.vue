<template>
  <div class="rt-page-fixed">
    <div class="chat-layout">
      <aside class="rt-card side">
        <div class="side-hd">
          <span>会话</span>
          <el-button size="small" text :icon="Plus" @click="newChat">新建</el-button>
        </div>
        <div class="conv-list">
          <div
            v-for="item in conversations"
            :key="item.id"
            class="conv"
            :class="{ active: item.id === conversationId }"
            @click="openConversation(item)"
          >
            <div class="conv-main">
              <div class="conv-title">{{ item.title }}</div>
              <div class="conv-meta">{{ item.model || '默认模型' }} · {{ item.messageCount || 0 }} 条</div>
            </div>
            <el-icon class="conv-del" @click.stop="removeConversation(item)"><Delete /></el-icon>
          </div>
          <div v-if="!conversations.length" class="rt-empty">暂无会话</div>
        </div>
      </aside>

      <section class="rt-card chat">
        <div class="chat-hd">
          <div class="hd-left">
            <span class="hd-title">合规智能助手</span>
            <el-tag size="small" effect="plain" :type="online ? 'success' : 'danger'">
              {{ online ? 'Ollama 在线' : 'Ollama 未连接' }}
            </el-tag>
            <el-select
              v-model="datasetId"
              size="small"
              class="kb-select"
              :disabled="loading"
              placeholder="知识库增强"
            >
              <template #prefix>
                <el-icon><Collection /></el-icon>
              </template>
              <el-option label="不使用知识库" value="" />
              <el-option label="全部知识库检索" value="all" />
              <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
          </div>
          <el-select v-model="model" size="small" style="width: 190px" placeholder="选择模型">
            <el-option v-for="m in models" :key="m.id" :label="m.id" :value="m.id" />
            <el-option v-if="!models.length" :label="defaultModel" :value="defaultModel" />
          </el-select>
        </div>

        <div v-if="!online" class="offline">
          本地 Ollama 未连接。请先执行 <code>ollama serve</code> 并拉取模型
          <code>ollama pull {{ defaultModel }}</code>，然后在
          <code>jv-regtech-ai</code> 的 <code>regtech.ai.ollama.base-url</code> 中确认地址。
        </div>

        <div ref="scrollRef" class="messages">
          <div v-if="!messages.length" class="welcome">
            <div class="welcome-mark">N</div>
            <div class="welcome-title">从哪里开始？</div>
            <div class="welcome-sub">
              可挂载平台知识库做检索增强（RAG），回答自动标注引用资料与相似度；不选知识库则为纯模型对话。
            </div>
            <div class="prompts">
              <div v-for="p in prompts" :key="p" class="prompt" @click="usePrompt(p)">{{ p }}</div>
            </div>
          </div>

          <div v-for="(msg, i) in messages" :key="i" class="msg" :class="msg.role">
            <div class="avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
            <div class="bubble">
              <div class="content">
                {{ msg.content || (msg.role === 'assistant' && loading ? (retrieving ? '正在检索知识库…' : '正在思考…') : '') }}
              </div>

              <div v-if="msg.role === 'assistant' && msg.sources && msg.sources.length" class="sources">
                <div class="sources-hd" @click="msg.open = !msg.open">
                  <el-icon><Document /></el-icon>
                  <span>引用 {{ msg.sources.length }} 条知识库资料</span>
                  <el-icon class="arrow" :class="{ open: msg.open }"><ArrowRight /></el-icon>
                </div>
                <div v-show="msg.open" class="source-list">
                  <div v-for="(s, si) in msg.sources" :key="si" class="source">
                    <div class="source-top">
                      <span class="source-idx">资料{{ si + 1 }}</span>
                      <span class="source-title">{{ s.documentTitle || '知识库文档' }}</span>
                      <span v-if="s.articleNo" class="source-no">{{ s.articleNo }}</span>
                      <span class="source-score">相似度 {{ (s.score || 0).toFixed(3) }}</span>
                    </div>
                    <div class="source-text">{{ truncate(s.content, 180) }}</div>
                  </div>
                </div>
              </div>

              <div v-if="msg.role === 'assistant' && (msg.cost || (msg.sources && msg.sources.length))" class="meta">
                <span v-if="msg.sources && msg.sources.length">知识库增强</span>
                <span v-if="msg.cost">{{ msg.cost }} ms</span>
              </div>
            </div>
          </div>
        </div>

        <div class="composer">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="输入合规问题，Enter 发送，Shift + Enter 换行"
            @keydown.enter="onEnter"
          />
          <div class="composer-right">
            <el-button v-if="loading" size="small" @click="stop">停止</el-button>
            <el-button type="primary" :icon="Promotion" :loading="loading" @click="send">发送</el-button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Collection, Delete, Document, Plus, Promotion } from '@element-plus/icons-vue'
import { truncate } from '@regtech/shared'
import {
  deleteConversation,
  fetchConversations,
  fetchDatasets,
  fetchMessages,
  fetchModels,
  searchKnowledge,
  streamChat
} from '../api'

const BASE_PROMPT = '你是 NCS RegTech 监管合规助手，回答需准确、简洁，涉及条文时请注明法规名称与条款号；不确定时明确说明。'
const DATASET_KEY = 'regtech.ai.datasetId'

const models = ref([])
const defaultModel = ref('gpt-oss:20b')
const online = ref(false)
const model = ref('')

const datasets = ref([])
const datasetId = ref(readDataset())
const retrieving = ref(false)

const conversations = ref([])
const conversationId = ref(null)
const messages = ref([])
const input = ref('')
const loading = ref(false)
const scrollRef = ref(null)
let abortController = null

function readDataset() {
  const saved = localStorage.getItem(DATASET_KEY)
  if (!saved) return ''
  if (saved === 'all') return 'all'
  const id = Number(saved)
  return Number.isFinite(id) ? id : ''
}

const prompts = [
  '金融消费者适当性管理的核心要求是什么？',
  '个人信息处理者在自动化决策前需要履行哪些义务？',
  '香港持牌法团的客户适当性评估有哪些规定？',
  '对比中国大陆与香港在信息披露要求上的差异'
]

const scrollToBottom = async () => {
  await nextTick()
  if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
}

const loadModels = async () => {
  try {
    const data = await fetchModels()
    models.value = data.data || []
    defaultModel.value = data.defaultModel || 'gpt-oss:20b'
    online.value = !!data.online
    if (!model.value) model.value = defaultModel.value
  } catch (e) {
    online.value = false
  }
}

const loadDatasets = async () => {
  try {
    datasets.value = await fetchDatasets()
  } catch (e) {
    datasets.value = []
  }
}

const loadConversations = async () => {
  conversations.value = await fetchConversations()
}

const openConversation = async (item) => {
  conversationId.value = item.id
  const data = await fetchMessages(item.id)
  messages.value = (data || []).map((m) => ({ role: m.role, content: m.content, cost: m.costMs }))
  scrollToBottom()
}

const newChat = () => {
  conversationId.value = null
  messages.value = []
  input.value = ''
}

const removeConversation = async (item) => {
  await ElMessageBox.confirm(`确认删除会话「${item.title}」？`, '删除会话', { type: 'warning' })
  await deleteConversation(item.id)
  if (conversationId.value === item.id) newChat()
  await loadConversations()
}

const usePrompt = (p) => {
  input.value = p
  send()
}

const onEnter = (event) => {
  if (event.shiftKey) return
  event.preventDefault()
  send()
}

/** 按数据集检索知识库（'' 不检索，'all' 全库） */
const retrieve = async (text) => {
  if (!datasetId.value) return []
  retrieving.value = true
  try {
    const data = await searchKnowledge({
      query: text,
      datasetId: datasetId.value === 'all' ? null : Number(datasetId.value),
      topK: 5
    })
    return data.records || []
  } catch (e) {
    ElMessage.warning('知识库检索失败，已降级为纯模型回答')
    return []
  } finally {
    retrieving.value = false
  }
}

/** 把检索到的分块拼进系统提示词（控制总长度，避免超出上下文） */
const buildKbPrompt = (records) => {
  const blocks = []
  let budget = 2400
  records.forEach((item) => {
    const text = String(item.content || '').replace(/\s+/g, ' ').trim()
    if (!text || budget <= 0) return
    const slice = text.slice(0, Math.min(600, budget))
    budget -= slice.length
    const label = item.documentTitle || '知识库文档'
    const no = item.articleNo ? ` · ${item.articleNo}` : ''
    blocks.push(`【资料${blocks.length + 1}】${label}${no}\n${slice}`)
  })
  if (!blocks.length) return BASE_PROMPT
  return `${BASE_PROMPT}\n\n请优先依据下列平台知识库资料回答，引用处标注【资料N】；资料未覆盖的部分可基于通用知识补充，但需说明依据差异，禁止编造条文。\n\n${blocks.join('\n\n')}`
}

const send = async () => {
  const text = input.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  const assistant = { role: 'assistant', content: '', sources: [], open: false }
  messages.value.push(assistant)
  input.value = ''
  loading.value = true
  scrollToBottom()

  // 多轮追问时带上上一句用户问题，提升检索命中率
  const retrieveQuery = messages.value
    .filter((m) => m.role === 'user' && m.content)
    .slice(-2)
    .map((m) => m.content)
    .join(' ')

  const sources = await retrieve(retrieveQuery || text)
  assistant.sources = sources
  if (datasetId.value && !sources.length) {
    ElMessage.info('知识库中未检索到相关内容，本次直接使用模型回答')
  }

  const payload = {
    model: model.value,
    conversationId: conversationId.value,
    scene: 'chat',
    systemPrompt: sources.length ? buildKbPrompt(sources) : BASE_PROMPT,
    messages: messages.value
      .filter((m) => m.role === 'user' || (m.role === 'assistant' && m.content))
      .map((m) => ({ role: m.role, content: m.content }))
  }

  abortController = new AbortController()
  try {
    await streamChat(payload, {
      onMeta: (meta) => {
        conversationId.value = meta.conversationId
      },
      onDelta: (delta) => {
        assistant.content += delta
        scrollToBottom()
      },
      onError: (message) => {
        assistant.content = `调用失败：${message}`
        ElMessage.error(message)
      },
      onDone: () => {}
    }, abortController.signal)
  } catch (e) {
    assistant.content = assistant.content || `调用失败：${e.message}`
    ElMessage.error(e.message || '模型调用失败')
  } finally {
    loading.value = false
    abortController = null
    await loadConversations()
  }
}

const stop = () => {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  loading.value = false
}

watch(datasetId, (value) => {
  localStorage.setItem(DATASET_KEY, value === null || value === undefined ? '' : String(value))
})

onMounted(async () => {
  await Promise.all([loadModels(), loadConversations(), loadDatasets()])
})
</script>

<style scoped>
.chat-layout {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
}

.side {
  width: 236px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.side-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--rt-border-light);
  font-size: 12.5px;
  font-weight: 600;
}

.conv-list {
  flex: 1;
  overflow: auto;
  padding: 8px;
}

.conv {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 7px;
  cursor: pointer;
  margin-bottom: 2px;
}

.conv:hover {
  background: var(--rt-bg);
}

.conv.active {
  background: #F2F5FA;
}

.conv-main {
  min-width: 0;
  flex: 1;
}

.conv-title {
  font-size: 12.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-meta {
  font-size: 10.5px;
  color: var(--rt-text-3);
  margin-top: 2px;
}

.conv-del {
  font-size: 12px;
  color: var(--rt-text-3);
  opacity: 0;
}

.conv:hover .conv-del {
  opacity: 1;
}

.chat {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-hd {
  height: 48px;
  flex-shrink: 0;
  padding: 0 16px;
  border-bottom: 1px solid var(--rt-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hd-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hd-title {
  font-size: 13.5px;
  font-weight: 600;
}

.offline {
  margin: 12px 16px 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #FDF6E9;
  color: #8A6A25;
  font-size: 11.5px;
  line-height: 1.7;
}

.offline code {
  background: rgba(0, 0, 0, 0.05);
  padding: 1px 4px;
  border-radius: 3px;
}

.messages {
  flex: 1;
  overflow: auto;
  padding: 18px 20px;
}

.welcome {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  text-align: center;
}

.welcome-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #C6A15B, #EBD9AE);
  color: #12386B;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 6px;
}

.welcome-title {
  font-size: 16px;
  font-weight: 600;
}

.welcome-sub {
  font-size: 12px;
  color: var(--rt-text-3);
  max-width: 460px;
  line-height: 1.8;
}

.prompts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 18px;
  width: 100%;
  max-width: 620px;
}

.prompt {
  text-align: left;
  padding: 10px 12px;
  border: 1px solid var(--rt-border);
  border-radius: 9px;
  font-size: 12px;
  color: var(--rt-text-2);
  cursor: pointer;
  transition: all 0.16s;
}

.prompt:hover {
  border-color: #C6A15B;
  background: var(--rt-gold-soft);
}

.msg {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}

.msg.user {
  flex-direction: row-reverse;
}

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--rt-navy);
  color: #fff;
  font-size: 11.5px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.msg.user .avatar {
  background: #C6A15B;
  color: #12386B;
}

.bubble {
  max-width: 76%;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--rt-bg);
}

.msg.user .bubble {
  background: #EEF3FA;
}

.content {
  font-size: 13px;
  line-height: 1.85;
  white-space: pre-wrap;
  word-break: break-word;
}

.meta {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  font-size: 10.5px;
  color: var(--rt-text-3);
}

.kb-select {
  width: 178px;
}

.sources {
  margin-top: 8px;
  border: 1px solid var(--rt-border);
  border-radius: 8px;
  background: #FFFFFF;
  overflow: hidden;
}

.sources-hd {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  font-size: 11.5px;
  color: var(--rt-navy);
  background: #F3F8FE;
  cursor: pointer;
  user-select: none;
}

.sources-hd .arrow {
  margin-left: auto;
  font-size: 11px;
  transition: transform 0.16s;
}

.sources-hd .arrow.open {
  transform: rotate(90deg);
}

.source-list {
  padding: 4px 10px 8px;
}

.source {
  padding: 7px 0;
  border-bottom: 1px dashed var(--rt-border-light);
}

.source:last-child {
  border-bottom: none;
}

.source-top {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  line-height: 1.6;
}

.source-idx {
  color: var(--rt-gold);
  font-weight: 600;
}

.source-title {
  color: var(--rt-text);
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-no {
  font-family: 'SFMono-Regular', Consolas, monospace;
  color: var(--rt-text-3);
}

.source-score {
  margin-left: auto;
  color: var(--rt-text-3);
}

.source-text {
  margin-top: 2px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--rt-text-2);
}

.composer {
  flex-shrink: 0;
  border-top: 1px solid var(--rt-border-light);
  padding: 12px 16px;
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.composer :deep(.el-textarea__inner) {
  min-height: 56px !important;
  border-radius: 9px;
}

.composer-right {
  display: flex;
  gap: 6px;
  padding-bottom: 2px;
}
</style>
