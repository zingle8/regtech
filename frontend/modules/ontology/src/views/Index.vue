<template>
  <div class="rt-page-fixed">
    <div class="layout">
      <aside class="rt-card side">
        <div class="side-hd">
          <span>本体结构</span>
          <el-button size="small" text :icon="Plus" @click="openClassForm">新增类</el-button>
        </div>
        <div class="tree-wrap">
          <el-tree
            :data="tree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            @node-click="selectClass"
          >
            <template #default="{ data }">
              <span class="tree-node">
                <span class="tree-name">{{ data.name }}</span>
                <el-icon class="tree-del" @click.stop="removeClass(data)"><Delete /></el-icon>
              </span>
            </template>
          </el-tree>
          <div v-if="!tree.length" class="rt-empty">暂无本体类</div>
        </div>
        <div class="side-foot">
          共 {{ classCount }} 个类 · {{ individuals.length }} 个个体
        </div>
      </aside>

      <section class="main">
        <div class="class-bar">
          <div class="class-name">{{ currentClass ? currentClass.name : '未选择本体类' }}</div>
          <div class="class-desc">{{ currentClass ? currentClass.description || '暂无描述' : '在左侧选择一个本体类查看属性与实例' }}</div>
        </div>

        <el-tabs v-model="tab" class="tabs">
          <el-tab-pane label="属性定义" name="props">
            <div class="rt-toolbar">
              <div class="rt-toolbar-right">
                <el-button size="small" :icon="Plus" :disabled="!currentClass" @click="openPropForm">新增属性</el-button>
              </div>
            </div>
            <el-table :data="properties" size="small" empty-text="该本体类暂无属性">
              <el-table-column prop="code" label="标识" width="160" />
              <el-table-column prop="name" label="名称" width="160" />
              <el-table-column prop="dataType" label="类型" width="100" />
              <el-table-column label="必填" width="80">
                <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="description" label="说明" />
              <el-table-column label="操作" width="80" align="right">
                <template #default="{ row }">
                  <el-button size="small" text type="danger" @click="removeProperty(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="个体实例" name="individuals">
            <div class="rt-toolbar">
              <el-input v-model="individualKeyword" placeholder="搜索个体" clearable style="width: 200px" @keyup.enter="loadIndividuals" @clear="loadIndividuals" />
              <div class="rt-toolbar-right">
                <el-button size="small" :icon="Plus" :disabled="!currentClass" @click="openIndividualForm">新增个体</el-button>
              </div>
            </div>
            <el-table :data="individuals" size="small" empty-text="暂无个体">
              <el-table-column prop="name" label="名称" min-width="180" />
              <el-table-column prop="className" label="所属类" width="140" />
              <el-table-column prop="props" label="属性值" min-width="200" show-overflow-tooltip />
              <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
              <el-table-column label="操作" width="80" align="right">
                <template #default="{ row }">
                  <el-button size="small" text type="danger" @click="removeIndividual(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="本体 RAG" name="rag">
            <div class="rag">
              <el-input
                v-model="question"
                type="textarea"
                :rows="3"
                placeholder="例如：销售理财产品前，金融机构需要对个人客户履行哪些适当性义务？"
              />
              <div class="rag-bar">
                <span class="rt-sm rt-dim">先按本体概念扩展检索词，再把本体结构与命中条文一起交给模型</span>
                <el-button type="primary" :loading="asking" @click="ask">开始推理</el-button>
              </div>

              <div v-if="answer" class="rag-result">
                <div class="answer">{{ answer }}</div>

                <div v-if="hits.length" class="block">
                  <div class="block-title">本体命中</div>
                  <div class="hit-list">
                    <span v-for="h in hits" :key="h.type + h.id" class="hit">
                      <b>{{ h.name }}</b>
                      <span class="hit-type">{{ h.type === 'class' ? '类' : '个体' }}</span>
                    </span>
                  </div>
                </div>

                <div v-if="expansions.length" class="block">
                  <div class="block-title">检索词扩展</div>
                  <div class="expansions">
                    <span v-for="e in expansions" :key="e" class="rt-tag">{{ e }}</span>
                  </div>
                </div>

                <div v-if="sources.length" class="block">
                  <div class="block-title">引用条文</div>
                  <div v-for="s in sources" :key="s.index" class="source">
                    <span class="rt-tag gold">[{{ s.index }}]</span>
                    <div>
                      <div class="source-title">{{ s.title }} {{ s.articleNo || '' }}</div>
                      <div class="source-content">{{ s.content }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>

    <el-dialog v-model="classDialog" title="新增本体类" width="440px">
      <el-form :model="classForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="classForm.name" placeholder="例如：信息披露义务" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="classForm.code" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="父类">
          <el-tree-select
            v-model="classForm.parentId"
            :data="tree"
            :props="{ label: 'name', children: 'children', value: 'id' }"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="classForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classDialog = false">取消</el-button>
        <el-button type="primary" @click="saveClass">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="propDialog" title="新增属性" width="460px">
      <el-form :model="propForm" label-width="80px">
        <el-form-item label="标识" required>
          <el-input v-model="propForm.code" placeholder="disclosure_channel" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="propForm.name" placeholder="披露渠道" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="propForm.dataType" style="width: 100%">
            <el-option label="字符串" value="string" />
            <el-option label="数值" value="number" />
            <el-option label="日期" value="date" />
            <el-option label="布尔" value="boolean" />
            <el-option label="引用" value="ref" />
          </el-select>
        </el-form-item>
        <el-form-item label="必填">
          <el-switch v-model="propForm.required" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="propForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="propDialog = false">取消</el-button>
        <el-button type="primary" @click="saveProperty">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="individualDialog" title="新增个体" width="460px">
      <el-form :model="individualForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="individualForm.name" placeholder="例如：某城商行理财产品页" />
        </el-form-item>
        <el-form-item label="属性 JSON">
          <el-input v-model="individualForm.props" type="textarea" :rows="4" placeholder='{"channel":"APP"}' />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="individualForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="individualDialog = false">取消</el-button>
        <el-button type="primary" @click="saveIndividual">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import {
  askOntology,
  createClass,
  createIndividual,
  createProperty,
  deleteClass,
  deleteIndividual,
  deleteProperty,
  fetchClassTree,
  fetchIndividuals,
  fetchProperties
} from '../api'

const tree = ref([])
const currentClass = ref(null)
const properties = ref([])
const individuals = ref([])
const individualKeyword = ref('')
const tab = ref('props')

const classDialog = ref(false)
const propDialog = ref(false)
const individualDialog = ref(false)
const classForm = reactive({ name: '', code: '', parentId: null, description: '' })
const propForm = reactive({ classId: null, code: '', name: '', dataType: 'string', required: false, description: '' })
const individualForm = reactive({ classId: null, name: '', props: '{}', description: '' })

const question = ref('')
const answer = ref('')
const hits = ref([])
const expansions = ref([])
const sources = ref([])
const asking = ref(false)

const classCount = computed(() => {
  const walk = (list) => list.reduce((sum, item) => sum + 1 + walk(item.children || []), 0)
  return walk(tree.value)
})

const loadTree = async () => {
  tree.value = await fetchClassTree()
}

const loadIndividuals = async () => {
  individuals.value = await fetchIndividuals({
    classId: currentClass.value ? currentClass.value.id : null,
    keyword: individualKeyword.value
  })
}

const selectClass = async (data) => {
  currentClass.value = data
  propForm.classId = data.id
  individualForm.classId = data.id
  properties.value = await fetchProperties(data.id)
  await loadIndividuals()
}

const openClassForm = () => {
  Object.assign(classForm, { name: '', code: '', parentId: null, description: '' })
  classDialog.value = true
}

const saveClass = async () => {
  if (!classForm.name) {
    ElMessage.warning('请填写名称')
    return
  }
  await createClass({ ...classForm })
  ElMessage.success('已创建')
  classDialog.value = false
  await loadTree()
}

const removeClass = async (data) => {
  await ElMessageBox.confirm(`确认删除本体类「${data.name}」？`, '删除本体类', { type: 'warning' })
  await deleteClass(data.id)
  ElMessage.success('已删除')
  if (currentClass.value && currentClass.value.id === data.id) {
    currentClass.value = null
    properties.value = []
    individuals.value = []
  }
  await loadTree()
}

const openPropForm = () => {
  Object.assign(propForm, { classId: currentClass.value.id, code: '', name: '', dataType: 'string', required: false, description: '' })
  propDialog.value = true
}

const saveProperty = async () => {
  if (!propForm.code || !propForm.name) {
    ElMessage.warning('请填写标识与名称')
    return
  }
  await createProperty({ ...propForm })
  ElMessage.success('已保存')
  propDialog.value = false
  properties.value = await fetchProperties(currentClass.value.id)
}

const removeProperty = async (row) => {
  await ElMessageBox.confirm(`确认删除属性「${row.name}」？`, '删除属性', { type: 'warning' })
  await deleteProperty(row.id)
  properties.value = await fetchProperties(currentClass.value.id)
}

const openIndividualForm = () => {
  Object.assign(individualForm, { classId: currentClass.value.id, name: '', props: '{}', description: '' })
  individualDialog.value = true
}

const saveIndividual = async () => {
  if (!individualForm.name) {
    ElMessage.warning('请填写名称')
    return
  }
  await createIndividual({ ...individualForm })
  ElMessage.success('已保存')
  individualDialog.value = false
  await loadIndividuals()
}

const removeIndividual = async (row) => {
  await ElMessageBox.confirm(`确认删除个体「${row.name}」？`, '删除个体', { type: 'warning' })
  await deleteIndividual(row.id)
  await loadIndividuals()
}

const ask = async () => {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  asking.value = true
  answer.value = ''
  hits.value = []
  expansions.value = []
  sources.value = []
  try {
    const data = await askOntology({ question: question.value, topK: 6 })
    answer.value = data.answer
    hits.value = data.ontologyHits || []
    expansions.value = data.expansions || []
    sources.value = data.sources || []
  } finally {
    asking.value = false
  }
}

onMounted(async () => {
  await loadTree()
  if (tree.value.length) {
    const first = tree.value[0]
    await selectClass(first)
  }
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
  width: 268px;
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

.tree-wrap {
  flex: 1;
  overflow: auto;
  padding: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 4px;
  font-size: 12.5px;
}

.tree-del {
  font-size: 11px;
  color: var(--rt-text-3);
  opacity: 0;
}

.tree-node:hover .tree-del {
  opacity: 1;
}

.side-foot {
  padding: 8px 12px;
  border-top: 1px solid var(--rt-border-light);
  font-size: 11px;
  color: var(--rt-text-3);
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.class-bar {
  background: var(--rt-card);
  border: 1px solid var(--rt-border);
  border-radius: var(--rt-radius);
  padding: 12px 16px;
  margin-bottom: 10px;
}

.class-name {
  font-size: 14px;
  font-weight: 600;
}

.class-desc {
  font-size: 11.5px;
  color: var(--rt-text-3);
  margin-top: 3px;
}

.tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--rt-card);
  border: 1px solid var(--rt-border);
  border-radius: var(--rt-radius);
  padding: 4px 14px 12px;
}

.tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.rag-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 10px 0 4px;
}

.rag-result {
  margin-top: 14px;
  border-top: 1px dashed var(--rt-border-light);
  padding-top: 12px;
}

.answer {
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.block {
  margin-top: 16px;
}

.block-title {
  font-size: 11.5px;
  color: var(--rt-text-3);
  margin-bottom: 8px;
}

.hit-list,
.expansions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.hit {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--rt-border);
  border-radius: 6px;
  padding: 4px 9px;
  font-size: 12px;
}

.hit-type {
  font-size: 10px;
  color: var(--rt-text-3);
  background: var(--rt-bg);
  border-radius: 3px;
  padding: 0 4px;
}

.source {
  display: flex;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--rt-border-light);
}

.source-title {
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 3px;
}

.source-content {
  font-size: 11.5px;
  color: var(--rt-text-2);
  line-height: 1.7;
}
</style>
