<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>物流记录</h2>
        <p>车架入库、出库与销售流转，销售后车架状态自动变为已发货</p>
      </div>
    </div>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="q.frameId" placeholder="按车架ID过滤（可留空）" style="width:220px" clearable @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button type="success" @click="openCreate">新增物流事件</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="frameId" label="车架ID" width="90" />
      <el-table-column label="事件" width="120">
        <template #default="{ row }">
          <el-tag :type="EVENT_TYPE[row.eventType]?.tag || 'info'" size="small">{{ EVENT_TYPE[row.eventType]?.name || row.eventType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
      <el-table-column prop="eventTime" label="发生时间" width="170" />
      <el-table-column prop="dealerId" label="操作人ID" width="100" />
    </el-table>

    <el-dialog v-model="dlg" title="新增物流事件" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="车架">
          <el-select v-model="form.frameId" filterable placeholder="选择车架" style="width:100%">
            <el-option v-for="f in frames" :key="f.id" :value="f.id" :label="f.serialNo + '（' + (f.model || '-') + '）'" />
          </el-select>
        </el-form-item>
        <el-form-item label="事件类型">
          <el-radio-group v-model="form.eventType">
            <el-radio-button value="STOCK_IN">入库</el-radio-button>
            <el-radio-button value="STOCK_OUT">出库</el-radio-button>
            <el-radio-button value="SALE">销售</el-radio-button>
          </el-radio-group>
          <div class="tip">选择「销售」后车架状态将自动流转为已发货</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="物流单号 / 收货方等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">提交</el-button>
      </template>
    </el-dialog>
  </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const EVENT_TYPE = {
  STOCK_IN: { name: '入库', tag: 'success' },
  STOCK_OUT: { name: '出库', tag: 'warning' },
  SALE: { name: '销售', tag: 'primary' },
}

const rows = ref([])
const frames = ref([])
const loading = ref(false)
const dlg = ref(false)
const saving = ref(false)
const q = reactive({ frameId: '' })
const form = reactive({ frameId: null, eventType: 'STOCK_IN', remark: '' })

async function load() {
  loading.value = true
  try {
    const params = {}
    if (q.frameId && !isNaN(Number(q.frameId))) params.frameId = Number(q.frameId)
    rows.value = await api.get('/logistics', { params })
  } finally { loading.value = false }
}

async function openCreate() {
  if (!frames.value.length) {
    frames.value = await api.get('/frames', { params: { size: 100 } }).then(d => d.records)
  }
  form.frameId = null; form.eventType = 'STOCK_IN'; form.remark = ''
  dlg.value = true
}

async function save() {
  if (!form.frameId) return ElMessage.warning('请选择车架')
  saving.value = true
  try {
    await api.post('/logistics', { ...form })
    ElMessage.success('物流事件已记录')
    dlg.value = false
    load()
  } finally { saving.value = false }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
.tip { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
