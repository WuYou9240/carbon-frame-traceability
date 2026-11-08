<template>
  <el-container style="height:100%">
    <el-aside width="228px" class="aside">
      <div class="brand">
        <div class="logo">
          <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="9" cy="21" r="5.6" stroke="#8ec5ff" stroke-width="2"/>
            <circle cx="23" cy="21" r="5.6" stroke="#8ec5ff" stroke-width="2"/>
            <path d="M9 21L16 11L23 21M16 11L20 12.2M13.4 10H18" stroke="#fff" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
        <div class="brand-txt">
          <b>车架质量溯源</b>
          <span>FISCO BCOS · 质量存证</span>
        </div>
      </div>

      <div class="nav-label">工作台</div>
      <el-menu :default-active="$route.path" router class="menu"
        background-color="transparent" text-color="#9aa8bb" active-text-color="#ffffff">
        <el-menu-item v-for="m in visibleMenus" :key="m.path" :index="m.path">
          <span class="mi-icon">{{ m.icon }}</span>
          <span>{{ m.label }}</span>
        </el-menu-item>
      </el-menu>

      <div class="footer-link">
        <div class="scan-card" @click="goScan">
          <div class="scan-ico">📱</div>
          <div>
            <b>消费者扫码页</b>
            <span>输入编号即可验真</span>
          </div>
          <span class="arrow">→</span>
        </div>
        <p class="chain-tip">链上存证 · 数据不可篡改</p>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="head-left">
          <div class="crumb"><span class="root">工作台</span><span class="sep">/</span>{{ $route.meta.title || '概览' }}</div>
        </div>
        <div class="head-right">
          <span class="chain-badge"><i class="dot"></i> 链上节点正常</span>
          <el-dropdown @command="onCmd">
            <span class="user-info">
              <span class="avatar">{{ (user?.realName || user?.username || '?').slice(0, 1) }}</span>
              <span class="u-meta">
                <b>{{ user?.realName || user?.username }}</b>
                <small>{{ roleName }}</small>
              </span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { currentUser, ROLE_NAME } from '../api'

const router = useRouter()
const user = currentUser()
const roleName = computed(() => ROLE_NAME[user?.role] || user?.role)

// 菜单与后端接口权限保持一致，避免角色点进去只见空白页
const MENUS = [
  { path: '/stats', icon: '📊', label: '统计分析', roles: ['ADMIN', 'DEALER', 'QC'] },
  { path: '/frames', icon: '🚲', label: '车架档案', roles: ['ADMIN', 'OPERATOR', 'QC', 'DEALER'] },
  { path: '/materials', icon: '🧵', label: '原材料批次', roles: ['ADMIN', 'OPERATOR'] },
  { path: '/logistics', icon: '🚚', label: '物流记录', roles: ['ADMIN', 'DEALER'] },
  { path: '/chain', icon: '⛓️', label: '上链存证', roles: ['ADMIN', 'QC'] },
  { path: '/users', icon: '👥', label: '用户管理', roles: ['ADMIN'] },
  { path: '/logs', icon: '📝', label: '操作日志', roles: ['ADMIN'] },
]
const visibleMenus = computed(() => MENUS.filter(m => user && m.roles.includes(user.role)))

function onCmd(cmd) {
  if (cmd === 'logout') {
    localStorage.removeItem('qts_token')
    localStorage.removeItem('qts_user')
    router.push('/login')
  }
}

function goScan() {
  router.push('/trace')
}
</script>

