<template>
  <aside class="sidebar" :class="{ open }">
    <div class="brand">
      <div class="brand-logo">N</div>
      <div class="brand-text">
        <div class="brand-name">{{ brand }}</div>
        <div class="brand-sub">监管科技平台 v1.0</div>
      </div>
    </div>

    <nav class="nav">
      <template v-for="(item, i) in menus" :key="i">
        <div v-if="item.path" class="nav-item" :class="{ active: isActive(item.path) }" @click="go(item.path)">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </div>
        <div v-else class="nav-group">
          <div class="nav-group-title">{{ item.title }}</div>
          <div
            v-for="child in item.children"
            :key="child.path"
            class="nav-item sub"
            :class="{ active: isActive(child.path) }"
            @click="go(child.path)"
          >
            <el-icon><component :is="child.icon" /></el-icon>
            <span>{{ child.title }}</span>
          </div>
        </div>
      </template>
    </nav>

    <div class="sidebar-foot">
      <span class="dot"></span>
      <span>已接入 API 网关</span>
    </div>
  </aside>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'

defineProps({
  menus: { type: Array, default: () => [] },
  brand: { type: String, default: 'NCS RegTech' },
  /** 窄屏抽屉展开状态 */
  open: { type: Boolean, default: false }
})

const emit = defineEmits(['navigate'])

const router = useRouter()
const route = useRoute()

const isActive = (path) => route.path === path || route.path.startsWith(path + '/')
const go = (path) => {
  router.push(path)
  emit('navigate')
}
</script>

<style scoped>
.sidebar {
  width: var(--rt-sidebar-w);
  flex-shrink: 0;
  background: linear-gradient(180deg, #12386B 0%, #0C2A55 100%);
  display: flex;
  flex-direction: column;
  color: rgba(255, 255, 255, 0.72);
}

.brand {
  height: var(--rt-topbar-h);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
}

.brand-logo {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: linear-gradient(135deg, #C6A15B 0%, #E3CE9C 100%);
  color: #12386B;
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-name {
  font-size: 13.5px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 0.01em;
}

.brand-sub {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.42);
  margin-top: 2px;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 10px 10px 16px;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 12px;
  border-radius: 7px;
  cursor: pointer;
  font-size: 13px;
  user-select: none;
  color: rgba(255, 255, 255, 0.68);
  transition: background 0.16s, color 0.16s;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.nav-item.active {
  background: rgba(198, 161, 91, 0.14);
  color: #F1DFB6;
  font-weight: 600;
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 0 2px 2px 0;
  background: var(--rt-gold);
}

.nav-item.sub {
  padding-left: 22px;
  font-size: 12.5px;
}

.nav-group {
  margin-bottom: 4px;
}

.nav-group-title {
  padding: 12px 12px 5px;
  font-size: 10.5px;
  letter-spacing: 0.08em;
  color: rgba(255, 255, 255, 0.32);
}

.sidebar-foot {
  padding: 10px 16px 14px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.42);
  display: flex;
  align-items: center;
  gap: 6px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4ADE80;
  box-shadow: 0 0 0 3px rgba(74, 222, 128, 0.14);
}

/* 窄屏：侧边栏改为抽屉，点菜单后自动收起 */
@media (max-width: 960px) {
  .sidebar {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 1001;
    width: 232px;
    transform: translateX(-100%);
    transition: transform 0.22s ease;
    box-shadow: 2px 0 16px rgba(12, 42, 85, 0.28);
  }

  .sidebar.open {
    transform: none;
  }

  .brand {
    height: var(--rt-topbar-h);
  }
}

@media (max-width: 400px) {
  .sidebar {
    width: 82vw;
  }
}
</style>
