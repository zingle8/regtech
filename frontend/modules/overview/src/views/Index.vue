<template>
  <div class="rt-page">
    <div class="rt-stats">
      <StatCard
        accent
        label="法规条款"
        :value="summary.legalArticles"
        :foot="`覆盖 ${jurisdictions.length} 个法域`"
      />
      <StatCard label="知识库分块" :value="summary.kbChunks" :foot="`${summary.kbDocuments} 篇文档 · ${summary.kbDatasets} 个知识库`" />
      <StatCard label="图谱关系" :value="summary.graphEdges" :foot="`${summary.graphNodes} 个节点`" />
      <StatCard label="今日问答" :value="summary.todayMessages" :foot="`累计 ${summary.messages} 条消息`" />
    </div>

    <div class="grid-2">
      <div class="rt-card">
        <div class="rt-card-hd">
          <span>近 14 天问答趋势</span>
          <el-radio-group v-model="days" size="small" @change="loadTrend">
            <el-radio-button :value="7">7 天</el-radio-button>
            <el-radio-button :value="14">14 天</el-radio-button>
            <el-radio-button :value="30">30 天</el-radio-button>
          </el-radio-group>
        </div>
        <div class="rt-card-bd">
          <div ref="trendRef" class="chart chart-lg"></div>
        </div>
      </div>

      <div class="rt-card">
        <div class="rt-card-hd">
          <span>法域分布</span>
          <span class="rt-sm rt-dim">按条款数</span>
        </div>
        <div class="rt-card-bd">
          <div ref="pieRef" class="chart chart-lg"></div>
        </div>
      </div>
    </div>

    <div class="grid-2 bottom">
      <div class="rt-card">
        <div class="rt-card-hd">
          <span>条文变更动态</span>
          <div class="hd-right">
            <el-select v-model="action" size="small" class="hd-select" placeholder="全部操作">
              <el-option label="全部操作" value="" />
              <el-option label="新增" value="CREATE" />
              <el-option label="更新" value="UPDATE" />
              <el-option label="删除" value="DELETE" />
            </el-select>
            <el-button size="small" text :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
          </div>
        </div>
        <div class="rt-card-bd no-pad">
          <el-table :data="visibleChanges" size="small" empty-text="暂无条文变更记录，新增或修改法条后在此展示">
            <el-table-column label="操作" width="72">
              <template #default="{ row }">
                <el-tag size="small" :type="actionType(row.action)">{{ actionLabel(row.action) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="法规 / 条款" min-width="190">
              <template #default="{ row }">
                <div class="cell-main" :title="row.lawName">{{ row.lawName || '-' }}</div>
                <div class="cell-sub">
                  <span class="rt-mono">{{ row.articleNo || '-' }}</span>
                  <span v-if="row.title" class="cell-title">{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="变更内容" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.summary || '-' }}</template>
            </el-table-column>
            <el-table-column label="时间 / 操作人" width="132">
              <template #default="{ row }">
                <div class="cell-main">{{ shortTime(row.operateTime) }}</div>
                <div class="cell-sub">{{ row.operatorName || '-' }}</div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div class="rt-card">
        <div class="rt-card-hd">运行指标</div>
        <div class="rt-card-bd">
          <div class="metric" v-for="item in metrics" :key="item.label">
            <div class="metric-left">
              <span class="metric-dot" :class="item.tone"></span>
              <span class="metric-label">{{ item.label }}</span>
            </div>
            <span class="metric-value">{{ item.value }}</span>
          </div>
          <div class="foot-note">
            条文变更（新增 / 更新 / 删除）实时留痕于 <span class="rt-mono">kb_article_log</span>，
            可在「条文检索」中查看条款原文。
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import { StatCard } from '@regtech/ui'
import { jurisdictionLabel } from '@regtech/shared'
import { fetchArticleChanges, fetchSummary, fetchTrend } from '../api'

const summary = reactive({})
const changes = ref([])
const action = ref('')
const trend = ref([])
const days = ref(14)
const loading = ref(false)

const trendRef = ref(null)
const pieRef = ref(null)
let trendChart = null
let pieChart = null

const jurisdictions = computed(() => summary.jurisdictions || [])

const metrics = computed(() => [
  { label: '本地 Ollama', value: summary.ollamaOnline ? '在线' : '未连接', tone: summary.ollamaOnline ? 'ok' : 'bad' },
  { label: '在线用户', value: summary.activeUsers ?? 0 },
  { label: 'AI 会话', value: summary.conversations ?? 0 },
  { label: '本体类', value: summary.ontologyClasses ?? 0 },
  { label: '本体个体', value: summary.ontologyIndividuals ?? 0 },
  { label: '差异记录', value: summary.diffRecords ?? 0 }
])

const loadSummary = async () => {
  const data = await fetchSummary()
  Object.assign(summary, data)
  renderPie()
}

const loadTrend = async () => {
  trend.value = await fetchTrend(days.value)
  renderTrend()
}

const loadChanges = async () => {
  changes.value = await fetchArticleChanges(12)
}

const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadSummary(), loadTrend(), loadChanges()])
  } finally {
    loading.value = false
  }
}

