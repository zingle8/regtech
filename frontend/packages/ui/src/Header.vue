<template>
  <header class="topbar">
    <div class="crumb">
      <span class="page-title">{{ pageTitle }}</span>
      <span class="page-subtitle">{{ pageSubtitle }}</span>
    </div>
    <div class="topbar-right">
      <a class="gateway-link" href="http://127.0.0.1:28080" target="_blank" rel="noreferrer">
        <el-icon><DataLine /></el-icon>
        <span>网关控制台</span>
      </a>
      <el-dropdown trigger="click" @command="onCommand">
        <div class="user-chip">
          <el-avatar :size="26" class="avatar">{{ initial }}</el-avatar>
          <div class="user-meta">
            <div class="user-name">{{ nickname }}</div>
            <div class="user-role">{{ roleLabel }}</div>
          </div>
          <el-icon class="arrow"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore, logout as logoutApi } from '@regtech/shared'
import { ArrowDown, DataLine } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const pageTitle = computed(() => route.meta.title || '')
const pageSubtitle = computed(() => route.meta.subtitle || '')
const initial = computed(() => (store.nickname || 'U').slice(0, 1))
const nickname = computed(() => store.nickname)
const roleLabel = computed(() =>
  ({ ADMIN: '管理员', ANALYST: '合规分析员', VIEWER: '只读用户' }[store.role] || '合规分析员')
)

const onCommand = async (cmd) => {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
    try {
      await logoutApi()
    } catch (e) {
      /* 后端异常也要清理本地会话 */
    }
    store.forceLogout()
    router.replace('/login')
    ElMessage.success('已退出登录')
  }
}
</script>

<style scoped>
.topbar {
  height: var(--rt-topbar-h);
  flex-shrink: 0;
  background: var(--rt-card);
  border-bottom: 1px solid var(--rt-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
}

.crumb {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.page-title {
  font-size: 15px;
  font-weight: 600;
}

.page-subtitle {
  font-size: 12px;
  color: var(--rt-text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.gateway-link {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--rt-text-2);
  text-decoration: none;
  padding: 4px 8px;
  border-radius: 6px;
}

.gateway-link:hover {
  background: var(--sidebar-hover);
  color: var(--rt-navy);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 8px;
  cursor: pointer;
}

.user-chip:hover {
  background: var(--sidebar-hover);
}

.avatar {
  background: var(--rt-navy);
  font-size: 12px;
  color: #fff;
}

.user-meta {
  line-height: 1.25;
}

.user-name {
  font-size: 12.5px;
}

.user-role {
  font-size: 10.5px;
  color: var(--rt-text-3);
}

.arrow {
  font-size: 11px;
  color: var(--rt-text-3);
}
</style>
