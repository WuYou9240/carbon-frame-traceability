<template>
  <div class="login-bg">
    <div class="glow g1"></div>
    <div class="glow g2"></div>
    <div class="grid"></div>

    <div class="login-wrap">
      <!-- 左侧品牌区 -->
      <div class="brand-side">
        <div class="badge">
          <svg viewBox="0 0 32 32" fill="none">
            <circle cx="9" cy="21" r="5.6" stroke="#8ec5ff" stroke-width="2"/>
            <circle cx="23" cy="21" r="5.6" stroke="#8ec5ff" stroke-width="2"/>
            <path d="M9 21L16 11L23 21M16 11L20 12.2M13.4 10H18" stroke="#fff" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
        <h1>公路自行车<br />碳纤维车架质量溯源系统</h1>
        <p class="tagline">从一块碳布到一辆整车的全链路质量档案，<br />每一次质检、每一道工序，都留下不可篡改的链上凭证。</p>
        <div class="features">
          <div><i>⛓️</i><span>FISCO BCOS 四节点联盟链存证</span></div>
          <div><i>🔍</i><span>SHA-256 实时验真，篡改即时告警</span></div>
          <div><i>📱</i><span>一物一码，消费者扫码可查</span></div>
        </div>
        <div class="chain-line"><span></span><span></span><span></span><em>group0 · 存证链运行中</em></div>
      </div>

      <!-- 右侧登录卡 -->
      <div class="login-card">
        <h2>欢迎回来</h2>
        <p class="sub">请使用系统分配的账号登录</p>
        <el-form @keyup.enter="doLogin">
          <el-form-item>
            <el-input v-model="form.username" placeholder="账号" size="large" prefix-icon="User" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.password" type="password" placeholder="密码" size="large" prefix-icon="Lock" show-password />
          </el-form-item>
          <el-button type="primary" size="large" class="submit" :loading="loading" @click="doLogin">登 录</el-button>
        </el-form>
        <div v-if="showHint" class="hint">
          <div class="hint-title">演示账号（密码均为 Qts@2026）</div>
          <div class="chips">
            <span v-for="u in demoUsers" :key="u" @click="fill(u)">{{ u }}</span>
          </div>
        </div>
      </div>
    </div>

    <p class="copyright">© 2026 碳纤维车架质量溯源系统 · 基于 FISCO BCOS 联盟链</p>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const router = useRouter()
const form = reactive({ username: 'admin', password: '' })
const loading = ref(false)
const showHint = ref(true)
const demoUsers = ['admin', 'operator', 'qc', 'dealer', 'xfz']

function fill(u) { form.username = u; form.password = 'Qts@2026' }

