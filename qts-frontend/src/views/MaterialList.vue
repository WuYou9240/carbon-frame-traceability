<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>原材料批次</h2>
        <p>碳纤维预浸料等来料批次与来料检验记录</p>
      </div>
    </div>
  <div class="page-card">
    <div class="mb" style="display:flex;gap:10px">
      <el-input v-model="batchNo" placeholder="批次号" style="width:180px" clearable @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button v-if="canEdit" type="success" @click="dlg = true">新增批次</el-button>
    </div>
    <el-table :data="rows" stripe v-loading="loading">
      <el-table-column prop="batchNo" label="批次号" width="150" />
      <el-table-column prop="materialName" label="材料名称" min-width="180" />
      <el-table-column prop="spec" label="规格" min-width="150" />
      <el-table-column prop="supplier" label="供应商" width="150" />
      <el-table-column prop="incomingDate" label="入库日期" width="110" />
      <el-table-column label="来料检验" width="100">
        <template #default="{ row }">
          <el-tag :type="{ PENDING: 'info', PASSED: 'success', FAILED: 'danger' }[row.qcStatus]">
            {{ { PENDING: '待检', PASSED: '合格', FAILED: '不合格' }[row.qcStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" />
    </el-table>
    <el-pagination style="margin-top:14px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="dlg" title="新增原材料批次" width="500px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="批次号"><el-input v-model="form.batchNo" /></el-form-item>
        <el-form-item label="材料名称"><el-input v-model="form.materialName" placeholder="如 T700 碳纤维预浸料" /></el-form-item>
        <el-form-item label="规格"><el-input v-model="form.spec" placeholder="如 12K/200g/32%" /></el-form-item>
        <el-form-item label="供应商"><el-input v-model="form.supplier" /></el-form-item>
        <el-form-item label="入库日期"><el-date-picker v-model="form.incomingDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="来料检验">
          <el-select v-model="form.qcStatus"><el-option label="待检" value="PENDING" /><el-option label="合格" value="PASSED" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api, { currentUser } from '../api'

const user = currentUser()
const canEdit = ['ADMIN', 'OPERATOR'].includes(user?.role)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const batchNo = ref('')
const loading = ref(false)
const dlg = ref(false)
const form = reactive({ qcStatus: 'PASSED' })

async function load() {
  loading.value = true
  try {
    const data = await api.get('/materials', { params: { current: page.value, size: 10, batchNo: batchNo.value } })
    rows.value = data.records
    total.value = Number(data.total)
  } finally { loading.value = false }
}

async function save() {
  await api.post('/materials', form)
  ElMessage.success('已保存')
  dlg.value = false
  Object.keys(form).forEach(k => delete form[k])
  form.qcStatus = 'PASSED'
  load()
}

onMounted(load)
</script>
