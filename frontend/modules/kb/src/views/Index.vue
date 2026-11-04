<template>
  <div class="rt-page-fixed">
    <div class="layout">
      <aside class="side">
        <div class="side-hd">
          <span>知识库</span>
          <el-button size="small" text :icon="Plus" @click="openDataset">新建</el-button>
        </div>
        <div class="dataset" :class="{ active: !datasetId }" @click="selectDataset(null)">
          <div class="ds-name">全部知识库</div>
          <div class="ds-meta">{{ totalDocLabel }}</div>
        </div>
        <div
          v-for="item in datasets"
          :key="item.id"
          class="dataset"
          :class="{ active: datasetId === item.id }"
          @click="selectDataset(item.id)"
        >
          <div class="ds-name">{{ item.name }}</div>
          <div class="ds-meta">
            {{ jurisdictionLabel(item.jurisdiction) }} · {{ item.docCount || 0 }} 篇 / {{ item.chunkCount || 0 }} 块
          </div>
        </div>
        <div v-if="!datasets.length" class="rt-empty">暂无知识库</div>
      </aside>

      <section class="main">
        <el-tabs v-model="tab" class="tabs">
          <el-tab-pane label="文档管理" name="docs">
            <div class="rt-toolbar">
              <el-input
                v-model="keyword"
                placeholder="搜索文档标题"
                clearable
                style="width: 240px"
                :prefix-icon="Search"
                @keyup.enter="loadDocuments"
                @clear="loadDocuments"
              />
              <div class="rt-toolbar-right">
                <el-button :icon="Refresh" @click="loadDocuments">刷新</el-button>
                <el-button :icon="MagicStick" :loading="reindexing" @click="reindex">补齐向量</el-button>
                <el-button type="primary" :icon="Upload" @click="openDoc">上传文档</el-button>
              </div>
            </div>

            <div class="rt-card table-card">
              <el-table :data="documents" size="small" height="100%" empty-text="该知识库暂无文档">
                <el-table-column prop="title" label="文档" min-width="220">
                  <template #default="{ row }">
                    <span class="doc-link" @click="viewDoc(row)">{{ row.title }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="datasetName" label="知识库" min-width="150" />
                <el-table-column label="法域" width="90">
                  <template #default="{ row }">{{ jurisdictionLabel(row.jurisdiction) }}</template>
                </el-table-column>
                <el-table-column prop="chunkCount" label="分块" width="70" />
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" effect="plain" :type="statusType(row.status)">{{ row.status }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="生效日期" width="110">
                  <template #default="{ row }">{{ formatDate(row.effectiveDate) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="110" align="right">
                  <template #default="{ row }">
                    <el-button size="small" text type="primary" @click="viewDoc(row)">查看</el-button>
                    <el-button size="small" text type="danger" @click="removeDoc(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="rt-card-ft">
                <span class="rt-sm rt-dim">共 {{ docTotal }} 篇文档</span>
                <el-pagination
                  v-model:current-page="docQuery.current"
                  :page-size="docQuery.size"
                  :total="docTotal"
                  layout="prev, pager, next"
                  small
                  @current-change="loadDocuments"
                />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="语义检索" name="search">
            <div class="rt-card search-card">
              <div class="search-bar">
                <el-input
                  v-model="query"
                  placeholder="输入问题或条文关键词，例如：自动化决策的告知义务"
                  size="large"
                  clearable
                  @keyup.enter="runSearch"
                >
                  <template #prefix><el-icon><Search /></el-icon></template>
                </el-input>
                <el-select v-model="mode" size="large" style="width: 136px">
                  <el-option label="向量检索" value="vector" />
                  <el-option label="关键词" value="keyword" />
                </el-select>
                <el-button type="primary" size="large" :loading="searching" @click="runSearch">检索</el-button>
              </div>
              <div class="search-hint">
                <span v-if="searchMode" class="rt-tag gold">{{ modeLabel(searchMode) }}</span>
                <span class="rt-sm rt-dim">向量检索依赖本地 Ollama 的 nomic-embed-text；不可用时自动降级为关键词。</span>
              </div>

              <div v-if="results.length" class="results">
                <div v-for="(item, i) in results" :key="item.id" class="rt-result">
                  <div class="rt-result-hd">
                    <span class="num">{{ i + 1 }}</span>
                    <span class="rt-result-title">{{ item.documentTitle || '未命名文档' }}</span>
                    <span v-if="item.articleNo" class="rt-tag">{{ item.articleNo }}</span>
                    <span v-if="item.score !== null" class="rt-score">相似度 {{ item.score }}</span>
                  </div>
                  <div class="rt-result-body">{{ item.content }}</div>
                </div>
              </div>
              <div v-else class="rt-empty">
                {{ searched ? '未检索到相关内容' : '输入问题开始检索，结果可直接用于合规核查' }}
              </div>
            </div>

            <div class="rt-card rag-card">
              <div class="rt-card-hd">
                <span>RAG 溯源问答</span>
                <span class="rt-sm rt-dim">检索条文 → 本地模型生成 → 返回引用</span>
              </div>
              <div class="rt-card-bd">
                <el-input
                  v-model="question"
                  type="textarea"
                  :rows="3"
                  placeholder="例如：银行在向客户销售理财产品前需要履行哪些适当性义务？"
                />
                <div class="rag-actions">
                  <el-button type="primary" :loading="asking" @click="ask">生成回答</el-button>
                </div>
                <div v-if="answer" class="answer">
                  <div class="answer-hd">回答</div>
                  <div class="answer-body">{{ answer }}</div>
                  <div v-if="sources.length" class="sources">
                    <div class="sources-title">引用条文</div>
                    <div v-for="s in sources" :key="s.index" class="source">
                      <span class="rt-tag gold">[{{ s.index }}]</span>
                      <span class="source-title">{{ s.title }} {{ s.articleNo || '' }}</span>
                      <span class="rt-score" v-if="s.score !== null">相似度 {{ s.score }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>

    <!-- 新建知识库 -->
    <el-dialog v-model="datasetDialog" title="新建知识库" width="440px">
      <el-form :model="datasetForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="datasetForm.name" placeholder="例如：中国金融监管法规库" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="datasetForm.code" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="法域">
          <el-select v-model="datasetForm.jurisdiction" style="width: 100%">
            <el-option v-for="j in jurisdictions" :key="j.value" :label="j.label" :value="j.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="datasetForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="datasetDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDataset">创建</el-button>
      </template>
    </el-dialog>

    <!-- 上传文档 -->
    <el-dialog v-model="docDialog" title="上传法规文档" width="720px" top="6vh">
      <el-form :model="docForm" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="标题" required>
              <el-input v-model="docForm.title" placeholder="例如：中华人民共和国个人信息保护法" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="知识库">
              <el-select v-model="docForm.datasetId" style="width: 100%" placeholder="选择知识库">
                <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="法域">
              <el-select v-model="docForm.jurisdiction" style="width: 100%">
                <el-option v-for="j in jurisdictions" :key="j.value" :label="j.label" :value="j.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生效日期">
              <el-date-picker
                v-model="docForm.effectiveDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="正文" required>
          <el-input
            v-model="docForm.content"
            type="textarea"
            :rows="14"
            placeholder="粘贴法规原文，按段落切分后自动向量化入库（每块约 600 字）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="rt-sm rt-dim dialog-tip">入库时会调用本地 Ollama 生成向量，耗时取决于文档长度</span>
        <el-button @click="docDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDoc">开始入库</el-button>
      </template>
    </el-dialog>

    <!-- 文档详情 -->
    <el-drawer v-model="docDrawer" size="560px" :title="currentDoc.title || '文档详情'">
      <div class="drawer-meta">
        <span class="rt-tag">{{ currentDoc.datasetName || '未归类' }}</span>
        <span class="rt-tag">{{ jurisdictionLabel(currentDoc.jurisdiction) }}</span>
        <span class="rt-tag">{{ currentDoc.chunkCount || 0 }} 个分块</span>
      </div>
      <pre class="doc-content">{{ currentDoc.content }}</pre>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { formatDate, jurisdictionLabel, statusType } from '@regtech/shared'
import {
  askWithKnowledge,
  createDataset,
  createDocument,
  deleteDocument,
  fetchDatasets,
  fetchDocument,
  fetchDocuments,
  reindexKnowledge,
  searchKnowledge
} from '../api'

const datasets = ref([])
const datasetId = ref(null)
const tab = ref('docs')

const documents = ref([])
const docTotal = ref(0)
const keyword = ref('')
const docQuery = reactive({ current: 1, size: 10 })

const datasetDialog = ref(false)
const docDialog = ref(false)
const docDrawer = ref(false)
const saving = ref(false)
const datasetForm = reactive({ name: '', code: '', jurisdiction: 'CN', description: '' })
const docForm = reactive({
  title: '',
  datasetId: null,
  jurisdiction: 'CN',
  effectiveDate: '',
  content: '',
  sourceType: 'TEXT',
  status: '现行有效'
})
const currentDoc = ref({})

const query = ref('')
const mode = ref('vector')
const results = ref([])
const searching = ref(false)
const searched = ref(false)
const searchMode = ref('')

const question = ref('')
const answer = ref('')
const sources = ref([])
const asking = ref(false)

const jurisdictions = [
  { label: '中国大陆', value: 'CN' },
  { label: '中国香港', value: 'HK' },
  { label: '中国台湾', value: 'TW' }
]

const totalDocLabel = computed(() => {
  const total = datasets.value.reduce((sum, item) => sum + (item.docCount || 0), 0)
  return `${total} 篇文档`
})

const modeLabel = (value) => ({ vector: '向量检索', keyword: '关键词检索', 'keyword-fallback': '向量不可用 · 已降级' }[value] || value)

const loadDatasets = async () => {
  datasets.value = await fetchDatasets()
}

const loadDocuments = async () => {
  const data = await fetchDocuments({
    datasetId: datasetId.value,
    keyword: keyword.value,
    current: docQuery.current,
    size: docQuery.size
  })
  documents.value = data.records || []
  docTotal.value = data.total || 0
}

const selectDataset = (id) => {
  datasetId.value = id
  docQuery.current = 1
  loadDocuments()
}

const openDataset = () => {
  Object.assign(datasetForm, { name: '', code: '', jurisdiction: 'CN', description: '' })
  datasetDialog.value = true
}

const saveDataset = async () => {
  if (!datasetForm.name) {
    ElMessage.warning('请填写知识库名称')
    return
  }
  saving.value = true
  try {
    await createDataset({ ...datasetForm })
    ElMessage.success('知识库已创建')
    datasetDialog.value = false
    await loadDatasets()
  } finally {
    saving.value = false
  }
}

const openDoc = () => {
  Object.assign(docForm, {
    title: '',
    datasetId: datasetId.value,
    jurisdiction: 'CN',
    effectiveDate: '',
    content: '',
    sourceType: 'TEXT',
    status: '现行有效'
  })
  docDialog.value = true
}

const saveDoc = async () => {
  if (!docForm.title || !docForm.content) {
    ElMessage.warning('请填写标题与正文')
    return
  }
  saving.value = true
  try {
    const data = await createDocument({ ...docForm })
    ElMessage.success(`入库完成：${data.chunks} 个分块${data.embedded ? '，已生成向量' : '（向量未生成）'}`)
    docDialog.value = false
    await Promise.all([loadDatasets(), loadDocuments()])
  } finally {
    saving.value = false
  }
}

const viewDoc = async (row) => {
  currentDoc.value = await fetchDocument(row.id)
  docDrawer.value = true
}

const removeDoc = async (row) => {
  await ElMessageBox.confirm(`确认删除《${row.title}》及其全部分块？`, '删除文档', { type: 'warning' })
  await deleteDocument(row.id)
  ElMessage.success('已删除')
  await Promise.all([loadDatasets(), loadDocuments()])
}

const reindexing = ref(false)

const reindex = async () => {
  await ElMessageBox.confirm(
    '将为所有缺少向量的分块与条款重新生成向量，耗时取决于数据量与本地模型，是否继续？',
    '补齐向量',
    { type: 'info' }
  )
  reindexing.value = true
  try {
    const data = await reindexKnowledge()
    ElMessage.success(`完成：分块 ${data.chunks} 条、条款 ${data.articles} 条已补齐向量`)
  } finally {
    reindexing.value = false
  }
}

const runSearch = async () => {
  if (!query.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }
  searching.value = true
  try {
    const data = await searchKnowledge({
      query: query.value,
      datasetId: datasetId.value,
      topK: 8,
      mode: mode.value
    })
    results.value = data.records || []
    searchMode.value = data.mode
    searched.value = true
  } finally {
    searching.value = false
  }
}

const ask = async () => {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  asking.value = true
  answer.value = ''
  sources.value = []
  try {
    const data = await askWithKnowledge({
      question: question.value,
      datasetId: datasetId.value,
      topK: 6
    })
    answer.value = data.answer
    sources.value = data.sources || []
  } finally {
    asking.value = false
  }
}

onMounted(async () => {
  await loadDatasets()
  await loadDocuments()
})
</script>

<style scoped>
.layout {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
}

.side {
  width: 232px;
  flex-shrink: 0;
  background: var(--rt-card);
  border: 1px solid var(--rt-border);
  border-radius: var(--rt-radius);
  padding: 10px;
  overflow: auto;
}

.side-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 6px 10px;
  font-size: 12.5px;
  font-weight: 600;
}

.dataset {
  padding: 9px 10px;
  border-radius: 7px;
  cursor: pointer;
  margin-bottom: 2px;
  border: 1px solid transparent;
}

.dataset:hover {
  background: var(--rt-bg);
}

.dataset.active {
  background: #F2F5FA;
  border-color: #DCE3EC;
}

.ds-name {
  font-size: 12.5px;
  font-weight: 500;
}

.ds-meta {
  font-size: 11px;
  color: var(--rt-text-3);
  margin-top: 3px;
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.tabs :deep(.el-tab-pane) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.table-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-card :deep(.el-table) {
  flex: 1;
}

.doc-link {
  cursor: pointer;
  color: var(--rt-navy);
  font-weight: 500;
}

.doc-link:hover {
  color: #2A5AA6;
  text-decoration: underline;
}

.search-card {
  padding: 18px;
}

.search-bar {
  display: flex;
  gap: 8px;
}

.search-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.results {
  margin-top: 16px;
}

.num {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  background: var(--rt-navy);
  color: #fff;
  font-size: 10.5px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.rag-card {
  margin-top: 12px;
}

.rag-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.answer {
  margin-top: 14px;
  border-top: 1px dashed var(--rt-border-light);
  padding-top: 12px;
}

.answer-hd {
  font-size: 12px;
  color: var(--rt-text-3);
  margin-bottom: 6px;
}

.answer-body {
  font-size: 13px;
  line-height: 1.85;
  white-space: pre-wrap;
}

.sources {
  margin-top: 12px;
}

.sources-title {
  font-size: 11.5px;
  color: var(--rt-text-3);
  margin-bottom: 6px;
}

.source {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--rt-border-light);
}

.source-title {
  font-size: 12px;
  color: var(--rt-text-2);
}

.dialog-tip {
  float: left;
  line-height: 32px;
}

.drawer-meta {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
}

.doc-content {
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--rt-text-2);
  background: var(--rt-bg);
  padding: 14px;
  border-radius: 8px;
}
</style>