async function doLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const data = await api.post('/auth/login', form)
    localStorage.setItem('qts_token', data.token)
    localStorage.setItem('qts_user', JSON.stringify(data.user))
    ElMessage.success('登录成功')
    // 按角色落到各自的首页：消费者 → 扫码页，生产操作员 → 车架档案，其余 → 统计分析
    const role = data.user?.role
    router.push(role === 'CONSUMER' ? '/trace' : (role === 'OPERATOR' ? '/frames' : '/stats'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-bg {
  position: relative; min-height: 100%;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: radial-gradient(1200px 700px at 18% 8%, #12324f 0%, transparent 55%),
              radial-gradient(900px 600px at 88% 92%, #0e3b4f 0%, transparent 55%),
              linear-gradient(140deg, #071019 0%, #0b1a2a 45%, #08222f 100%);
  overflow: hidden;
}
.glow { position: absolute; border-radius: 50%; filter: blur(90px); opacity: .55; pointer-events: none; }
.g1 { width: 460px; height: 460px; background: #1d4ed8; top: -120px; left: -80px; }
.g2 { width: 420px; height: 420px; background: #0891b2; bottom: -140px; right: -60px; }
.grid {
  position: absolute; inset: 0; pointer-events: none; opacity: .16;
  background-image: linear-gradient(rgba(148,163,184,.16) 1px, transparent 1px),
                    linear-gradient(90deg, rgba(148,163,184,.16) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(circle at 50% 45%, #000 0%, transparent 72%);
}

.login-wrap {
  position: relative; z-index: 2;
  display: grid; grid-template-columns: 1fr 400px;
  gap: 64px; align-items: center;
  width: min(1080px, 92vw);
}

/* 左侧品牌 */
.brand-side { color: #fff; }
.badge {
  width: 52px; height: 52px; border-radius: 15px;
  display: grid; place-items: center; margin-bottom: 26px;
  background: linear-gradient(140deg, rgba(37,99,235,.32), rgba(6,182,212,.24));
  border: 1px solid rgba(142,197,255,.35);
  box-shadow: 0 8px 26px -10px rgba(56,148,255,.75);
}
.badge svg { width: 30px; height: 30px; }
.brand-side h1 {
  font-size: 34px; line-height: 1.35; margin: 0 0 18px;
  letter-spacing: .5px; font-weight: 600;
}
.tagline { color: #93a8c2; font-size: 13.5px; line-height: 1.9; margin: 0 0 30px; }
.features { display: flex; flex-direction: column; gap: 12px; }
.features div { display: flex; align-items: center; gap: 11px; font-size: 13px; color: #c3d3e6; }
.features i {
  width: 30px; height: 30px; border-radius: 9px; display: grid; place-items: center;
  background: rgba(148, 163, 184, .1); border: 1px solid rgba(148, 163, 184, .16); font-style: normal; font-size: 14px;
}
.chain-line { display: flex; align-items: center; gap: 6px; margin-top: 34px; }
.chain-line span {
  width: 7px; height: 7px; border-radius: 50%; background: #22d3ee;
  box-shadow: 0 0 0 4px rgba(34, 211, 238, .12);
}
.chain-line span:nth-child(2) { animation: pulse 1.6s infinite .2s; }
.chain-line span:nth-child(3) { animation: pulse 1.6s infinite .4s; }
.chain-line em { font-style: normal; font-size: 11.5px; color: #6b8aa8; margin-left: 8px; }
@keyframes pulse { 0%,100% { opacity: .35 } 50% { opacity: 1 } }

/* 右侧卡片 */
.login-card {
  padding: 36px 34px 30px;
  border-radius: 20px;
  background: rgba(255, 255, 255, .97);
  border: 1px solid rgba(255, 255, 255, .6);
  box-shadow: 0 30px 80px -30px rgba(0, 0, 0, .7), 0 0 0 1px rgba(148, 163, 184, .12);
}
.login-card h2 { margin: 0 0 6px; font-size: 21px; color: #0f172a; letter-spacing: .3px; }
.sub { margin: 0 0 26px; font-size: 12.5px; color: #94a3b8; }
.submit { width: 100%; height: 44px; font-size: 14.5px; letter-spacing: 3px; margin-top: 4px; }
.hint {
  margin-top: 22px; padding: 13px 14px;
  border-radius: 11px; background: #f6f9fe; border: 1px dashed #d8e4f5;
}
.hint-title { font-size: 11.5px; color: #7d90a8; margin-bottom: 9px; }
.chips { display: flex; flex-wrap: wrap; gap: 7px; }
.chips span {
  font-size: 11.5px; color: #2563eb; background: #fff;
  border: 1px solid #d7e5fb; border-radius: 999px; padding: 3px 10px; cursor: pointer;
  transition: all .18s;
}
.chips span:hover { background: #2563eb; color: #fff; border-color: #2563eb; }

.copyright {
  position: relative; z-index: 2; margin-top: 44px;
  font-size: 11px; color: #3f5468; letter-spacing: .4px;
}

/* 窄屏 */
@media (max-width: 900px) {
  .login-wrap { grid-template-columns: 1fr; gap: 30px; }
  .brand-side h1 { font-size: 24px; }
  .features, .chain-line { display: none; }
}
</style>
