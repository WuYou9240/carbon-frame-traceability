<template>
  <div class="trace-bg">
    <!-- ========== 首页态：搜索框 + 装饰插画 ========== -->
    <div v-if="!serial" class="hero">
      <header class="head">
        <div class="logo">🚴</div>
        <h1>公路自行车碳纤维车架<br/>质量溯源查询</h1>
        <p class="sub">输入车架编号，查看全链路质量档案与区块链验真结果</p>
      </header>

      <div class="search-bar">
        <el-input v-model="searchSn" size="large" placeholder="输入车架编号（如 FR2026-TEST-001）" clearable
          @keyup.enter="doSearch">
          <template #append>
            <el-button @click="doSearch">🔍 查询</el-button>
          </template>
        </el-input>
      </div>

      <div class="chips">
        <span class="chip">⛓️ 区块链验真</span>
        <span class="chip">🏭 全链路追溯</span>
        <span class="chip">🛡️ 防篡改存证</span>
      </div>

      <!-- 装饰插画：自行车 + 链上区块 -->
      <div class="illus">
        <svg viewBox="0 0 620 300" fill="none" xmlns="http://www.w3.org/2000/svg">
          <!-- 背景光晕 -->
          <ellipse cx="310" cy="268" rx="220" ry="14" fill="#d6e4f3" opacity="0.75"/>
          <circle cx="86" cy="76" r="5" fill="#bcd4ec"/>
          <circle cx="546" cy="52" r="7" fill="#cfe0f2"/>
          <circle cx="586" cy="150" r="4" fill="#bcd4ec"/>
          <circle cx="46" cy="186" r="4" fill="#cfe0f2"/>

          <!-- 链上区块组 -->
          <g>
            <rect x="165" y="26" width="76" height="50" rx="9" fill="#dcebf9" stroke="#4f8fd6" stroke-width="2"/>
            <text x="203" y="58" text-anchor="middle" font-family="Consolas, monospace" font-size="20" fill="#16324f" font-weight="bold">#</text>
            <line x1="241" y1="51" x2="277" y2="51" stroke="#4f8fd6" stroke-width="2" stroke-dasharray="5 4"/>
            <circle cx="259" cy="51" r="6" fill="#fff" stroke="#4f8fd6" stroke-width="2"/>
            <rect x="277" y="26" width="76" height="50" rx="9" fill="#eaf3fc" stroke="#4f8fd6" stroke-width="2"/>
            <!-- 锁图标 -->
            <path d="M306 44 v-4 a9 9 0 0 1 18 0 v4" stroke="#16324f" stroke-width="2.2" fill="none" stroke-linecap="round"/>
            <rect x="301" y="44" width="28" height="20" rx="4" fill="#16324f"/>
            <circle cx="315" cy="53" r="2.6" fill="#eaf3fc"/>
            <line x1="353" y1="51" x2="389" y2="51" stroke="#4f8fd6" stroke-width="2" stroke-dasharray="5 4"/>
            <circle cx="371" cy="51" r="6" fill="#fff" stroke="#4f8fd6" stroke-width="2"/>
            <rect x="389" y="26" width="76" height="50" rx="9" fill="#dcebf9" stroke="#4f8fd6" stroke-width="2"/>
            <path d="M413 51 l7 8 l14 -16" stroke="#2f9e6f" stroke-width="3.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
          </g>
          <!-- 上链虚线 -->
          <path d="M427 78 C 427 96, 400 100, 390 116" stroke="#8fb8de" stroke-width="2" stroke-dasharray="5 5" fill="none"/>

          <!-- 自行车 -->
          <g stroke="#16324f" stroke-width="3.6" stroke-linecap="round" stroke-linejoin="round" fill="none">
            <!-- 车轮 -->
            <circle cx="200" cy="216" r="46" stroke-width="4"/>
            <circle cx="200" cy="216" r="9" fill="#fff"/>
            <circle cx="420" cy="216" r="46" stroke-width="4"/>
            <circle cx="420" cy="216" r="9" fill="#fff"/>
            <!-- 车架 -->
            <path d="M200 216 L300 216"/>
            <path d="M200 216 L264 120"/>
            <path d="M300 216 L264 120"/>
            <path d="M300 216 L382 128"/>
            <path d="M264 120 L382 128"/>
            <!-- 前叉 -->
            <path d="M382 128 L420 216"/>
            <!-- 座管与车座 -->
            <path d="M264 120 L256 112"/>
            <path d="M234 111 L278 108" stroke-width="6"/>
            <!-- 车把 -->
            <path d="M382 128 L392 114"/>
            <path d="M376 112 L404 110"/>
            <!-- 曲柄 -->
            <circle cx="300" cy="216" r="8" fill="#fff"/>
            <path d="M300 216 L320 240"/>
            <circle cx="320" cy="240" r="4" fill="#16324f"/>
          </g>

          <!-- 二维码小徽章 -->
          <g transform="translate(486,158)">
            <rect x="0" y="0" width="52" height="52" rx="8" fill="#fff" stroke="#4f8fd6" stroke-width="2.5"/>
            <rect x="9" y="9" width="12" height="12" rx="2" fill="#16324f"/>
            <rect x="31" y="9" width="12" height="12" rx="2" fill="#16324f"/>
            <rect x="9" y="31" width="12" height="12" rx="2" fill="#16324f"/>
            <rect x="32" y="32" width="5" height="5" fill="#4f8fd6"/>
            <rect x="40" y="40" width="4" height="4" fill="#4f8fd6"/>
            <rect x="32" y="40" width="4" height="4" fill="#9cc4e8"/>
          </g>
          <path d="M471 200 Q478 210 470 220" stroke="#4f8fd6" stroke-width="2" stroke-dasharray="4 3" fill="none"/>
        </svg>
      </div>

      <footer class="foot">数据由 FISCO BCOS 联盟链存证 · 车架质量溯源系统</footer>
    </div>

    <!-- ========== 报告态：查询到车架后 ========== -->
    <div v-else class="trace-wrap">
      <header class="head">
        <div class="logo">🚴</div>
        <h1>公路自行车碳纤维车架<br/>质量溯源报告</h1>
      </header>

      <!-- 搜索框：输入车架编号直接查询 -->
      <div class="search-bar">
        <el-input v-model="searchSn" placeholder="输入车架编号查询（如 FR2026-TEST-001）" clearable
          @keyup.enter="doSearch">
          <template #append>
            <el-button @click="doSearch">🔍 查询</el-button>
          </template>
        </el-input>
      </div>

      <div v-if="loading" class="loading"><el-icon class="spin"><Loading /></el-icon> 正在查询车架档案…</div>

      <template v-else-if="data">
        <!-- 验真结果（置顶醒目） -->
        <div class="verify" :class="verify.result === 'VALID' ? 'ok' : (verify.result === 'TAMPERED' ? 'bad' : 'warn')">
          <div class="verify-icon">{{ verify.result === 'VALID' ? '✓' : (verify.result === 'TAMPERED' ? '!' : '…') }}</div>
          <div class="verify-text">
            <b>{{ verify.result === 'VALID' ? '区块链验真通过 · 数据未被篡改'
              : (verify.result === 'TAMPERED' ? '篡改警报 · 链上链下哈希不一致'
              : (verify.message || '尚未上链存证')) }}</b>
            <p v-if="verify.result === 'TAMPERED'" class="verify-sub">
              本页的链下数据与区块链存证值已不一致，说明数据在存证后被修改过。<br/>
              请勿采信本页的质量信息，建议联系生产厂商核验真伪。
            </p>
            <p v-else-if="verify.result === 'VALID'" class="verify-sub">
              链下数据经 SHA-256 实时重算，与 FISCO BCOS 链上存证值完全一致。
            </p>
          </div>
        </div>

        <!-- 二维码防伪校验：四层逐项校验，让消费者看得懂"凭什么说是正品" -->
        <section v-if="ac && ac.exists" class="block ac-block">
          <h3>🛡️ 二维码防伪校验
            <span class="ac-level" :class="'lv-' + (ac.level || '').toLowerCase()">{{ ac.levelText }}</span>
          </h3>
          <div class="ac-list">
            <div v-for="(c, i) in ac.checks" :key="i" class="ac-item" :class="{ no: !c.pass }">
              <span class="ac-dot">{{ c.pass ? '✓' : '✕' }}</span>
              <div class="ac-body">
                <b>{{ c.name }}</b>
                <p>{{ c.detail }}</p>
              </div>
            </div>
          </div>
          <p class="ac-msg">{{ ac.message }}</p>
        </section>

        <section class="block">
          <h3>🚲 车架信息</h3>
          <div class="grid">
            <div class="item"><span>序列号</span><b class="mono">{{ frame.serialNo }}</b></div>
            <div class="item"><span>型号</span><b>{{ frame.model }}</b></div>
            <div class="item"><span>尺寸</span><b>{{ frame.size }}</b></div>
            <div class="item"><span>生产车间</span><b>{{ frame.workshop }}</b></div>
            <div class="item"><span>生产日期</span><b>{{ frame.productionDate }}</b></div>
            <div class="item"><span>状态</span><b>{{ STATUS_NAME[frame.status] }}</b></div>
          </div>
        </section>

        <section class="block" v-if="data.batch">
          <h3>🧵 原材料批次</h3>
          <div class="grid">
            <div class="item"><span>批次号</span><b>{{ data.batch.batchNo }}</b></div>
            <div class="item"><span>材料</span><b>{{ data.batch.materialName }}</b></div>
            <div class="item"><span>规格</span><b>{{ data.batch.spec }}</b></div>
            <div class="item"><span>供应商</span><b>{{ data.batch.supplier }}</b></div>
          </div>
        </section>

        <section class="block" v-if="data.processes?.length">
          <h3>🏭 生产工艺全链路</h3>
          <el-timeline style="padding-left:4px">
            <el-timeline-item v-for="p in data.processes" :key="p.id" :timestamp="p.recordTime" placement="top"
              :type="doneProcesses.includes(p.processType) ? 'success' : 'primary'">
              <b>{{ PROCESS_NAME[p.processType] || p.processType }}</b>
              <div class="proc-data">{{ formatJson(p.recordData) }}</div>
            </el-timeline-item>
          </el-timeline>
        </section>

        <section class="block" v-if="data.inspections?.length">
          <h3>🔍 质检记录</h3>
          <div v-for="i in data.inspections" :key="i.id" class="qc-row">
            <span>{{ QC_ITEM_NAME[i.item] || i.item }}</span>
            <el-tag :type="i.result === 'PASS' ? 'success' : 'danger'" size="small">{{ i.result === 'PASS' ? '合格' : '不合格' }}</el-tag>
            <span class="qc-time">{{ i.inspectTime }}　报告号 {{ i.reportNo }}</span>
          </div>
        </section>

        <section class="block" v-if="data.logistics?.length">
          <h3>🚚 流通信息</h3>
          <div v-for="l in data.logistics" :key="l.id" class="qc-row">
            <span>{{ { STOCK_IN: '入库', STOCK_OUT: '出库', SALE: '销售' }[l.eventType] }} · {{ l.location }}</span>
            <span class="qc-time">{{ l.eventTime }}</span>
          </div>
        </section>

        <section class="block chain-block">
          <h3>⛓️ 区块链存证</h3>
          <div class="hash-line"><span>链下实时哈希 (SHA-256)</span><code>{{ verify.offChainHash }}</code></div>
          <div class="hash-line"><span>链上存证哈希</span><code>{{ verify.onChainHash || '—' }}</code></div>
          <div class="hash-line" v-if="verify.hashVersion">
            <span>哈希口径</span>
            <code>v{{ verify.hashVersion }} · {{ verify.hashVersion === 2 ? '车架+工序+碳布批次' : '车架+工序（不含批次）' }}</code>
          </div>
          <div class="tip">链上哈希由 FISCO BCOS 联盟链 QualityTrace 合约存证，消费者可独立验证数据完整性</div>
        </section>

        <section class="block qr-block">
          <h3>📱 本页二维码</h3>
          <div class="qr-flex">
            <el-image :src="'/api/qr/' + serial + '.png'" style="width:150px;height:150px;border:1px solid #dfe6ef;border-radius:8px;background:#fff" fit="contain" />
            <div class="qr-text">
              <p>用手机扫一扫，随时查看这台车架的<br/>全链路质量档案与区块链验真结果</p>
              <p class="mono">扫码内容：{{ serial }}</p>
            </div>
          </div>
        </section>

        <footer class="foot">
          <a href="#/trace" class="back-link">← 返回查询首页</a><br/>
          本报告由车架质量溯源系统生成 · {{ new Date().toLocaleString('zh-CN') }}
        </footer>
      </template>

      <div v-else class="notfound">
        <div style="font-size:40px">🔎</div>
        <p>未找到车架「{{ serial }}」的档案</p>
        <p class="nf-tip">请检查编号是否输入正确，或使用上方搜索框重新查询</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import axios from 'axios'