const visibleChanges = computed(() =>
  action.value ? changes.value.filter((item) => item.action === action.value) : changes.value
)

const actionLabel = (value) => ({ CREATE: '新增', UPDATE: '更新', DELETE: '删除' }[value] || value)
const actionType = (value) => ({ CREATE: 'success', UPDATE: 'primary', DELETE: 'danger' }[value] || 'info')
const shortTime = (value) => (value ? String(value).replace('T', ' ').slice(5, 16) : '-')

const renderTrend = () => {
  if (!trendRef.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    grid: { left: 8, right: 14, top: 26, bottom: 4, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: trend.value.map((i) => i.day),
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#DCE7F6' } },
      axisLabel: { color: '#8B93A1', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#F0F1F4' } },
      axisLabel: { color: '#8B93A1', fontSize: 11 }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: trend.value.map((i) => i.total),
        lineStyle: { width: 2, color: '#12386B' },
        itemStyle: { color: '#C6A15B' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(20,36,61,0.16)' },
            { offset: 1, color: 'rgba(20,36,61,0.01)' }
          ])
        }
      }
    ]
  })
}

const renderPie = () => {
  if (!pieRef.value) return
  if (!pieChart) pieChart = echarts.init(pieRef.value)
  pieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, textStyle: { fontSize: 11, color: '#5B6472' } },
    color: ['#12386B', '#C6A15B', '#6C7E97', '#2A5AA6', '#8B93A1'],
    series: [
      {
        type: 'pie',
        radius: ['52%', '72%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        label: { show: false },
        data: jurisdictions.value.map((i) => ({
          name: jurisdictionLabel(i.jurisdiction),
          value: i.total
        }))
      }
    ]
  })
}

const resize = () => {
  trendChart && trendChart.resize()
  pieChart && pieChart.resize()
}

onMounted(async () => {
  await loadAll()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  trendChart && trendChart.dispose()
  pieChart && pieChart.dispose()
})
</script>

<style scoped>
.grid-2 {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: 12px;
}

.grid-2.bottom {
  margin-top: 12px;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
}

.chart {
  width: 100%;
}

.chart-lg {
  height: 232px;
}

.no-pad {
  padding: 0;
}

.hd-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hd-select {
  width: 96px;
}

.cell-main {
  font-size: 12.5px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--rt-text-3);
}

.cell-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px dashed var(--rt-border-light);
}

.metric:last-of-type {
  border-bottom: none;
}

.metric-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.metric-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--rt-text-3);
}

.metric-dot.ok {
  background: #16A34A;
}

.metric-dot.bad {
  background: var(--rt-danger);
}

.metric-label {
  font-size: 12.5px;
  color: var(--rt-text-2);
}

.metric-value {
  font-size: 13px;
  font-weight: 600;
}

.foot-note {
  margin-top: 12px;
  padding: 10px 12px;
  background: var(--rt-bg);
  border-radius: 8px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--rt-text-3);
}
</style>
