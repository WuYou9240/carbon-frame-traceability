<template>
  <div v-if="stats" class="page">
    <div class="page-head">
      <div>
        <h2>统计分析</h2>
        <p>车架生产与链上存证的整体运行情况</p>
      </div>
      <div class="head-right">
        <div class="head-chip">数据实时汇总 · {{ today }}</div>
        <el-button type="primary" :loading="exporting" @click="exportCsv">导出台账 CSV</el-button>
      </div>
    </div>

    <!-- KPI -->
    <div class="kpis">
      <div class="kpi" v-for="k in kpis" :key="k.label">
        <div class="kpi-ico" :style="{ background: k.bg, color: k.color }">{{ k.icon }}</div>
        <div class="kpi-body">
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value">{{ k.value }}<small v-if="k.unit">{{ k.unit }}</small></div>
          <div class="kpi-note">{{ k.note }}</div>
        </div>
      </div>
    </div>

    <!-- 中段：状态分布 + 热度榜 -->
    <div class="mid">
      <el-card class="panel" header="车架状态分布">
        <div class="status-list">
          <div class="status-row" v-for="s in statusRows" :key="s.key">
            <span class="s-dot" :style="{ background: s.color }"></span>
            <span class="s-name">{{ s.name }}</span>
            <div class="s-bar">
              <i :style="{ width: s.pct + '%', background: s.color }"></i>
            </div>
            <span class="s-num">{{ s.value }}</span>
            <span class="s-pct">{{ s.pct }}%</span>
          </div>
        </div>
        <div class="status-foot">
          <span>状态流转：在产 → 已固化 → 已质检 → 已发货</span>
        </div>
      </el-card>

      <el-card class="panel" header="溯源查询热度 Top10">
        <el-empty v-if="!hotRows.length" description="暂无扫码记录" :image-size="72" />
        <div v-else class="rank-list">
          <div class="rank-row" v-for="(r, i) in hotRows" :key="r.serial">
            <span class="rank-no" :class="i < 3 ? 'top' + (i + 1) : ''">{{ i + 1 }}</span>
            <span class="rank-serial mono">{{ r.serial }}</span>
            <div class="rank-bar"><i :style="{ width: hotPct(r.count) + '%' }"></i></div>
            <span class="rank-count">{{ r.count }} 次</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 质检汇总 -->
    <el-card class="panel insp" header="质检结果统计">
      <div class="insp-wrap">
        <div class="insp-metrics">
          <div class="im">
            <span>质检记录总数</span>
            <b>{{ stats.inspectionTotal }}</b>
          </div>
          <div class="im ok">
            <span>合格</span>
            <b>{{ stats.inspectionPass }}</b>
          </div>
          <div class="im bad">
            <span>不合格</span>
            <b>{{ stats.inspectionFail }}</b>
          </div>
          <div class="im">
            <span>成功上链交易</span>
            <b>{{ stats.chainTxSuccess }}</b>
          </div>
        </div>
        <div class="pass-ring">
          <div class="ring" :style="{ '--pct': (stats.passRate || 0) }">
            <div class="ring-in">
              <b>{{ stats.passRate }}<small>%</small></b>
              <span>合格率</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api, { STATUS_NAME } from '../api'

const stats = ref(null)
const exporting = ref(false)
const today = new Date().toLocaleDateString('zh-CN')
onMounted(async () => { stats.value = await api.get('/stats/overview') })