<style scoped>
.aside {
  background: linear-gradient(180deg, #0b1220 0%, #0f1b2e 55%, #0c2137 100%);
  display: flex; flex-direction: column;
  border-right: 1px solid rgba(148, 163, 184, .08);
  position: relative;
}
.brand {
  display: flex; align-items: center; gap: 11px;
  padding: 20px 18px;
  border-bottom: 1px solid rgba(148, 163, 184, .1);
}
.logo {
  width: 38px; height: 38px; border-radius: 11px;
  display: grid; place-items: center;
  background: linear-gradient(140deg, rgba(37, 99, 235, .35), rgba(6, 182, 212, .28));
  border: 1px solid rgba(142, 197, 255, .35);
  box-shadow: 0 4px 14px -6px rgba(56, 148, 255, .7);
}
.logo svg { width: 24px; height: 24px; }
.brand-txt { display: flex; flex-direction: column; line-height: 1.35; }
.brand-txt b { color: #fff; font-size: 14.5px; letter-spacing: .4px; }
.brand-txt span { color: #64748b; font-size: 10.5px; letter-spacing: .3px; }

.nav-label {
  color: #4b5b70; font-size: 10.5px; letter-spacing: 2px;
  padding: 18px 20px 8px;
}
.menu { border: none; padding: 0 10px; }
.menu :deep(.el-menu-item) {
  height: 42px; line-height: 42px;
  border-radius: 9px; margin-bottom: 4px;
  font-size: 13.5px;
  padding-left: 12px !important;
  transition: background .2s, color .2s;
}
.menu :deep(.el-menu-item:hover) { background: rgba(148, 163, 184, .09); color: #dbe6f5; }
.menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(37, 99, 235, .9), rgba(37, 99, 235, .55));
  box-shadow: 0 6px 18px -10px rgba(37, 99, 235, .9);
  position: relative;
}
.menu :deep(.el-menu-item.is-active)::before {
  content: ""; position: absolute; left: -10px; top: 50%; transform: translateY(-50%);
  width: 3px; height: 18px; border-radius: 0 3px 3px 0; background: #7dd3fc;
}
.mi-icon { margin-right: 10px; font-size: 14px; }

.footer-link { margin-top: auto; padding: 14px; }
.scan-card {
  display: flex; align-items: center; gap: 10px;
  padding: 11px 12px;
  border-radius: 11px;
  background: rgba(56, 189, 248, .08);
  border: 1px solid rgba(56, 189, 248, .18);
  cursor: pointer;
  transition: background .2s, transform .2s;
}
.scan-card:hover { background: rgba(56, 189, 248, .14); transform: translateY(-1px); }
.scan-ico { font-size: 16px; }
.scan-card b { display: block; color: #cbe9ff; font-size: 12.5px; font-weight: 600; }
.scan-card span { color: #64809b; font-size: 10.5px; }
.scan-card .arrow { margin-left: auto; color: #38bdf8; font-size: 13px; }
.chain-tip { text-align: center; color: #3d4b5f; font-size: 10px; margin: 12px 0 2px; letter-spacing: .3px; }

.header {
  background: rgba(255, 255, 255, .86);
  backdrop-filter: blur(8px);
  display: flex; align-items: center; justify-content: space-between;
  border-bottom: 1px solid var(--qts-border);
  height: 60px;
}
.crumb { font-size: 13px; color: var(--qts-text); letter-spacing: .2px; display: flex; align-items: center; gap: 7px; }
.crumb .root { color: var(--qts-text-3); }
.crumb .sep { color: #d5deea; }
.head-right { display: flex; align-items: center; gap: 18px; }
.chain-badge {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 12px; color: #16a34a;
  background: #f0fdf4; border: 1px solid #dcfce7;
  padding: 4px 10px; border-radius: 999px;
}
.chain-badge .dot {
  width: 6px; height: 6px; border-radius: 50%; background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, .16);
}
.user-info { cursor: pointer; display: flex; align-items: center; gap: 9px; color: #333; }
.avatar {
  width: 32px; height: 32px; border-radius: 50%;
  display: grid; place-items: center;
  background: linear-gradient(135deg, #2563eb, #38bdf8);
  color: #fff; font-size: 13px; font-weight: 600;
}
.u-meta { display: flex; flex-direction: column; line-height: 1.3; }
.u-meta b { font-size: 12.5px; color: var(--qts-text); }
.u-meta small { font-size: 10.5px; color: var(--qts-text-3); }

.main { padding: 18px 20px; overflow: auto; }
.page-fade-enter-active, .page-fade-leave-active { transition: opacity .18s ease, transform .18s ease; }
.page-fade-enter-from { opacity: 0; transform: translateY(8px); }
.page-fade-leave-to { opacity: 0; transform: translateY(-4px); }
</style>