import { PROCESS_NAME, QC_ITEM_NAME, STATUS_NAME } from '../api'

const route = useRoute()
const serial = route.params.serial || ''
const data = ref(null)
const loading = ref(!!serial)
const searchSn = ref('')

function doSearch() {
  const sn = (searchSn.value || '').trim()
  if (!sn || sn === serial) return
  // 同步更新 hash 后整页刷新，确保按新车架号重新加载
  window.location.hash = '#/trace/' + encodeURIComponent(sn)
  location.reload()
}

const frame = computed(() => data.value?.frame || {})
const verify = computed(() => data.value?.verify || {})
const ac = ref(null)
const doneProcesses = computed(() => (data.value?.processes || []).map(p => p.processType))

function formatJson(s) {
  if (!s) return ''
  try {
    return Object.entries(JSON.parse(s)).map(([k, v]) => k + ': ' + v).join('；')
  } catch { return s }
}

onMounted(async () => {
  if (!serial) return
  try {
    // 防伪校验独立成接口，即便溯源主体查询失败也能单独给出结论
    const [main, check] = await Promise.all([
      axios.get('/api/trace/' + encodeURIComponent(serial)).catch(() => null),
      axios.get('/api/qr/' + encodeURIComponent(serial) + '/verify').catch(() => null)
    ])
    if (main?.data?.code === 0) data.value = main.data.data
    if (check?.data?.code === 0) ac.value = check.data.data
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.trace-bg {
  min-height: 100vh;
  background: linear-gradient(160deg, #eef4fb 0%, #e4ecf7 100%);
  padding: 24px 12px 60px;
}
.trace-wrap { max-width: 640px; margin: 0 auto; }
.head { text-align: center; padding: 20px 0; }
.search-bar { margin-bottom: 16px; }
.search-bar :deep(.el-input-group__append) { background: #16324f; color: #fff; border: none; }
.notfound { text-align: center; padding: 50px 0; color: #606266; background: #fff; border-radius: 12px; }
.notfound p { margin: 12px 0 4px; font-size: 15px; }
.nf-tip { font-size: 12px; color: #909399; }
.head .logo { font-size: 44px; }
.head h1 { font-size: 20px; color: #16324f; margin-top: 6px; line-height: 1.5; }

/* ---- 首页态 ---- */
.hero { max-width: 760px; margin: 0 auto; }
.hero .head { padding: 46px 0 24px; }
.hero .logo { font-size: 56px; }
.hero h1 { font-size: 26px; color: #16324f; margin-top: 10px; line-height: 1.5; }
.hero .sub { font-size: 14px; color: #7a8ba0; margin-top: 12px; }
.hero .search-bar { max-width: 520px; margin: 0 auto 20px; }
.chips { display: flex; justify-content: center; gap: 12px; flex-wrap: wrap; }
.chip {
  font-size: 13px; color: #2c5178; background: #fff; border: 1px solid #d7e4f2;
  padding: 6px 14px; border-radius: 999px; box-shadow: 0 2px 8px rgba(22,50,79,.06);
}
.illus { margin-top: 12px; text-align: center; }
.illus svg { width: 100%; max-width: 620px; height: auto; }
.illus .float { animation: floaty 4s ease-in-out infinite; transform-origin: center; }
@keyframes floaty { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-6px); } }
.foot { text-align: center; font-size: 11px; color: #a0abb8; padding: 16px 0; }

/* ---- 报告态 ---- */
.verify {
  border-radius: 12px; padding: 18px; display: flex; gap: 14px; align-items: center;
  margin-bottom: 16px; box-shadow: 0 4px 16px rgba(0,0,0,.06);
}
.verify.ok { background: linear-gradient(135deg, #e6ffed, #d3f5dd); border: 1px solid #95d475; }
.verify.bad { background: linear-gradient(135deg, #fff1f0, #ffccc7); border: 1px solid #ffa39e; }
.verify.warn { background: #fffbe6; border: 1px solid #ffe58f; }
.verify-icon { font-size: 34px; font-weight: 700; }
.verify.ok .verify-icon { color: #389e0d; }
.verify.bad .verify-icon { color: #cf1322; }
.verify.warn .verify-icon { color: #d48806; }
.verify-text { font-size: 15px; }
.verify-sub { font-size: 12px; line-height: 1.7; margin: 6px 0 0; color: #7a8ba0; }
.verify.ok .verify-sub { color: #3f7a4a; }
.verify.bad .verify-sub { color: #a4393a; }
.verify.bad { animation: alertPulse 2s ease-in-out infinite; }
@keyframes alertPulse {
  0%, 100% { box-shadow: 0 4px 16px rgba(207,19,34,.10); }
  50% { box-shadow: 0 4px 22px rgba(207,19,34,.32); }
}
.block {
  background: #fff; border-radius: 12px; padding: 18px; margin-bottom: 14px;
  box-shadow: 0 2px 10px rgba(0,0,0,.05);
}
.block h3 { font-size: 15px; color: #16324f; margin-bottom: 12px; }
/* 二维码防伪校验 */
.ac-level {
  display: inline-block; margin-left: 8px; padding: 2px 10px; border-radius: 10px;
  font-size: 12px; font-weight: 600; vertical-align: middle;
}
.ac-level.lv-authentic { background: #e6ffed; color: #389e0d; border: 1px solid #95d475; }
.ac-level.lv-tampered { background: #fff1f0; color: #cf1322; border: 1px solid #ffa39e; }
.ac-level.lv-suspect { background: #fffbe6; color: #d48806; border: 1px solid #ffe58f; }
.ac-level.lv-not_recorded { background: #f4f4f5; color: #909399; border: 1px solid #dcdfe6; }
.ac-level.lv-not_found { background: #fff1f0; color: #cf1322; border: 1px solid #ffa39e; }
.ac-list { display: flex; flex-direction: column; gap: 8px; }
.ac-item {
  display: flex; gap: 10px; align-items: flex-start;
  padding: 10px 12px; border-radius: 8px; background: #f6faf6; border: 1px solid #e2f0e2;
}
.ac-item.no { background: #fdf4f4; border-color: #f7dcdc; }
.ac-dot {
  flex: 0 0 20px; height: 20px; line-height: 19px; text-align: center; border-radius: 50%;
  font-size: 12px; font-weight: 700; color: #fff; background: #52c41a;
}
.ac-item.no .ac-dot { background: #ff4d4f; }
.ac-body b { font-size: 13px; color: #303133; }
.ac-body p { font-size: 12px; color: #7a8ba0; margin: 2px 0 0; line-height: 1.6; }
.ac-msg { margin: 12px 0 0; font-size: 12.5px; color: #606266; padding-top: 10px; border-top: 1px dashed #e4e7ed; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.item span { display: block; font-size: 12px; color: #909399; }
.item b { font-size: 13px; color: #303133; }
.proc-data { font-size: 12px; color: #606266; margin-top: 4px; line-height: 1.6; }
.qc-row { display: flex; align-items: center; gap: 10px; padding: 6px 0; font-size: 13px; }
.qc-time { color: #909399; font-size: 12px; }
.chain-block { background: #0f2027; color: #fff; }
.chain-block h3 { color: #fff; }
.hash-line { margin: 8px 0; }
.hash-line span { display: block; font-size: 12px; color: #8899a6; }
.hash-line code {
  display: block; font-size: 12px; color: #69c0ff; word-break: break-all;
  font-family: Consolas, monospace; background: rgba(255,255,255,.06);
  padding: 6px 8px; border-radius: 6px; margin-top: 4px;
}
.tip { font-size: 11px; color: #66788a; margin-top: 10px; }
.qr-flex { display: flex; gap: 16px; align-items: center; }
.qr-text p { font-size: 13px; color: #303133; line-height: 1.8; margin: 0; }
.qr-text .mono { font-size: 11px; color: #909399; font-family: Consolas, monospace; margin-top: 8px; }
.back-link { font-size: 12px; color: #4f8fd6; text-decoration: none; margin-bottom: 6px; display: inline-block; }
.loading { text-align: center; padding: 60px 0; color: #606266; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.mono { font-family: Consolas, monospace; }
</style>
