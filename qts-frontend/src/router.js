import { createRouter, createWebHashHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

const routes = [
  { path: '/login', component: () => import('./views/Login.vue') },
  {
    path: '/',
    component: () => import('./views/Layout.vue'),
    redirect: '/stats',
    children: [
      { path: 'stats', component: () => import('./views/StatsView.vue'), meta: { title: '统计分析', roles: ['ADMIN', 'DEALER', 'QC'] } },
      { path: 'frames', component: () => import('./views/FrameList.vue'), meta: { title: '车架档案', roles: ['ADMIN', 'OPERATOR', 'QC', 'DEALER'] } },
      { path: 'frames/:id', component: () => import('./views/FrameDetail.vue'), meta: { title: '车架详情', roles: ['ADMIN', 'OPERATOR', 'QC', 'DEALER'] } },
      { path: 'materials', component: () => import('./views/MaterialList.vue'), meta: { title: '原材料批次', roles: ['ADMIN', 'OPERATOR'] } },
      { path: 'logistics', component: () => import('./views/LogisticsView.vue'), meta: { title: '物流记录', roles: ['ADMIN', 'DEALER'] } },
      { path: 'chain', component: () => import('./views/ChainView.vue'), meta: { title: '上链存证', roles: ['ADMIN', 'QC'] } },
      { path: 'users', component: () => import('./views/UserView.vue'), meta: { title: '用户管理', roles: ['ADMIN'] } },
      { path: 'logs', component: () => import('./views/LogView.vue'), meta: { title: '操作日志', roles: ['ADMIN'] } },
    ]
  },
  // 消费者扫码溯源页（公开）
  { path: '/trace', component: () => import('./views/Trace.vue') },
  { path: '/trace/:serial', component: () => import('./views/Trace.vue') },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

function localUser() {
  try { return JSON.parse(localStorage.getItem('qts_user') || 'null') } catch { return null }
}

// 各角色默认首页：消费者没有内部工作台，落到公开的扫码查询页；其余角色取第一个有权菜单
function homeFor(user) {
  if (user?.role === 'CONSUMER') return '/trace'
  const children = routes.find(r => r.path === '/')?.children || []
  const first = children.find(r => r.meta?.roles?.includes(user?.role))
  return first ? '/' + first.path : '/login'
}

router.beforeEach((to) => {
  const publicPages = ['/login']
  const isPublic = publicPages.includes(to.path) || to.path.startsWith('/trace')
  const token = localStorage.getItem('qts_token')
  if (!isPublic && !token) {
    ElMessage.warning('请先登录')
    return '/login'
  }
  // 页面级角色守卫：与后端接口权限一致，手输 URL 也进不去无权页面
  if (!isPublic && to.meta.roles?.length) {
    const user = localUser()
    if (!user || !to.meta.roles.includes(user.role)) {
      if (!user) {
        ElMessage.warning('请先登录')
        return '/login'
      }
      if (to.path !== homeFor(user)) {
        ElMessage.warning('您没有访问该页面的权限')
        return homeFor(user)
      }
    }
  }
  document.title = (to.meta.title ? to.meta.title + ' - ' : '') + '碳纤维车架质量溯源系统'
})

export default router
