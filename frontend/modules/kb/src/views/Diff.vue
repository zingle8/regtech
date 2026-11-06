<template>
  <div class="rt-page">
    <div class="rt-toolbar">
      <el-radio-group v-model="mode" size="small" @change="reset">
        <el-radio-button value="region">多地域对比</el-radio-button>
        <el-radio-button value="scenario">多场景对比</el-radio-button>
      </el-radio-group>

      <el-input
        v-if="mode === 'region'"
        v-model="scene"
        placeholder="法律场景 / 主题，如：投资者适当性"
        clearable
        style="width: 280px"
        @keyup.enter="run"
      />
      <el-select
        v-else
        v-model="scenarios"
        multiple
        filterable
        allow-create
        default-first-option
        placeholder="选择或输入多个法律场景"
        style="width: 320px"
      >
        <el-option v-for="item in scenePresets" :key="item" :label="item" :value="item" />
      </el-select>

      <el-select v-model="selected" multiple collapse-tags placeholder="全部法域" style="width: 220px">
        <el-option
          v-for="item in jurisdictionOptions"
          :key="item.value"
          :label="`${item.label}（${item.total}）`"
          :value="item.value"
        />
      </el-select>

      <el-select v-model="topK" style="width: 120px">
        <el-option :value="1" label="每域 1 条" />
        <el-option :value="2" label="每域 2 条" />
        <el-option :value="3" label="每域 3 条" />
      </el-select>

      <el-switch v-model="explain" active-text="AI 解读" />

      <div class="rt-toolbar-right">
        <el-button :icon="Clock" @click="openHistory">对比历史</el-button>
        <el-button :disabled="picks.length !== 2" :loading="diffLoading" @click="pairDiff">逐条精读</el-button>
        <el-button type="primary" :icon="MagicStick" :loading="loading" @click="run">开始对比</el-button>
      </div>
    </div>

    <div v-if="explainText" class="rt-card">
      <div class="rt-card-hd">
        <span>AI 对比解读</span>
        <span class="rt-sm rt-dim">基于召回的条文与相似度生成，仅作辅助参考</span>
      </div>
      <div class="rt-card-bd explain-body">{{ explainText }}</div>
    </div>

    <div v-if="!result" class="rt-card welcome-card">
      <div class="welcome-title">按场景对齐各法域条文</div>
      <div class="welcome-sub">
        选择场景与法域后，系统用地向量在条文库中按法域召回最相关条文，
        给出原文、跨地域语义相似度，以及每条条文在知识库中的数据集与文档出处。
      </div>
    </div>

    <template v-else>
      <!-- 多地域：横向卡片对照 -->
      <div v-if="mode === 'region'" class="compare-grid">
        <div v-for="cell in regionCells" :key="cell.jurisdiction" class="rt-card cmp-card">
          <div class="cmp-hd">
            <span class="cmp-jur">{{ jurisdictionLabel(cell.jurisdiction) }}</span>
            <el-tag v-if="cell.score !== null && cell.score !== undefined" size="small" effect="plain">
              相似 {{ (cell.score * 100).toFixed(1) }}%
            </el-tag>
            <el-checkbox
              v-if="cell.found"
              class="cmp-pick"
              :model-value="picks.includes(cell.jurisdiction)"
              @change="togglePick(cell.jurisdiction)"
            >
              精读
            </el-checkbox>
          </div>

          <template v-if="cell.found">
            <div class="cmp-law" :title="cell.article.lawName">{{ cell.article.lawName }}</div>
            <div class="cmp-meta">
              <span class="rt-tag gold">{{ cell.article.articleNo }}</span>
              <span class="cmp-title">{{ cell.article.title }}</span>
            </div>
            <div class="cmp-text">{{ truncate(cell.article.content, 220) }}</div>
            <div class="cmp-cite" @click="open(cell)">
              <el-icon><Link /></el-icon>
              <span>{{ cell.article.citation?.citation || '未关联出处' }}</span>
            </div>
            <div v-if="cell.alternatives && cell.alternatives.length" class="cmp-alt">
              <span class="alt-label">备选</span>
              <span
                v-for="alt in cell.alternatives"
                :key="alt.id"
                class="alt-item"
                @click="open({ jurisdiction: cell.jurisdiction, found: true, article: alt, alternatives: [] })"
              >
                {{ alt.articleNo }}{{ alt.title ? ` · ${alt.title}` : '' }}
              </span>
            </div>
          </template>
          <div v-else class="cmp-empty">该法域未检索到相关条文</div>
        </div>
      </div>

      <!-- 跨地域相似度 -->
      <div v-if="mode === 'region'" class="rt-card">
        <div class="rt-card-hd">
          <span>跨地域相似度</span>
          <span class="rt-sm rt-dim">{{ currentRow?.name }} · 覆盖 {{ currentRow?.coverage }} 个法域</span>
        </div>
        <el-table :data="currentRow?.pairs || []" size="small" empty-text="至少两个法域命中条文才能计算相似度">
          <el-table-column prop="fromLabel" label="条文 A" min-width="230" />
          <el-table-column prop="toLabel" label="条文 B" min-width="230" />
          <el-table-column label="语义相似度" width="210">
            <template #default="{ row }">
              <div class="sim-cell">
                <div class="sim-bar">
                  <span :class="levelClass(row.similarity)" :style="{ width: pct(row.similarity) }"></span>
                </div>
                <span class="sim-num">{{ row.similarity === null ? '-' : `${(row.similarity * 100).toFixed(1)}%` }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="level" label="结论" width="110" />
        </el-table>
      </div>

      <!-- 多场景：场景 × 法域矩阵 -->
      <div v-else class="rt-card">
        <el-table :data="rows" size="small" border empty-text="暂无对比结果">
          <el-table-column label="法律场景" width="170" fixed>
            <template #default="{ row }">
              <div class="scene-name">{{ row.name }}</div>
              <div class="scene-cov">覆盖 {{ row.coverage }}</div>
            </template>
          </el-table-column>
          <el-table-column
            v-for="code in activeColumns"
            :key="code"
            :label="jurisdictionLabel(code)"
            min-width="240"
          >
            <template #default="{ row }">
              <div v-if="cellOf(row, code) && cellOf(row, code).found" class="mx-cell" @click="open(cellOf(row, code))">
                <div class="mx-law">{{ cellOf(row, code).article.lawName }}</div>
                <div class="mx-meta">
                  <span class="rt-tag gold">{{ cellOf(row, code).article.articleNo }}</span>
                  <span class="mx-score">{{ (cellOf(row, code).score * 100).toFixed(1) }}%</span>
                </div>
                <div class="mx-title">{{ cellOf(row, code).article.title || '—' }}</div>
              </div>
              <div v-else class="mx-empty">未检索到相关条文</div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <el-drawer v-model="drawer" size="600px" title="条文原文与知识库出处">
      <template v-if="active && active.article">
        <div class="dt-head">
          <el-tag size="small" effect="plain">{{ jurisdictionLabel(active.article.jurisdiction) }}</el-tag>
          <el-tag size="small" effect="plain">{{ active.article.status }}</el-tag>
          <el-tag size="small" effect="plain">{{ active.article.version }}</el-tag>
          <el-tag size="small" effect="plain">生效 {{ formatDate(active.article.effectiveDate) }}</el-tag>
          <el-tag v-if="active.score !== null && active.score !== undefined" size="small" type="success" effect="plain">
            召回相似度 {{ (active.score * 100).toFixed(1) }}%
          </el-tag>
        </div>

        <div class="dt-law">《{{ active.article.lawName }}》{{ active.article.articleNo }}</div>
        <div class="dt-title">{{ active.article.title }}</div>
        <div class="dt-content">{{ active.article.content }}</div>

        <div class="dt-block">
          <div class="dt-block-hd">引用出处</div>
          <div class="dt-cite">{{ active.article.citation?.citation || '—' }}</div>
          <div v-if="active.article.citation?.datasetName" class="dt-row">
            <span class="dt-label">知识库</span>
            <span>{{ active.article.citation.datasetName }}</span>
            <span class="rt-dim">{{ active.article.citation.datasetCode }}</span>
          </div>
          <div v-if="active.article.citation?.documentTitle" class="dt-row">
            <span class="dt-label">文档</span>
            <span>#{{ active.article.citation.documentId }} 《{{ active.article.citation.documentTitle }}》</span>
            <span class="rt-dim">{{ active.article.citation.sourceType }}</span>
          </div>
          <div v-if="active.article.citation?.documentCreateTime" class="dt-row">
            <span class="dt-label">入库时间</span>
            <span>{{ formatDate(active.article.citation.documentCreateTime, true) }}</span>
          </div>
          <div class="dt-row">
            <span class="dt-label">来源渠道</span>
            <span>{{ active.article.citation?.channel || '条文库' }}</span>
          </div>
          <div class="dt-explain">{{ active.article.citation?.explain }}</div>
        </div>

        <div v-if="active.article.tags" class="dt-block">
          <div class="dt-block-hd">标签</div>
          <div class="dt-tags">
            <span v-for="tag in active.article.tags.split(',')" :key="tag" class="rt-tag">{{ tag }}</span>
          </div>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="diffDrawer" size="760px" title="逐条精读对比">
      <template v-if="diff">
        <div class="diff-head">
          <div class="diff-side">
            <div class="diff-name">《{{ diff.left.lawName }}》{{ diff.left.articleNo }}</div>
            <div class="diff-cite">{{ diff.left.citation?.citation }}</div>
          </div>
          <div class="diff-score">
            <div class="diff-num">{{ (diff.similarity * 100).toFixed(1) }}%</div>
            <div class="rt-sm rt-dim">逐行重合 · {{ diff.changeType }}</div>
          </div>
          <div class="diff-side right">
            <div class="diff-name">《{{ diff.right.lawName }}》{{ diff.right.articleNo }}</div>
            <div class="diff-cite">{{ diff.right.citation?.citation }}</div>
          </div>
        </div>

        <div class="diff-blocks">
          <div v-for="(block, index) in diff.blocks" :key="index" class="diff-line" :class="block.type">
            <span class="ln">{{ block.leftLine ?? '' }}</span>
            <span class="ln">{{ block.rightLine ?? '' }}</span>
            <span class="txt">{{ block.text }}</span>
          </div>
        </div>
        <div class="rt-sm rt-dim diff-note">{{ diff.citationNote }}</div>
      </template>
    </el-drawer>

    <el-drawer v-model="historyDrawer" size="740px" title="对比历史">
      <el-table v-loading="recordsLoading" :data="records" size="small" empty-text="暂无对比记录，多地域模式下逐条精读后会自动留痕">
        <el-table-column prop="leftTitle" label="基准条文" min-width="180" show-overflow-tooltip />
        <el-table-column prop="rightTitle" label="对比条文" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="96">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.changeType === '无变化' ? 'success' : 'warning'">
              {{ row.changeType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重合度" width="84">
          <template #default="{ row }">{{ (Number(row.similarity) * 100).toFixed(1) }}%</template>
        </el-table-column>
        <el-table-column label="时间" width="136">
          <template #default="{ row }">{{ formatDate(row.createTime, true) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="viewRecord(row)">查看</el-button>
            <el-button size="small" text type="danger" @click="removeRecord(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="history-ft">
        <span class="rt-sm rt-dim">共 {{ recordTotal }} 条</span>
        <el-pagination
          v-model:current-page="recordQuery.current"
          :page-size="recordQuery.size"
          :total="recordTotal"
          layout="prev, pager, next"
          small
          @current-change="loadRecords"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, Link, MagicStick } from '@element-plus/icons-vue'
import { formatDate, jurisdictionLabel, truncate } from '@regtech/shared'
import {
  compareDiff,
  compareMatrix,
  deleteDiffRecord,
  fetchDiffJurisdictions,
  fetchDiffRecord,
  fetchDiffRecords
} from '../api'

const scenePresets = ['投资者适当性', '信息披露', '个人信息保护', '自动化决策', '数据安全义务', '广告与招揽', '内幕交易']

const mode = ref('region')
const scene = ref('投资者适当性')
const scenarios = ref(['投资者适当性', '信息披露'])
const jurisdictions = ref([])
const selected = ref([])
const topK = ref(2)
const explain = ref(false)

const loading = ref(false)
const result = ref(null)
const explainText = ref('')

const drawer = ref(false)
const active = ref(null)
const picks = ref([])

const diffDrawer = ref(false)
const diff = ref(null)
const diffLoading = ref(false)

const historyDrawer = ref(false)
const records = ref([])
const recordTotal = ref(0)
const recordQuery = reactive({ current: 1, size: 10 })
const recordsLoading = ref(false)

const jurisdictionOptions = computed(() =>
  jurisdictions.value.map((item) => ({
    value: item.jurisdiction,
    label: jurisdictionLabel(item.jurisdiction),
    total: item.total
  }))
)

const rows = computed(() => result.value?.scenarios || [])
const currentRow = computed(() => rows.value[0] || null)
const regionCells = computed(() => (mode.value === 'region' ? currentRow.value?.cells || [] : []))
const activeColumns = computed(() =>
  selected.value.length ? selected.value : jurisdictionOptions.value.map((item) => item.value)
)

const cellOf = (row, code) => (row.cells || []).find((item) => item.jurisdiction === code)

const pct = (score) => {
  const value = Number(score)
  return `${Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0)) * 100}%`
}

const levelClass = (score) => {
  if (score === null || score === undefined) return 'gray'
  if (score >= 0.85) return 'high'
  if (score >= 0.7) return 'mid'
  if (score >= 0.55) return 'low'
  return 'gray'
}

const loadJurisdictions = async () => {
  try {
    jurisdictions.value = await fetchDiffJurisdictions()
  } catch (e) {
    jurisdictions.value = []
  }
}

const reset = () => {
  result.value = null
  explainText.value = ''
  picks.value = []
}

const run = async () => {
  const list = mode.value === 'region' ? [scene.value.trim()].filter(Boolean) : [...scenarios.value]
  if (!list.length) {
    ElMessage.warning('请先填写需要对比的法律场景')
    return
  }
  if (selected.value.length === 1) {
    ElMessage.warning('至少选择两个法域才能对比')
    return
  }
  loading.value = true
  result.value = null
  explainText.value = ''
  picks.value = []
  try {
    const data = await compareMatrix({
      jurisdictions: selected.value,
      scenarios: list,
      topK: topK.value,
      explain: explain.value
    })
    result.value = data
    explainText.value = data.explain || ''
    const empty = (data.scenarios || []).every((row) => (row.missing || []).length === (data.scenarios || []).length)
    if (empty) {
      ElMessage.info('各法域都没有检索到相关条文，试试更换场景或先补充条文')
    }
  } catch (e) {
    ElMessage.error(e.message || '对比失败')
  } finally {
    loading.value = false
  }
}

const open = (cell) => {
  if (!cell || !cell.found || !cell.article) return
  active.value = cell
  drawer.value = true
}

const togglePick = (code) => {
  const index = picks.value.indexOf(code)
  if (index >= 0) {
    picks.value.splice(index, 1)
    return
  }
  if (picks.value.length >= 2) {
    ElMessage.warning('最多选择两个法域做逐条精读')
    return
  }
  picks.value.push(code)
}

const pairDiff = async () => {
  if (picks.value.length !== 2) {
    ElMessage.warning('请先在两张对比卡片上勾选「精读」')
    return
  }
  const cells = picks.value.map((code) => regionCells.value.find((item) => item.jurisdiction === code))
  if (cells.some((cell) => !cell || !cell.found)) {
    ElMessage.warning('所选法域缺少可对比的条文')
    return
  }
  diffLoading.value = true
  try {
    diff.value = await compareDiff({ leftId: cells[0].article.id, rightId: cells[1].article.id })
    diffDrawer.value = true
  } catch (e) {
    ElMessage.error(e.message || '逐条精读失败')
  } finally {
    diffLoading.value = false
  }
}

const loadRecords = async () => {
  recordsLoading.value = true
  try {
    const data = await fetchDiffRecords({ ...recordQuery })
    records.value = data.records || []
    recordTotal.value = data.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载对比历史失败')
  } finally {
    recordsLoading.value = false
  }
}

const openHistory = () => {
  historyDrawer.value = true
  loadRecords()
}

const viewRecord = async (row) => {
  const record = await fetchDiffRecord(row.id)
  let blocks = []
  try {
    blocks = JSON.parse(record.diffJson || '[]')
  } catch (e) {
    blocks = []
  }
  diff.value = {
    similarity: Number(record.similarity),
    changeType: record.changeType,
    blocks,
    left: { lawName: record.leftTitle, articleNo: '' },
    right: { lawName: record.rightTitle, articleNo: '' }
  }
  historyDrawer.value = false
  diffDrawer.value = true
}

const removeRecord = async (row) => {
  await ElMessageBox.confirm('确认删除该对比记录？', '删除记录', { type: 'warning' })
  await deleteDiffRecord(row.id)
  ElMessage.success('已删除')
  await loadRecords()
}

onMounted(loadJurisdictions)
</script>

<style scoped>
.explain-body {
  font-size: 12.5px;
  line-height: 1.9;
  white-space: pre-wrap;
  color: var(--rt-text-2);
}

.welcome-card {
  padding: 40px 24px;
  text-align: center;
}

.welcome-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 8px;
}

.welcome-sub {
  font-size: 12px;
  line-height: 1.9;
  color: var(--rt-text-3);
  max-width: 620px;
  margin: 0 auto;
}

.compare-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}

