<template>
  <div class="rt-page">
    <div class="rt-toolbar">
      <el-input
        v-model="keyword"
        placeholder="法规名称 / 条款号 / 正文关键词"
        clearable
        style="width: 300px"
        :prefix-icon="Search"
        @keyup.enter="smartSearch"
        @clear="load"
      />
      <el-select v-model="jurisdiction" placeholder="全部法域" clearable style="width: 130px" @change="load">
        <el-option v-for="j in jurisdictions" :key="j.value" :label="j.label" :value="j.value" />
      </el-select>
      <el-button type="primary" :icon="MagicStick" :loading="searching" @click="smartSearch">智能检索</el-button>
      <div class="rt-toolbar-right">
        <el-button :icon="Refresh" @click="load">重置</el-button>
        <el-button :icon="Plus" @click="openForm()">新增条款</el-button>
      </div>
    </div>

    <div v-if="searchMode" class="search-banner">
      <span class="rt-tag gold">{{ modeLabel(searchMode) }}</span>
      <span class="rt-sm rt-dim">命中 {{ total }} 条，结果按相似度排序</span>
      <el-button size="small" text @click="clearSearch">返回列表</el-button>
    </div>

    <div class="rt-card">
      <el-table :data="articles" size="small" empty-text="暂无条款数据">
        <el-table-column prop="lawName" label="法规" min-width="200" />
        <el-table-column prop="articleNo" label="条款" width="100" />
        <el-table-column prop="title" label="要点" min-width="180" />
        <el-table-column label="法域" width="90">
          <template #default="{ row }">{{ jurisdictionLabel(row.jurisdiction) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="70" />
        <el-table-column label="相似度" width="90">
          <template #default="{ row }">
            <span v-if="row.score !== null && row.score !== undefined" class="rt-score">{{ row.score }}</span>
            <span v-else class="rt-dim">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="view(row)">查看</el-button>
            <el-button size="small" text @click="openForm(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="rt-card-ft">
        <span class="rt-sm rt-dim">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="query.current"
          :page-size="query.size"
          :total="total"
          layout="prev, pager, next"
          small
          @current-change="load"
        />
      </div>
    </div>

    <el-drawer v-model="drawer" size="520px" :title="current.title || '条款详情'">
      <div class="meta">
        <span class="rt-tag gold">{{ current.lawName }}</span>
        <span class="rt-tag">{{ current.articleNo }}</span>
        <span class="rt-tag">{{ jurisdictionLabel(current.jurisdiction) }}</span>
        <span class="rt-tag">{{ current.status }}</span>
      </div>
      <div class="article-content">{{ current.content }}</div>
      <div class="meta-block">
        <div><span class="meta-label">版本</span>{{ current.version || '-' }}</div>
        <div><span class="meta-label">生效日期</span>{{ formatDate(current.effectiveDate) }}</div>
        <div><span class="meta-label">标签</span>{{ current.tags || '-' }}</div>
      </div>
    </el-drawer>

    <el-dialog v-model="formDialog" :title="form.id ? '编辑条款' : '新增条款'" width="640px">
      <el-form :model="form" label-width="86px">
        <el-row :gutter="12">
          <el-col :span="14">
            <el-form-item label="法规名称" required>
              <el-input v-model="form.lawName" placeholder="例如：中华人民共和国个人信息保护法" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="条款号" required>
              <el-input v-model="form.articleNo" placeholder="第十三条" />
            </el-form-item>
          </el-col>
          <el-col :span="14">
            <el-form-item label="要点">
              <el-input v-model="form.title" placeholder="处理个人信息的合法性基础" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="法域">
              <el-select v-model="form.jurisdiction" style="width: 100%">
                <el-option v-for="j in jurisdictions" :key="j.value" :label="j.label" :value="j.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="现行有效" value="现行有效" />
                <el-option label="已修订" value="已修订" />
                <el-option label="已废止" value="已废止" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本">
              <el-input v-model="form.version" placeholder="v1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生效日期">
              <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标签">
              <el-input v-model="form.tags" placeholder="个人信息,告知义务" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="正文" required>
          <el-input v-model="form.content" type="textarea" :rows="8" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { formatDate, jurisdictionLabel, statusType } from '@regtech/shared'
import { createArticle, deleteArticle, fetchArticles, fetchArticle, searchArticles, updateArticle } from '../api'

const articles = ref([])
const total = ref(0)
const keyword = ref('')
const jurisdiction = ref('')
const searching = ref(false)
const searchMode = ref('')
const query = reactive({ current: 1, size: 10 })

const drawer = ref(false)
const current = ref({})

const formDialog = ref(false)
const saving = ref(false)
const form = reactive({
  id: null,
  lawName: '',
  articleNo: '',
  title: '',
  jurisdiction: 'CN',
  status: '现行有效',
  version: 'v1',
  effectiveDate: '',
  tags: '',
  content: ''
})

const jurisdictions = [
  { label: '中国大陆', value: 'CN' },
  { label: '中国香港', value: 'HK' },
  { label: '中国台湾', value: 'TW' }
]

const modeLabel = (mode) => ({ hybrid: '混合检索', vector: '向量检索', keyword: '关键词检索' }[mode] || mode)

const load = async () => {
  searchMode.value = ''
  const data = await fetchArticles({
    keyword: keyword.value,
    jurisdiction: jurisdiction.value,
    current: query.current,
    size: query.size
  })
  articles.value = data.records || []
  total.value = data.total || 0
}

const smartSearch = async () => {
  if (!keyword.value.trim()) {
    await load()
    return
  }
  searching.value = true
  try {
    const data = await searchArticles({
      query: keyword.value,
      jurisdiction: jurisdiction.value || null,
      topK: 20
    })
    articles.value = data.records || []
    total.value = data.total || 0
    searchMode.value = data.mode
  } finally {
    searching.value = false
  }
}

const clearSearch = () => {
  keyword.value = ''
  query.current = 1
  load()
}

const view = async (row) => {
  current.value = await fetchArticle(row.id)
  drawer.value = true
}

const openForm = (row) => {
  Object.assign(form, {
    id: row ? row.id : null,
    lawName: row ? row.lawName : '',
    articleNo: row ? row.articleNo : '',
    title: row ? row.title : '',
    jurisdiction: row ? row.jurisdiction : 'CN',
    status: row ? row.status : '现行有效',
    version: row ? row.version : 'v1',
    effectiveDate: row ? row.effectiveDate : '',
    tags: row ? row.tags : '',
    content: row ? row.content : ''
  })
  formDialog.value = true
}

const save = async () => {
  if (!form.lawName || !form.articleNo || !form.content) {
    ElMessage.warning('请填写法规名称、条款号与正文')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    delete payload.id
    if (form.id) {
      await updateArticle(form.id, payload)
    } else {
      await createArticle(payload)
    }
    ElMessage.success('已保存')
    formDialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确认删除《${row.lawName}》${row.articleNo}？`, '删除条款', { type: 'warning' })
  await deleteArticle(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.search-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.article-content {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.9;
  color: var(--rt-text);
  background: var(--rt-bg);
  border-radius: 8px;
  padding: 14px;
}

.meta-block {
  margin-top: 14px;
  display: grid;
  gap: 8px;
  font-size: 12.5px;
  color: var(--rt-text-2);
}

.meta-label {
  display: inline-block;
  width: 70px;
  color: var(--rt-text-3);
}
</style>
