<template>
  <div class="rt-page-fixed">
    <div class="rt-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索法规 / 条款 / 主题"
        clearable
        style="width: 280px"
        :prefix-icon="Search"
        @keyup.enter="load"
        @clear="load"
      />
      <el-select v-model="category" placeholder="全部类型" clearable style="width: 130px" @change="load">
        <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
      </el-select>
      <div class="chips">
        <span class="chip">节点 <b>{{ graph.nodeTotal || 0 }}</b></span>
        <span class="chip">关系 <b>{{ graph.linkTotal || 0 }}</b></span>
      </div>
      <div class="rt-toolbar-right">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="MagicStick" :loading="building" @click="rebuild">从法条库构图</el-button>
      </div>
    </div>

    <div class="body">
      <div class="rt-card canvas-card">
        <div ref="chartRef" class="chart"></div>
        <div v-if="!graph.nodes || !graph.nodes.length" class="empty-mask">
          <div class="empty-title">图谱为空</div>
          <div class="rt-sm rt-dim">点击右上角「从法条库构图」，按现有法规条款自动生成节点与关系</div>
        </div>
        <div class="legend">
          <span v-for="(c, i) in graph.categories" :key="c.name" class="legend-item">
            <i :style="{ background: palette[i % palette.length] }"></i>{{ c.name }}
          </span>
        </div>
      </div>

      <aside class="rt-card side">
        <div class="rt-card-hd">节点详情</div>
        <div v-if="current" class="rt-card-bd detail">
          <div class="detail-name">{{ current.name }}</div>
          <div class="meta">
            <span class="rt-tag gold">{{ current.categoryName || '未分类' }}</span>
            <span class="rt-tag">{{ jurisdictionLabel(current.jurisdiction) }}</span>
            <span class="rt-tag">关联度 {{ current.value }}</span>
          </div>
          <p class="desc">{{ current.description || '暂无描述' }}</p>
          <div class="neighbors">
            <div class="neighbors-title">相邻关系</div>
            <div v-for="(n, i) in neighbors" :key="i" class="neighbor">
              <span class="relation">{{ n.relation }}</span>
              <span class="neighbor-name">{{ n.name }}</span>
            </div>
            <div v-if="!neighbors.length" class="rt-dim rt-sm">暂无相邻节点</div>
          </div>
        </div>
        <div v-else class="rt-empty">点击图谱中的节点查看详情</div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Refresh, Search } from '@element-plus/icons-vue'
import { jurisdictionLabel } from '@regtech/shared'
import { buildGraph, fetchGraph } from '../api'

const chartRef = ref(null)
const graph = ref({ nodes: [], links: [], categories: [] })
const keyword = ref('')
const category = ref('')
const loading = ref(false)
const building = ref(false)
const current = ref(null)
const neighbors = ref([])

const palette = ['#12386B', '#C6A15B', '#2A5AA6', '#12805C', '#8B93A1', '#7B5CFF']
const categoryOptions = ['法规', '条款', '主题']

let chart = null

const load = async () => {
  loading.value = true
  try {
    graph.value = await fetchGraph({
      keyword: keyword.value,
      category: category.value,
      limit: 300
    })
    current.value = null
    neighbors.value = []
    render()
  } finally {
    loading.value = false
  }
}

const render = () => {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
    chart.on('click', (params) => {
      if (params.dataType === 'node') selectNode(params.data.id)
    })
  }
  const { nodes, links, categories } = graph.value
  chart.setOption(
    {
      tooltip: {
        formatter: (params) => {
          if (params.dataType === 'edge') return `${params.data.relation}`
          return `${params.data.name}<br/><span style="color:#8B93A1">${params.data.categoryName || ''}</span>`
        }
      },
      animationDuration: 500,
      series: [
        {
          type: 'graph',
          layout: 'force',
          roam: true,
          draggable: true,
          focusNodeAdjacency: true,
          symbol: 'circle',
          edgeSymbol: ['none', 'arrow'],
          edgeSymbolSize: [0, 6],
          categories: (categories || []).map((c, i) => ({
            name: c.name,
            itemStyle: { color: palette[i % palette.length] }
          })),
          label: {
            show: true,
            position: 'right',
            fontSize: 10.5,
            color: '#5B6472',
            formatter: (params) => (params.data.name || '').slice(0, 16)
          },
          lineStyle: { color: '#B9CDE6', width: 1, curveness: 0.08, opacity: 0.85 },
          emphasis: { focus: 'adjacency', lineStyle: { width: 2, color: '#C6A15B' } },
          force: { repulsion: 320, edgeLength: [70, 150], gravity: 0.08 },
          data: nodes,
          links: links.map((l) => ({ ...l, label: { show: false } }))
        }
      ]
    },
    true
  )
}

const selectNode = (id) => {
  const node = (graph.value.nodes || []).find((n) => String(n.id) === String(id))
  if (!node) return
  current.value = node
  neighbors.value = (graph.value.links || [])
    .filter((l) => String(l.source) === String(id) || String(l.target) === String(id))
    .map((l) => {
      const otherId = String(l.source) === String(id) ? l.target : l.source
      const other = (graph.value.nodes || []).find((n) => String(n.id) === String(otherId))
      return { relation: l.relation, name: other ? other.name : otherId }
    })
    .slice(0, 12)
}

const rebuild = async () => {
  await ElMessageBox.confirm(
    '将清空现有图谱，并按法条库重新生成「法规 → 条款 → 主题」节点与关系，是否继续？',
    '从法条库构图',
    { type: 'warning' }
  )
  building.value = true
  try {
    const data = await buildGraph(300)
    ElMessage.success(`构图完成：${data.nodes} 个节点 / ${data.edges} 条关系`)
    await load()
  } finally {
    building.value = false
  }
}

const resize = () => chart && chart.resize()

onMounted(async () => {
  await load()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart && chart.dispose()
})
</script>

<style scoped>
.body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
}

.canvas-card {
  position: relative;
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.chart {
  width: 100%;
  height: 100%;
  background-image: radial-gradient(#E9ECF1 1px, transparent 1px);
  background-size: 18px 18px;
}

.empty-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.82);
}

.empty-title {
  font-size: 14px;
  font-weight: 600;
}

.legend {
  position: absolute;
  left: 16px;
  bottom: 14px;
  display: flex;
  gap: 14px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid var(--rt-border-light);
  padding: 6px 10px;
  border-radius: 7px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: var(--rt-text-2);
}

.legend-item i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.chips {
  display: flex;
  gap: 8px;
  margin-left: 4px;
}

.chip {
  font-size: 11.5px;
  color: var(--rt-text-2);
  background: var(--rt-card);
  border: 1px solid var(--rt-border);
  padding: 3px 10px;
  border-radius: 20px;
}

.chip b {
  color: var(--rt-navy);
}

.side {
  width: 300px;
  flex-shrink: 0;
  overflow: auto;
}

.detail-name {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  margin-bottom: 8px;
}

.meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.desc {
  margin: 12px 0 0;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--rt-text-2);
}

.neighbors {
  margin-top: 18px;
  border-top: 1px dashed var(--rt-border-light);
  padding-top: 12px;
}

.neighbors-title {
  font-size: 11.5px;
  color: var(--rt-text-3);
  margin-bottom: 8px;
}

.neighbor {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 12px;
  border-bottom: 1px dashed var(--rt-border-light);
}

.relation {
  font-size: 10.5px;
  color: #8A6A25;
  background: var(--rt-gold-soft);
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}

.neighbor-name {
  color: var(--rt-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
