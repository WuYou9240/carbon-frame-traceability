import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({ baseURL: '/api', timeout: 30000 })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('qts_token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

api.interceptors.response.use(
  (resp) => {
    const data = resp.data
    if (data && data.code !== undefined) {
      if (data.code === 0) return data.data
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('qts_token')
      localStorage.removeItem('qts_user')
      if (!location.hash.startsWith('#/login')) {
        ElMessage.warning('登录已过期，请重新登录')
        location.hash = '#/login'
      }
    } else if (err.response && err.response.data && err.response.data.message) {
      ElMessage.error(err.response.data.message)
    } else {
      ElMessage.error('网络异常')
    }
    return Promise.reject(err)
  }
)

export default api

export const currentUser = () => {
  try {
    return JSON.parse(localStorage.getItem('qts_user') || 'null')
  } catch {
    return null
  }
}

export const ROLE_NAME = {
  ADMIN: '系统管理员',
  OPERATOR: '生产操作员',
  QC: '质检员',
  DEALER: '经销商',
  CONSUMER: '消费者'
}

export const STATUS_NAME = {
  IN_PROD: '在产',
  CURED: '已固化',
  INSPECTED: '已质检',
  SHIPPED: '已发货'
}

export const STATUS_TYPE = {
  IN_PROD: 'info',
  CURED: 'warning',
  INSPECTED: 'success',
  SHIPPED: 'primary'
}

export const PROCESS_NAME = {
  CLOTH_SELECTION: '碳布选型',
  AUTO_CUTTING: '自动裁布',
  LAYUP: '人工铺层',
  CURING: '高温固化',
  CNC: 'CNC精加工',
  NDT: '无损检测'
}

export const QC_ITEM_NAME = {
  APPEARANCE: '外观',
  DIMENSION: '尺寸',
  MECHANIC: '力学性能'
}
