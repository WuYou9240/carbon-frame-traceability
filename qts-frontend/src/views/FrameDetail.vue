<template>
  <div v-if="loadError" class="load-fail">
    <el-result icon="error" title="详情加载失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>
  </div>
  <div v-else-if="data" class="page detail">
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <div class="back" @click="$router.back()">← 返回车架档案</div>
        <h2 class="mono">{{ frame.serialNo }}</h2>
        <p>{{ frame.model }} · {{ frame.size }} · {{ frame.workshop }} · 生产于 {{ frame.productionDate }}</p>
      </div>
      <div class="head-tags">
        <span class="status-pill" :class="frame.status">{{ STATUS_NAME[frame.status] }}</span>
        <span class="chain-pill" :class="frame.hashRecorded === 1 ? 'on' : 'off'"><i></i>{{ frame.hashRecorded === 1 ? '已上链存证' : '未上链' }}</span>
      </div>
    </div>

    <!-- 基本信息 -->
    <el-card header="车架档案">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="序列号"><span class="mono">{{ frame.serialNo }}</span></el-descriptions-item>
        <el-descriptions-item label="型号">{{ frame.model }}</el-descriptions-item>
        <el-descriptions-item label="尺寸">{{ frame.size }}</el-descriptions-item>
        <el-descriptions-item label="生产车间">{{ frame.workshop }}</el-descriptions-item>
        <el-descriptions-item label="生产日期">{{ frame.productionDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <span class="status-pill" :class="frame.status">{{ STATUS_NAME[frame.status] }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="碳布批次">{{ data.batch?.batchNo }}（{{ data.batch?.materialName }}）</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ data.batch?.supplier }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 哈希验真 -->
    <el-card>
      <template #header><span>区块链哈希验真</span></template>
      <el-alert v-if="verify.result === 'VALID'" type="success" :closable="false" show-icon
        title="数据已上链存证，链上链下哈希一致，未被篡改" />
      <el-alert v-else-if="verify.result === 'TAMPERED'" type="error" :closable="false" show-icon
        title="篡改警报：链上链下哈希不一致，链下数据已被修改！"
        description="链上存证哈希不会随链下数据变化，二者不符说明存证后数据被改过。请核查操作日志，或用「恢复数据」还原后再确认。" />
      <el-alert v-else type="warning" :closable="false" show-icon :title="verify.message || '尚未上链存证'" />

      <div class="hash-grid">
        <div class="hash-box">
          <div class="hb-label"><span class="hb-dot off"></span>链下实时哈希（SHA-256）</div>
          <div class="hb-value mono">{{ verify.offChainHash || '—' }}</div>
        </div>
        <div class="hash-box">
          <div class="hb-label"><span class="hb-dot" :class="verify.result === 'VALID' ? 'on' : 'bad'"></span>链上存证哈希</div>
          <div class="hb-value mono">{{ verify.onChainHash || '—' }}</div>
        </div>
      </div>

      <div v-if="verify.hashVersion" class="hash-version">
        <span class="hv-tag">哈希口径 v{{ verify.hashVersion }}</span>
        <span class="hv-desc">
          {{ verify.hashVersion === 2
            ? '覆盖车架本体 + 生产工序 + 碳布批次信息；流通（物流）环节单独存证，不计入质量哈希'
            : '历史口径，覆盖车架本体 + 生产工序，不含碳布批次；老数据沿用此口径校验以保证可复验' }}
        </span>
      </div>

      <div v-if="['ADMIN','QC'].includes(user?.role) && frame.hashRecorded !== 1" class="hash-actions">
        <el-button type="primary" :loading="chaining" @click="recordOnChain">立即上链存证</el-button>
        <span class="act-tip">三项质检全部合格后方可存证</span>
      </div>
      <div v-if="user?.role === 'ADMIN' && frame.hashRecorded === 1" class="hash-actions">
        <template v-if="!frame.model.includes('-TAMPERED')">
          <el-button type="danger" plain @click="simulateTamper">🧪 模拟数据篡改</el-button>
          <span class="act-tip">演示用：修改链下数据后，验真将报警 TAMPERED</span>
        </template>
        <template v-else>
          <el-tag type="danger">数据已篡改，验真将报警</el-tag>
          <el-button type="success" plain @click="restoreData">恢复数据</el-button>
        </template>
      </div>
    </el-card>

    <!-- 二维码 -->
    <el-card header="一物一码">
      <div class="qr-row">
        <div class="qr-frame">
          <el-image :src="'/api/qr/' + frame.serialNo + '.png'" fit="contain" />
        </div>
        <div class="qr-info">
          <div class="qr-title">消费者扫码即可查看全链路质量档案</div>
          <div class="qr-line">扫码内容：<span class="mono">{{ qr?.qrContent || ('/trace/' + frame.serialNo) }}</span></div>
          <div class="qr-line">二维码与车架序列号一一绑定，打印后贴附于车架五通位置</div>
          <el-button v-if="frame.serialNo" size="small" style="margin-top:10px" @click="openTrace">打开溯源页 →</el-button>
          <el-button v-if="['ADMIN','QC','OPERATOR'].includes(user?.role)" size="small" type="primary"
                     style="margin-top:10px" :loading="reporting" @click="openReport">生成质检报告</el-button>
        </div>
      </div>
    </el-card>

    <!-- 生产工序 -->
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>🏭 生产工序记录（{{ processes.length }}）</span>
          <el-button v-if="canOp" size="small" type="primary" @click="procDlg = true">录入工序</el-button>
        </div>
      </template>
      <el-steps :active="stepsActive" finish-status="success" style="margin-bottom:16px">
        <el-step v-for="(name, key) in PROCESS_NAME" :key="key" :title="name"
          :status="doneProcesses.includes(key) ? 'success' : 'wait'" />
      </el-steps>
      <el-table :data="processes" size="small" stripe>
        <el-table-column label="工序" width="110">
          <template #default="{ row }">{{ PROCESS_NAME[row.processType] || row.processType }}</template>
        </el-table-column>
        <el-table-column label="工艺参数" min-width="300">
          <template #default="{ row }">
            <span class="mono" style="font-size:12px">{{ formatJson(row.recordData) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="recordTime" label="记录时间" width="160" />
        <el-table-column prop="remark" label="备注" width="140" />
      </el-table>
    </el-card>

    <!-- 质检记录 -->
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>🔍 质检记录（{{ inspections.length }}/3）</span>
          <el-button v-if="['ADMIN','QC'].includes(user?.role)" size="small" type="primary" @click="qcDlg = true">录入质检</el-button>
        </div>
      </template>
      <el-empty v-if="!inspections.length" description="暂无质检记录" :image-size="50" />
      <el-table v-else :data="inspections" size="small" stripe>
        <el-table-column label="质检项目" width="110">
          <template #default="{ row }">{{ QC_ITEM_NAME[row.item] || row.item }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.result === 'PASS' ? 'success' : 'danger'">{{ row.result === 'PASS' ? '合格' : '不合格' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="defectDesc" label="缺陷描述" />
        <el-table-column prop="reportNo" label="报告编号" width="160" />
        <el-table-column prop="inspectTime" label="质检时间" width="160" />
        <el-table-column v-if="['ADMIN','QC'].includes(user?.role)" label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="editQc(row)">修改</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 物流记录 -->
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>🚚 物流 / 销售记录</span>
          <el-button v-if="['ADMIN','DEALER'].includes(user?.role)" size="small" type="primary" @click="logiDlg = true">录入物流</el-button>
        </div>
      </template>
      <el-empty v-if="!logistics.length" description="暂无物流记录" :image-size="50" />
      <el-table v-else :data="logistics" size="small" stripe>
        <el-table-column label="事件" width="110">
          <template #default="{ row }">{{ { STOCK_IN: '入库', STOCK_OUT: '出库', SALE: '销售' }[row.eventType] }}</template>
        </el-table-column>
        <el-table-column prop="location" label="地点/仓库" />
        <el-table-column prop="eventTime" label="时间" width="160" />
        <el-table-column prop="remark" label="备注" />
      </el-table>
    </el-card>

    <!-- 录入工序弹窗 -->
    <el-dialog v-model="procDlg" title="录入工序数据" width="520px">
      <el-alert v-if="frame.hashRecorded === 1" type="warning" :closable="false" style="margin-bottom:14px"
        title="该车架已上链存证：新增工序后原存证将标记失效，需重新上链" />
      <el-form :model="procForm" label-width="100px">
        <el-form-item label="工序类型">
          <el-select v-model="procForm.processType" style="width:100%">
            <el-option v-for="(n, k) in PROCESS_NAME" :key="k" :label="n" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="工艺参数(JSON)">
          <el-input v-model="procForm.recordData" type="textarea" :rows="5"
            :placeholder="JSON.stringify(procTemplates[procForm.processType] || {}, null, 2)" />
          <el-button size="small" link type="primary" @click="fillTemplate">填充示例参数</el-button>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="procForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="procDlg = false">取消</el-button>
        <el-button type="primary" @click="saveProc">保存</el-button>
      </template>
    </el-dialog>

    <!-- 录入质检弹窗 -->
    <el-dialog v-model="qcDlg" title="录入质检结果" width="480px">
      <el-alert v-if="frame.hashRecorded === 1" type="warning" :closable="false" style="margin-bottom:14px"
        title="该车架已上链存证：录入新质检记录后原存证将标记失效，三项质检重新全部合格后会自动重新上链" />
      <el-form :model="qcForm" label-width="100px">
        <el-form-item label="质检项目">
          <el-select v-model="qcForm.item" style="width:100%">
            <el-option v-for="(n, k) in QC_ITEM_NAME" :key="k" :label="n" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-radio-group v-model="qcForm.result">
            <el-radio value="PASS">合格</el-radio>
            <el-radio value="FAIL">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="缺陷描述"><el-input v-model="qcForm.defectDesc" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qcDlg = false">取消</el-button>
        <el-button type="primary" @click="saveQc">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改质检结果弹窗 -->
    <el-dialog v-model="qcEditDlg" title="修改质检结果" width="480px">
      <el-alert type="warning" :closable="false" style="margin-bottom:14px"
        title="若该车架已上链存证，修改后需重新上链，修改行为将记入操作日志" />
      <el-form :model="qcEditForm" label-width="100px">
        <el-form-item label="质检项目">
          <el-input :value="QC_ITEM_NAME[qcEditForm.item] || qcEditForm.item" disabled />
        </el-form-item>
        <el-form-item label="结果">
          <el-radio-group v-model="qcEditForm.result">
            <el-radio value="PASS">合格</el-radio>
            <el-radio value="FAIL">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="缺陷描述"><el-input v-model="qcEditForm.defectDesc" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qcEditDlg = false">取消</el-button>
        <el-button type="primary" @click="saveQcEdit">保存修改</el-button>
      </template>
    </el-dialog>

    <!-- 录入物流弹窗 -->
    <el-dialog v-model="logiDlg" title="录入物流记录" width="480px">
      <el-form :model="logiForm" label-width="100px">
        <el-form-item label="事件类型">
          <el-select v-model="logiForm.eventType" style="width:100%">
            <el-option label="入库" value="STOCK_IN" />
            <el-option label="出库" value="STOCK_OUT" />
            <el-option label="销售" value="SALE" />
          </el-select>
        </el-form-item>
        <el-form-item label="地点/仓库"><el-input v-model="logiForm.location" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="logiForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="logiDlg = false">取消</el-button>
        <el-button type="primary" @click="saveLogi">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api, { currentUser, STATUS_NAME, STATUS_TYPE, PROCESS_NAME, QC_ITEM_NAME } from '../api'

const route = useRoute()
const user = currentUser()
const id = route.params.id
const data = ref(null)
const qr = ref(null)
const loadError = ref('')
const procDlg = ref(false)
const qcDlg = ref(false)
const qcEditDlg = ref(false)
const logiDlg = ref(false)
const chaining = ref(false)
const reporting = ref(false)

/**
 * 生成质检报告：后端返回 A4 版式 HTML，这里转成 Blob 在新标签页打开，
 * 浏览器直接 Ctrl+P 即可打印或另存为 PDF。
 * 不用 window.open 直连接口，是因为新标签页不会带上 Authorization 头。
 */
async function openReport() {
  reporting.value = true
  try {
    const html = await api.get(`/frames/${id}/report`, { responseType: 'text' })
    const url = URL.createObjectURL(new Blob([html], { type: 'text/html;charset=utf-8' }))
    window.open(url, '_blank')
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (e) {
    ElMessage.error('报告生成失败')
  } finally {
    reporting.value = false
  }
}

const procForm = reactive({ processType: 'CLOTH_SELECTION', recordData: '', remark: '' })
const qcForm = reactive({ item: 'APPEARANCE', result: 'PASS', defectDesc: '' })
const qcEditForm = reactive({ id: null, item: '', result: 'PASS', defectDesc: '' })
const logiForm = reactive({ eventType: 'STOCK_IN', location: '', remark: '' })

function editQc(row) {
  qcEditForm.id = row.id
  qcEditForm.item = row.item
  qcEditForm.result = row.result
  qcEditForm.defectDesc = row.defectDesc || ''
  qcEditDlg.value = true
}

async function saveQcEdit() {
  await api.put('/inspections/' + qcEditForm.id, {
    result: qcEditForm.result,
    defectDesc: qcEditForm.defectDesc,
  })
  ElMessage.success('质检结果已修改')
  qcEditDlg.value = false
  load()
}

const procTemplates = {
  CLOTH_SELECTION: { 材料型号: 'T700-12K', 树脂含量: '32%', 批次号: 'T700-2026-03', 库存编号: 'WH-01-023' },
  AUTO_CUTTING: { 裁布层数: 24, 单层尺寸: '600x300mm', 刀头速度: '450mm/s', 设备编号: 'CUT-02' },
  LAYUP: { 铺层角度: [0, 45, 90, 45, 0], 层数: 24, 铺层顺序: '0/45/90/45/0', 作业人员: '王师傅' },
  CURING: { 温度曲线: '80C/1h→130C/2h→160C/3h', 固化压力: '0.6MPa', 固化时间: '360min', 固化炉号: 'AUTOCLAVE-1' },
  CNC: { 加工精度: '±0.05mm', 五轴程序: 'NC-8821', 检测面: '头管/五通' },
  NDT: { 检测方式: '超声波C扫描', 增益: '48dB', 结果: '无分层/孔隙率1.2%' }
}

const frame = computed(() => data.value?.frame || {})
const verify = computed(() => data.value?.verify || {})
const processes = computed(() => data.value?.processes || [])
const inspections = computed(() => data.value?.inspections || [])
const logistics = computed(() => data.value?.logistics || [])
const doneProcesses = computed(() => processes.value.map(p => p.processType))
const stepsActive = computed(() => doneProcesses.value.length)
const canOp = computed(() => ['ADMIN', 'OPERATOR'].includes(user?.role))

function formatJson(s) {
  if (!s) return ''
  try {
    const obj = JSON.parse(s)
    return Object.entries(obj).map(([k, v]) => k + ': ' + v).join('；')
  } catch { return s }
}

function fillTemplate() {
  procForm.recordData = JSON.stringify(procTemplates[procForm.processType] || {}, null, 2)
}

async function load() {
  loadError.value = ''
  try {
    data.value = await api.get('/frames/' + id)
    try { qr.value = await api.get('/frames/' + id + '/qrcode') } catch { qr.value = null }
  } catch (e) {
    loadError.value = e?.response?.status
      ? '接口返回 ' + e.response.status + (e.response?.data?.message ? '：' + e.response.data.message : '')
      : (e?.message || '网络异常')
  }
}

async function saveProc() {
  try { JSON.parse(procForm.recordData) } catch {
    ElMessage.error('工艺参数必须是合法 JSON')
    return
  }
  await api.post('/frames/' + id + '/processes', procForm)
  ElMessage.success('工序已录入')
  procDlg.value = false
  load()
}

async function saveQc() {
  await api.post('/inspections', { ...qcForm, frameId: Number(id) })
  ElMessage.success('质检结果已保存')
  qcDlg.value = false
  load()
}

async function saveLogi() {
  await api.post('/logistics', { ...logiForm, frameId: Number(id) })
  ElMessage.success('物流记录已保存')
  logiDlg.value = false
  load()
}

async function recordOnChain() {
  chaining.value = true
  try {
    const resp = await api.post('/frames/' + id + '/record-onchain')
    ElMessage.success('上链成功！交易哈希: ' + resp.txHash)
    load()
  } finally { chaining.value = false }
}

function openTrace() {
  window.open('/#/trace/' + frame.value.serialNo)
}

/** 演示：篡改链下数据 → 验真应报警（专用接口，不重置存证标记） */
async function simulateTamper() {
  await api.put('/frames/' + id + '/tamper')
  ElMessage.warning('链下数据已修改，链上哈希未变 —— 打开溯源页即可看到 TAMPERED 报警')
  load()
}

/** 演示：把数据改回去 → 验真恢复通过 */
async function restoreData() {
  await api.put('/frames/' + id + '/restore')
  ElMessage.success('链下数据已恢复，链上链下哈希重新一致')
  load()
}

onMounted(load)
</script>

<style scoped>
.mono { font-family: "Cascadia Code", Consolas, monospace; word-break: break-all; }
.detail { display: flex; flex-direction: column; gap: 14px; }
.back { font-size: 12px; color: var(--qts-text-3); cursor: pointer; margin-bottom: 6px; display: inline-block; }
.back:hover { color: var(--qts-primary); }
.page-head h2 { font-size: 22px; letter-spacing: .2px; }
.head-tags { display: flex; align-items: center; gap: 10px; }

.status-pill {
  display: inline-block; font-size: 11.5px; padding: 3px 10px; border-radius: 999px;
  border: 1px solid transparent;
}
.status-pill.IN_PROD { color: #4f46e5; background: #eef0ff; border-color: #e0e4ff; }
.status-pill.CURED { color: #b45309; background: #fff7ed; border-color: #ffedd5; }
.status-pill.INSPECTED { color: #15803d; background: #f0fdf4; border-color: #dcfce7; }
.status-pill.SHIPPED { color: #1d4ed8; background: #eff6ff; border-color: #dbeafe; }

.chain-pill { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; }
.chain-pill i { width: 6px; height: 6px; border-radius: 50%; }
.chain-pill.on { color: #15803d; }
.chain-pill.on i { background: #22c55e; box-shadow: 0 0 0 3px rgba(34,197,94,.14); }
.chain-pill.off { color: #94a3b8; }
.chain-pill.off i { background: #cbd5e1; }

/* 哈希对比 */
.hash-grid { display: grid; grid-template-columns: 1fr; gap: 10px; margin-top: 14px; }
.hash-box {
  background: #0b1727; border: 1px solid #16283f; border-radius: 11px;
  padding: 12px 14px;
}
.hb-label {
  display: flex; align-items: center; gap: 7px;
  font-size: 11px; color: #7c93ad; letter-spacing: .4px; margin-bottom: 7px;
}
.hb-dot { width: 6px; height: 6px; border-radius: 50%; background: #475569; }
.hb-dot.on { background: #22c55e; box-shadow: 0 0 0 3px rgba(34,197,94,.16); }
.hb-dot.bad { background: #ef4444; box-shadow: 0 0 0 3px rgba(239,68,68,.16); }
.hb-value { font-size: 12.5px; color: #8ee6ff; line-height: 1.6; word-break: break-all; }
.hash-version {
  margin-top: 12px; display: flex; gap: 10px; align-items: flex-start;
  background: #f6f9fc; border: 1px solid #e4ecf5; border-radius: 9px; padding: 9px 12px;
}
.hv-tag {
  flex: none; font-size: 11px; font-weight: 600; color: #1d63c9;
  background: #e6f0ff; border-radius: 5px; padding: 2px 8px; letter-spacing: .3px;
}
.hv-desc { font-size: 12px; color: #6b7f96; line-height: 1.65; }
.hash-actions { margin-top: 14px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.act-tip { font-size: 12px; color: var(--qts-text-3); }

/* 二维码 */
.qr-row { display: flex; gap: 24px; align-items: center; }
.qr-frame {
  flex: none; padding: 10px; background: #fff;
  border: 1px solid var(--qts-border); border-radius: 12px;
  box-shadow: 0 6px 20px -12px rgba(15,23,42,.3);
}
.qr-frame :deep(.el-image) { width: 168px; height: 168px; }
.qr-info { display: flex; flex-direction: column; gap: 7px; }
.qr-title { font-size: 14px; font-weight: 600; color: var(--qts-ink); margin-bottom: 3px; }
.qr-line { font-size: 12.5px; color: var(--qts-text-2); line-height: 1.7; }
</style>