// 导出台账：后端返回带 BOM 的 CSV，这里直接落成文件，双击可被 Excel 正常识别中文
async function exportCsv() {
  exporting.value = true
  try {
    const blob = await api.get('/stats/export', { responseType: 'blob', timeout: 120000 })
    if (blob.type && blob.type.includes('json')) {
      const text = await blob.text()
      ElMessage.error(JSON.parse(text).message || '导出失败')
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `质量溯源台账-${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('台账已导出，可直接用 Excel 打开')
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

const STATUS_COLOR = {
  IN_PROD: '#6366f1',
  CURED: '#f59e0b',
  INSPECTED: '#16a34a',
  SHIPPED: '#2563eb',
}

const kpis = computed(() => {
  const s = stats.value || {}
  return [
    { label: '车架总数', value: s.frameTotal ?? 0, unit: ' 台', icon: '🚲', color: '#2563eb', bg: '#eef4ff', note: '全部在册车架档案' },
    { label: '已上链存证', value: s.onChainCount ?? 0, unit: ' 台', icon: '⛓️', color: '#0891b2', bg: '#e8f9fd', note: '区块链已固化哈希' },
    { label: '质检合格率', value: s.passRate ?? 0, unit: '%', icon: '✅', color: '#16a34a', bg: '#eefaf1', note: '基于全部质检记录' },
    { label: '上链交易数', value: s.chainTxTotal ?? 0, unit: ' 笔', icon: '📜', color: '#7c3aed', bg: '#f4efff', note: '累计存证交易' },
  ]
})

const statusRows = computed(() => {
  if (!stats.value) return []
  const total = stats.value.frameTotal || 1
  return Object.entries(stats.value.frameByStatus).map(([k, v]) => ({
    key: k,
    name: STATUS_NAME[k] || k,
    value: v,
    pct: Math.round(v * 100 / total),
    color: STATUS_COLOR[k] || '#94a3b8',
  }))
})

const hotRows = computed(() => {
  if (!stats.value) return []
  return Object.entries(stats.value.traceHotTop10 || {})
    .map(([serial, count]) => ({ serial, count }))
    .sort((a, b) => b.count - a.count)
})
const hotMax = computed(() => Math.max(1, ...hotRows.value.map(r => r.count)))
function hotPct(c) { return Math.max(6, Math.round(c * 100 / hotMax.value)) }
</script>

<style scoped>
.head-right { display: flex; align-items: center; gap: 10px; }
.head-chip {
  font-size: 12px; color: var(--qts-text-3);
  background: #fff; border: 1px solid var(--qts-border);
  padding: 6px 12px; border-radius: 999px;
}

/* KPI */
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 14px; }
.kpi {
  display: flex; gap: 14px; align-items: flex-start;
  padding: 18px 18px 16px;
  background: #fff; border: 1px solid var(--qts-border); border-radius: var(--qts-radius);
  box-shadow: var(--qts-shadow);
  transition: transform .22s, box-shadow .22s;
}
.kpi:hover { transform: translateY(-2px); box-shadow: var(--qts-shadow-hover); }
.kpi-ico {
  width: 42px; height: 42px; border-radius: 12px; flex: none;
  display: grid; place-items: center; font-size: 19px;
}
.kpi-label { font-size: 12.5px; color: var(--qts-text-2); }
.kpi-value {
  font-size: 28px; font-weight: 700; color: var(--qts-ink);
  line-height: 1.25; margin: 3px 0 2px;
  font-variant-numeric: tabular-nums; letter-spacing: -.5px;
}
.kpi-value small { font-size: 12px; font-weight: 500; color: var(--qts-text-3); margin-left: 2px; }
.kpi-note { font-size: 11px; color: var(--qts-text-3); }

/* 中段 */
.mid { display: grid; grid-template-columns: 1.05fr 1fr; gap: 14px; margin-bottom: 14px; }
.panel { height: 100%; }
.status-list { display: flex; flex-direction: column; gap: 16px; padding: 4px 2px 2px; }
.status-row { display: grid; grid-template-columns: 10px 60px 1fr 34px 44px; align-items: center; gap: 10px; }
.s-dot { width: 8px; height: 8px; border-radius: 50%; }
.s-name { font-size: 13px; color: var(--qts-text-2); }
.s-bar { height: 8px; border-radius: 6px; background: #f1f5fb; overflow: hidden; }
.s-bar i { display: block; height: 100%; border-radius: 6px; transition: width .5s ease; }
.s-num { font-size: 13px; font-weight: 600; color: var(--qts-ink); text-align: right; font-variant-numeric: tabular-nums; }
.s-pct { font-size: 11.5px; color: var(--qts-text-3); text-align: right; }
.status-foot { margin-top: 18px; padding-top: 13px; border-top: 1px dashed #eef2f8; font-size: 11.5px; color: var(--qts-text-3); }

.rank-list { display: flex; flex-direction: column; gap: 11px; }
.rank-row { display: grid; grid-template-columns: 22px 1fr 84px 52px; gap: 10px; align-items: center; }
.rank-no {
  width: 22px; height: 22px; border-radius: 7px; display: grid; place-items: center;
  font-size: 11px; font-weight: 700; color: #94a3b8; background: #f4f7fc;
}
.rank-no.top1 { background: linear-gradient(135deg, #fbbf24, #f59e0b); color: #fff; }
.rank-no.top2 { background: linear-gradient(135deg, #cbd5e1, #94a3b8); color: #fff; }
.rank-no.top3 { background: linear-gradient(135deg, #f0a97a, #d97a45); color: #fff; }
.rank-serial { font-size: 12px; color: var(--qts-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-bar { height: 6px; border-radius: 5px; background: #f1f5fb; overflow: hidden; }
.rank-bar i { display: block; height: 100%; border-radius: 5px; background: linear-gradient(90deg, #60a5fa, #22d3ee); }
.rank-count { font-size: 11.5px; color: var(--qts-text-2); text-align: right; font-variant-numeric: tabular-nums; }

/* 质检汇总 */
.insp-wrap { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.insp-metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; flex: 1; }
.im {
  background: #fafcff; border: 1px solid #eef3fa; border-radius: 11px;
  padding: 14px 16px;
}
.im span { font-size: 11.5px; color: var(--qts-text-3); }
.im b { display: block; font-size: 22px; color: var(--qts-ink); margin-top: 4px; font-variant-numeric: tabular-nums; }
.im.ok b { color: var(--qts-success); }
.im.bad b { color: var(--qts-danger); }

.pass-ring { flex: none; }
.ring {
  --pct: 0;
  width: 108px; height: 108px; border-radius: 50%;
  background: conic-gradient(#22c55e calc(var(--pct) * 1%), #eef3fa 0);
  display: grid; place-items: center;
}
.ring-in {
  width: 84px; height: 84px; border-radius: 50%; background: #fff;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  box-shadow: inset 0 0 0 1px #eef3fa;
}
.ring-in b { font-size: 20px; color: var(--qts-ink); line-height: 1; }
.ring-in b small { font-size: 11px; font-weight: 500; }
.ring-in span { font-size: 10.5px; color: var(--qts-text-3); margin-top: 4px; }

@media (max-width: 1180px) {
  .kpis { grid-template-columns: repeat(2, 1fr); }
  .mid { grid-template-columns: 1fr; }
}
</style>
