<template>
  <div class="rt-page">
    <div class="rt-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="账号 / 姓名 / 邮箱"
        clearable
        style="width: 240px"
        :prefix-icon="Search"
        @keyup.enter="load"
        @clear="load"
      />
      <el-select v-model="query.role" placeholder="全部角色" clearable style="width: 140px" @change="load">
        <el-option label="管理员" value="ADMIN" />
        <el-option label="合规分析员" value="ANALYST" />
        <el-option label="只读用户" value="VIEWER" />
      </el-select>
      <div class="rt-toolbar-right">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openForm()">新增用户</el-button>
      </div>
    </div>

    <div class="rt-card">
      <el-table :data="users" size="small" empty-text="暂无用户">
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="26" class="avatar">{{ (row.nickname || row.username).slice(0, 1) }}</el-avatar>
              <div>
                <div class="user-name">{{ row.nickname || row.username }}</div>
                <div class="user-sub">{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.role === 'ADMIN' ? 'warning' : 'info'">
              {{ roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              size="small"
              @change="(val) => toggleStatus(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="170">
          <template #default="{ row }">{{ formatDate(row.lastLoginTime, true) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="right">
          <template #default="{ row }">
            <el-button size="small" text @click="openForm(row)">编辑</el-button>
            <el-button size="small" text @click="resetPassword(row)">重置密码</el-button>
            <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="rt-card-ft">
        <span class="rt-sm rt-dim">共 {{ total }} 个账号</span>
        <el-pagination
          v-model:current-page="query.current"
          :page-size="query.size"
          :total="total"
          layout="prev, pager, next"
          small
          @current-change="load"
        />
      </div>
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑用户' : '新增用户'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="账号" required>
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录账号" />
        </el-form-item>
        <el-form-item :label="form.id ? '新密码' : '密码'">
          <el-input v-model="form.password" type="password" show-password :placeholder="form.id ? '留空表示不修改' : '留空默认 Regtech@123'" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="合规分析员" value="ANALYST" />
            <el-option label="只读用户" value="VIEWER" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { formatDate } from '@regtech/shared'
import { changeUserStatus, createUser, deleteUser, fetchUsers, resetUserPassword, updateUser } from '../api'

const users = ref([])
const total = ref(0)
const query = reactive({ keyword: '', role: '', current: 1, size: 10 })

const dialog = ref(false)
const saving = ref(false)
const form = reactive({ id: null, username: '', password: '', nickname: '', email: '', role: 'ANALYST', status: 1 })

const roleLabel = (role) => ({ ADMIN: '管理员', ANALYST: '合规分析员', VIEWER: '只读用户' }[role] || role)

const load = async () => {
  const data = await fetchUsers({ ...query })
  users.value = data.records || []
  total.value = data.total || 0
}

const openForm = (row) => {
  Object.assign(form, {
    id: row ? row.id : null,
    username: row ? row.username : '',
    password: '',
    nickname: row ? row.nickname : '',
    email: row ? row.email : '',
    role: row ? row.role : 'ANALYST',
    status: row ? row.status : 1
  })
  dialog.value = true
}

const save = async () => {
  if (!form.username) {
    ElMessage.warning('请填写账号')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    delete payload.id
    if (form.id) {
      await updateUser(form.id, payload)
    } else {
      await createUser(payload)
    }
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (row, val) => {
  await changeUserStatus(row.id, val ? 1 : 0)
  row.status = val ? 1 : 0
  ElMessage.success(val ? '已启用' : '已禁用')
}

const resetPassword = async (row) => {
  const { value } = await ElMessageBox.prompt('输入新密码（留空使用默认 Regtech@123）', '重置密码', {
    inputPlaceholder: '新密码',
    inputValue: ''
  }).catch(() => ({ value: null }))
  if (value === null) return
  await resetUserPassword(row.id, value || 'Regtech@123')
  ElMessage.success('密码已重置，旧令牌已失效')
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确认删除账号「${row.username}」？`, '删除用户', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.avatar {
  background: var(--rt-navy);
  font-size: 11px;
}

.user-name {
  font-size: 12.5px;
}

.user-sub {
  font-size: 10.5px;
  color: var(--rt-text-3);
}
</style>