.cmp-card {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cmp-hd {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cmp-jur {
  font-size: 13px;
  font-weight: 600;
  color: var(--rt-navy);
}

.cmp-pick {
  margin-left: auto;
  height: 20px;
}

.cmp-law {
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cmp-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.cmp-title {
  font-size: 11.5px;
  color: var(--rt-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cmp-text {
  font-size: 12px;
  line-height: 1.85;
  color: var(--rt-text-2);
  max-height: 118px;
  overflow: hidden;
}

.cmp-cite {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  padding: 7px 9px;
  border-radius: 7px;
  background: #F3F8FE;
  font-size: 11px;
  line-height: 1.6;
  color: #2A5AA6;
  cursor: pointer;
}

.cmp-cite:hover {
  background: #E7F0FD;
}

.cmp-empty {
  font-size: 12px;
  color: var(--rt-text-3);
  padding: 22px 0;
  text-align: center;
}

.cmp-alt {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--rt-text-3);
}

.alt-label {
  color: var(--rt-text-3);
}

.alt-item {
  padding: 1px 6px;
  border: 1px dashed var(--rt-border);
  border-radius: 4px;
  cursor: pointer;
}

.alt-item:hover {
  border-color: var(--rt-navy);
  color: var(--rt-navy);
}

.sim-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sim-bar {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: #EDF3FB;
  overflow: hidden;
}

.sim-bar span {
  display: block;
  height: 100%;
  border-radius: 3px;
}

.sim-bar .high {
  background: #12805C;
}

.sim-bar .mid {
  background: #2A6FD6;
}

.sim-bar .low {
  background: #C6A15B;
}

.sim-bar .gray {
  background: #B9CDE6;
}

.sim-num {
  width: 46px;
  text-align: right;
  font-size: 11.5px;
  color: var(--rt-text-2);
}

.scene-name {
  font-size: 12.5px;
  font-weight: 600;
}

.scene-cov {
  font-size: 10.5px;
  color: var(--rt-text-3);
  margin-top: 2px;
}

.mx-cell {
  cursor: pointer;
  padding: 2px 0;
}

.mx-cell:hover .mx-law {
  color: var(--rt-navy);
}

.mx-law {
  font-size: 12px;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mx-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
}

.mx-score {
  font-size: 10.5px;
  color: var(--rt-text-3);
}

.mx-title {
  font-size: 11px;
  color: var(--rt-text-3);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mx-empty {
  font-size: 11.5px;
  color: var(--rt-text-3);
}

.dt-head {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.dt-law {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.6;
}

.dt-title {
  font-size: 12px;
  color: var(--rt-text-3);
  margin: 4px 0 12px;
}

.dt-content {
  font-size: 12.5px;
  line-height: 1.95;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--rt-bg);
  white-space: pre-wrap;
}

.dt-block {
  margin-top: 16px;
  border: 1px solid var(--rt-border);
  border-radius: 8px;
  padding: 12px 14px;
}

.dt-block-hd {
  font-size: 12.5px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--rt-navy);
}

.dt-cite {
  font-size: 12px;
  line-height: 1.7;
  color: var(--rt-text);
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--rt-border-light);
  margin-bottom: 8px;
}

.dt-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
  line-height: 1.9;
  color: var(--rt-text-2);
}

.dt-label {
  width: 66px;
  flex-shrink: 0;
  color: var(--rt-text-3);
  font-size: 11.5px;
}

.dt-explain {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--rt-border-light);
  font-size: 11.5px;
  line-height: 1.85;
  color: var(--rt-text-3);
}

.dt-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.diff-head {
  display: grid;
  grid-template-columns: 1fr 110px 1fr;
  gap: 12px;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--rt-border-light);
}

