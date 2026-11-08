<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>用户管理</h2>
        <p>系统账号与五类角色权限分配</p>
      </div>
    </div>
  <div class="page-card">
    <div class="mb" style="display:flex;gap:10px">
      <el-input v-model="keyword" placeholder="账号/姓名" style="width:180px" clearable @keyup.enter="load" />
      <el-select v-model="role" placeholder="角色" style="width:150px" clearable>
        <el-option v-for="(n, k) in ROLE_NAME" :key="k" :label="n" :value="k" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button type="success" @click="dlg = true">新增用户</el-button>
    </div>
    <el-table :data="rows" stripe>
      <el-table-column prop="username" label="账号" width="130" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column label="角色" width="120">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'">{{ ROLE_NAME[row.role] || row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="edit(row)">编辑</el-button>
          <el-button link type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:14px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="dlg" :title="form.id ? '编辑用户' : '新增用户'" width="460px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="账号"><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width:100%">
            <el-option v-for="(n, k) in ROLE_NAME" :key="k" :label="n" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="初始密码" v-if="!form.id"><el-input v-model="form.password" placeholder="默认 Qts@2026" /></el-form-item>
        <el-form-item label="状态" v-if="form.id">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import api, { ROLE_NAME } from '../api'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const role = ref('')
const dlg = ref(false)
const form = reactive({})

async function load() {
  const data = await api.get('/users', { params: { current: page.value, size: 10, keyword: keyword.value, role: role.value } })
  rows.value = data.records
  total.value = Number(data.total)
}

function edit(row) {
  Object.assign(form, row)
  dlg.value = true
}

async function save() {
  if (form.id) {
    await api.put('/users/' + form.id, form)
  } else {
    await api.post('/users', form)
  }
  ElMessage.success('已保存')
  dlg.value = false
  Object.keys(form).forEach(k => delete form[k])
  load()
}

async function del(row) {
  await ElMessageBox.confirm('确定删除用户 ' + row.username + '？', '提示', { type: 'warning' })
  await api.delete('/users/' + row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
