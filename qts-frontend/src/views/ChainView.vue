<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>上链存证</h2>
        <p>FISCO BCOS 联盟链上的哈希存证记录，只存指纹、不存业务数据</p>
      </div>
      <div class="live-chip"><i></i> 节点在线</div>
    </div>

    <!-- 链状态概览 -->
    <div class="chain-hero" v-if="info">
      <div class="hero-item block">
        <span>当前块高</span>
        <b>{{ info.blockNumber }}</b>
        <small>持续出块中</small>
      </div>
      <div class="hero-item">
        <span>群组</span>
        <b class="mono">{{ info.group }}</b>
        <small>四节点共识</small>
      </div>
      <div class="hero-item wide">
        <span>存证合约</span>
        <b class="mono hash">{{ info.contractAddress }}</b>
        <small>QualityTrace · recordHash / getHash</small>
      </div>
      <div class="hero-item wide">
        <span>交易账户</span>
        <b class="mono hash">{{ info.account }}</b>
        <small>链上交易发起方</small>
      </div>
    </div>

    <el-card class="table-card">
      <template #header>
        <div class="card-head">
          <span>上链交易记录</span>
          <el-input v-model="serialNo" placeholder="按序列号过滤" clearable style="width:190px" @keyup.enter="load">
            <template #prefix><span style="font-size:12px">🔍</span></template>
          </el-input>
        </div>
      </template>
      <el-table :data="rows" stripe v-loading="loading">
        <el-table-column label="序列号" width="170">
          <template #default="{ row }">
            <span class="serial mono">{{ row.serialNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="链上哈希" min-width="300">
          <template #default="{ row }">
            <span class="hash-cell mono" :title="row.onchainHash">{{ shortHash(row.onchainHash) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="交易哈希" min-width="300">
          <template #default="{ row }">
            <span class="hash-cell mono tx" :title="row.txHash">{{ shortHash(row.txHash) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="块高" width="86">
          <template #default="{ row }"><span class="block-no">#{{ row.blockNumber }}</span></template>
        </el-table-column>
        <el-table-column label="版本" width="76">
          <template #default="{ row }"><span class="ver">v{{ row.dataVersion }}</span></template>
        </el-table-column>
        <el-table-column prop="txTime" label="上链时间" width="168" />
        <template #empty><el-empty description="暂无链上交易记录" :image-size="90" /></template>
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
          v-model:current-page="page" :page-size="10" @current-change="load" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const serialNo = ref('')
const info = ref(null)
const loading = ref(false)

function shortHash(h) {
  if (!h) return '-'
  return h.length > 34 ? h.slice(0, 18) + '…' + h.slice(-12) : h
}

async function load() {
  loading.value = true
  try {
    const data = await api.get('/chain/records', { params: { current: page.value, size: 10, serialNo: serialNo.value } })
    rows.value = data.records
    total.value = Number(data.total)
  } finally { loading.value = false }
}

onMounted(async () => {
  info.value = await api.get('/chain/info')
  load()
})
</script>

<style scoped>
.live-chip {
  display: inline-flex; align-items: center; gap: 7px;
  font-size: 12px; color: #15803d; background: #f0fdf4;
  border: 1px solid #dcfce7; padding: 6px 12px; border-radius: 999px;
}
.live-chip i { width: 6px; height: 6px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 0 3px rgba(34,197,94,.16); }

.chain-hero {
  display: grid; grid-template-columns: 150px 150px 1fr 1fr;
  gap: 1px; background: #e7ecf3;
  border: 1px solid var(--qts-border);
  border-radius: var(--qts-radius); overflow: hidden;
  box-shadow: var(--qts-shadow);
  margin-bottom: 14px;
}
.hero-item {
  background: linear-gradient(180deg, #ffffff, #fbfdff);
  padding: 16px 18px 14px;
  display: flex; flex-direction: column; gap: 5px;
}
.hero-item span { font-size: 11.5px; color: var(--qts-text-3); letter-spacing: .3px; }
.hero-item b { font-size: 15px; color: var(--qts-ink); font-weight: 600; }
.hero-item.block b { font-size: 26px; letter-spacing: -.5px; line-height: 1.1; font-variant-numeric: tabular-nums; }
.hero-item small { font-size: 10.5px; color: #b0bccb; }
.hero-item .hash { font-size: 12px; word-break: break-all; line-height: 1.5; color: #334155; }

.card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.table-card :deep(.el-card__body) { padding: 4px 6px 12px; }
.serial { font-size: 12.5px; background: #f4f8fe; padding: 3px 8px; border-radius: 6px; color: #0f172a; }
.hash-cell {
  font-size: 12px; color: #334155;
  display: inline-block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.hash-cell.tx { color: #0891b2; }
.block-no { font-size: 12px; color: #1d4ed8; background: #eff6ff; padding: 2px 7px; border-radius: 6px; font-variant-numeric: tabular-nums; }
.ver { font-size: 11px; color: #7c3aed; background: #f5f0ff; padding: 2px 7px; border-radius: 6px; }
.pager { display: flex; justify-content: flex-end; padding: 12px 14px 4px; }
</style>