.diff-side.right {
  text-align: right;
}

.diff-name {
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1.6;
}

.diff-cite {
  font-size: 11px;
  color: var(--rt-text-3);
  margin-top: 4px;
  line-height: 1.6;
}

.diff-score {
  text-align: center;
}

.diff-num {
  font-size: 18px;
  font-weight: 700;
  color: var(--rt-navy);
}

.diff-blocks {
  margin-top: 14px;
  border: 1px solid var(--rt-border);
  border-radius: 8px;
  overflow: auto;
  max-height: calc(100vh - 260px);
}

.diff-line {
  display: grid;
  grid-template-columns: 38px 38px 1fr;
  gap: 6px;
  padding: 2px 8px;
  font-size: 12px;
  line-height: 1.75;
}

.diff-line .ln {
  color: var(--rt-text-3);
  font-size: 10.5px;
  text-align: right;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.diff-line .txt {
  word-break: break-word;
}

.diff-line.insert {
  background: #EAF7F0;
}

.diff-line.delete {
  background: #FDEEEC;
}

.diff-note {
  margin-top: 10px;
}

.history-ft {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
}

/* 窄屏：精读头部与对照卡片改为单列 */
@media (max-width: 720px) {
  .diff-head {
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
    text-align: left;
  }

  .diff-side.right {
    text-align: left;
  }

  .diff-score {
    text-align: left;
  }

  .compare-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
