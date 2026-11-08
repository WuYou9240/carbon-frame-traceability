<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>车架档案</h2>
        <p>共 {{ total }} 台在册车架 · 每台车架对应一份链上质量存证</p>
      </div>
      <el-button v-if="canEdit" type="primary" @click="openCreate">＋ 新建车架档案</el-button>
    </div>

    <el-card class="tool-card">
      <div class="toolbar">
        <el-input v-model="q.serialNo" placeholder="搜索序列号" clearable style="width:210px" @keyup.enter="load">
          <template #prefix><span class="ico">🔍</span></template>
        </el-input>
        <el-select v-model="q.status" placeholder="全部状态" style="width:132px" clearable>
          <el-option v-for="(n, k) in STATUS_NAME" :key="k" :label="n" :value="k" />
        </el-select>
        <el-select v-model="q.hashRecorded" placeholder="上链状态" style="width:132px" clearable>
          <el-option label="已上链" :value="1" />
          <el-option label="未上链" :value="0" />
        </el-select>
        <el-button type="primary" plain @click="load">查询</el-button>
        <span class="tool-tip">支持按序列号模糊搜索</span>
      </div>
    </el-card>

    <el-alert v-if="tamperCount > 0" type="error" :closable="false" show-icon class="alarm"
      :title="'篡改警报：当前页有 ' + tamperCount + ' 台车架的链下数据与链上存证哈希不一致，请立即核查！'" />

    <el-card class="table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="序列号" min-width="180">
          <template #default="{ row }">
            <span class="serial mono">{{ row.serialNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="型号" width="120" />
        <el-table-column prop="size" label="尺寸" width="76" />
        <el-table-column prop="workshop" label="车间" width="96" />
        <el-table-column prop="productionDate" label="生产日期" width="110" />
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <span class="status-pill" :class="row.status">{{ STATUS_NAME[row.status] }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上链存证" width="100">
          <template #default="{ row }">
            <span class="chain-pill" :class="row.hashRecorded === 1 ? 'on' : 'off'">
              <i></i>{{ row.hashRecorded === 1 ? '已存证' : '未存证' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="验真状态" width="112">
          <template #default="{ row }">
            <span v-if="verifyOf(row) === 'VALID'" class="vf ok">✓ 通过</span>
            <span v-else-if="verifyOf(row) === 'TAMPERED'" class="vf bad">✕ 被篡改</span>
            <span v-else-if="verifyOf(row) === 'NOT_RECORDED'" class="vf none">未上链</span>
            <span v-else class="vf loading">校验中…</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push('/frames/' + row.id)">详情</el-button>
            <el-button v-if="canEdit" link @click="editRow(row)">编辑</el-button>
            <el-button v-if="user?.role === 'ADMIN' && row.hashRecorded !== 1" link type="danger" @click="del(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的车架档案" :image-size="90" />
        </template>
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
          v-model:current-page="page" :page-size="10" @current-change="load" />
      </div>
    </el-card>

    <el-dialog v-model="dlg" :title="form.id ? '编辑车架' : '新建车架档案'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="序列号" v-if="!form.id">
          <el-input v-model="form.serialNo" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="型号"><el-input v-model="form.model" placeholder="如 Aero-R1" /></el-form-item>
        <el-form-item label="尺寸">
          <el-select v-model="form.size" placeholder="选择尺寸">
            <el-option v-for="s in ['48cm','50cm','52cm','54cm','56cm']" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="碳布批次">
          <el-select v-model="form.batchId" placeholder="选择原材料批次" style="width:100%">
            <el-option v-for="b in batches" :key="b.id" :label="b.batchNo + '（' + b.materialName + '）'" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="生产车间"><el-input v-model="form.workshop" placeholder="如 一号车间" /></el-form-item>
        <el-form-item label="生产日期">
          <el-date-picker v-model="form.productionDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api, { currentUser, STATUS_NAME } from '../api'

const user = currentUser()
const canEdit = computed(() => ['ADMIN', 'OPERATOR'].includes(user?.role))
const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const dlg = ref(false)
const batches = ref([])
const q = reactive({ serialNo: '', status: '', hashRecorded: null })
const form = reactive({})
/** 每行车架的实时验真结果：{ [id]: 'VALID' | 'TAMPERED' | 'NOT_RECORDED' } */
const verifyMap = ref({})
const tamperCount = computed(() => Object.values(verifyMap.value).filter(v => v === 'TAMPERED').length)

function verifyOf(row) {
  return verifyMap.value[row.id]
}

/** 并发校验当前页每台车架：链下实时哈希 vs 链上存证哈希 */
async function loadVerify(list) {
  verifyMap.value = {}
  const out = {}
  await Promise.all(list.map(async (r) => {
    try {
      const v = await api.get('/frames/' + r.id + '/hash')
      out[r.id] = v?.result || 'UNKNOWN'
    } catch {
      out[r.id] = 'UNKNOWN'
    }
  }))
  verifyMap.value = out
}

async function load() {
  loading.value = true
  try {
    const data = await api.get('/frames', { params: { current: page.value, size: 10, ...clean(q) } })
    rows.value = data.records
    total.value = Number(data.total)
    verifyMap.value = {}
    loadVerify(rows.value)
  } finally { loading.value = false }
}

function clean(obj) {
  const o = {}
  for (const [k, v] of Object.entries(obj)) if (v !== '' && v !== null && v !== undefined) o[k] = v
  return o
}

function openCreate() {
  Object.keys(form).forEach(k => delete form[k])
  dlg.value = true
}

function editRow(row) {
  Object.assign(form, row)
  dlg.value = true
}

async function save() {
  if (form.id) {
    await api.put('/frames/' + form.id, form)
  } else {
    await api.post('/frames', form)
  }
  ElMessage.success('已保存')
  dlg.value = false
  Object.keys(form).forEach(k => delete form[k])
  load()
}

async function del(row) {
  await ElMessageBox.confirm('确定删除该车架档案？', '提示', { type: 'warning' })
  await api.delete('/frames/' + row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  load()
  try { batches.value = (await api.get('/materials', { params: { current: 1, size: 100 } })).records } catch {}
})
</script>

<style scoped>
.tool-card :deep(.el-card__body) { padding: 14px 18px; }
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tool-tip { margin-left: auto; font-size: 11.5px; color: var(--qts-text-3); }
.ico { font-size: 12px; }

.alarm { margin-bottom: 12px; border-radius: 12px; }
.table-card :deep(.el-card__body) { padding: 4px 6px 12px; }
.serial { font-size: 12.5px; color: #0f172a; letter-spacing: .2px; background: #f4f8fe; padding: 3px 8px; border-radius: 6px; }

.status-pill {
  display: inline-block; font-size: 11.5px; padding: 3px 9px; border-radius: 999px;
  border: 1px solid transparent;
}
.status-pill.IN_PROD { color: #4f46e5; background: #eef0ff; border-color: #e0e4ff; }
.status-pill.CURED { color: #b45309; background: #fff7ed; border-color: #ffedd5; }
.status-pill.INSPECTED { color: #15803d; background: #f0fdf4; border-color: #dcfce7; }
.status-pill.SHIPPED { color: #1d4ed8; background: #eff6ff; border-color: #dbeafe; }

.chain-pill { display: inline-flex; align-items: center; gap: 5px; font-size: 11.5px; }
.chain-pill i { width: 6px; height: 6px; border-radius: 50%; }
.chain-pill.on { color: #15803d; }
.chain-pill.on i { background: #22c55e; box-shadow: 0 0 0 3px rgba(34,197,94,.14); }
.chain-pill.off { color: #94a3b8; }
.chain-pill.off i { background: #cbd5e1; }

.vf { font-size: 11.5px; font-weight: 600; padding: 3px 9px; border-radius: 999px; }
.vf.ok { color: #15803d; background: #eafaf0; border: 1px solid #d3f3df; }
.vf.bad { color: #b91c1c; background: #fef2f2; border: 1px solid #fde2e1; }
.vf.none { color: #94a3b8; background: #f6f8fb; border: 1px solid #eef2f8; }
.vf.loading { color: #b4bfcd; font-weight: 400; }

.pager { display: flex; justify-content: flex-end; padding: 12px 14px 4px; }
</style>
