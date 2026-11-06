<template>
  <div class="shell">
    <Sidebar :menus="menus" :brand="brand" :open="navOpen" @navigate="navOpen = false" />
    <div v-if="navOpen" class="shell-mask" @click="navOpen = false"></div>
    <div class="shell-main">
      <HeaderBar @toggle-nav="navOpen = !navOpen" />
      <main class="shell-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './Sidebar.vue'
import HeaderBar from './Header.vue'

defineProps({
  menus: { type: Array, default: () => [] },
  brand: { type: String, default: 'NCS RegTech' }
})

/** 窄屏（≤960px）侧边栏以抽屉形式展开 */
const navOpen = ref(false)
const route = useRoute()
watch(() => route.fullPath, () => {
  navOpen.value = false
})
</script>

<style scoped>
.shell {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.shell-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.shell-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

@media (max-width: 960px) {
  .shell-mask {
    position: fixed;
    inset: 0;
    z-index: 1000;
    background: rgba(12, 42, 85, 0.42);
  }
}
</style>
