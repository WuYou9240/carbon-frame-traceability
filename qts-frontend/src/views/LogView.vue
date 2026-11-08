<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>操作日志</h2>
        <p>系统写操作的审计记录：谁、何时、做了什么、结果如何</p>
      </div>
    </div>
  <div class="page-card">
    <div class="mb" style="display:flex;gap:10px">
      <el-input v-model="username" placeholder="操作人账号" style="width:180px" clearable @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
    </div>
    <el-table :data="rows" stripe size="small">
      <el-table-column prop="username" label="操作人" width="110" />
      <el-table-column prop="operation" label="操作" width="140" />
      <el-table-column prop="method" label="方法" min-width="220" />
      <el-table-column label="参数" min-width="260">
        <template #default="{ row }">
          <el-tooltip :content="row.params" placement="top">
            <span class="mono" style="font-size:12px">{{ (row.params || '').slice(0, 60) }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="120" />
      <el-table-column label="结果" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.result === 'SUCCESS' ? 'success' : 'danger'">{{ row.result }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="logTime" label="时间" width="160" />
    </el-table>
    <el-pagination style="margin-top:14px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="20" @current-change="load" />
  </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const username = ref('')

async function load() {
  const data = await api.get('/logs', { params: { current: page.value, size: 20, username: username.value } })
  rows.value = data.records
  total.value = Number(data.total)
}

onMounted(load)
</script>

<style scoped>
.mono { font-family: Consolas, monospace; word-break: break-all; }
</style>
