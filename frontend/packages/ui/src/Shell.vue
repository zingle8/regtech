<template>
  <div class="shell">
    <Sidebar :menus="menus" :brand="brand" />
    <div class="shell-main">
      <HeaderBar />
      <main class="shell-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import Sidebar from './Sidebar.vue'
import HeaderBar from './Header.vue'

defineProps({
  menus: { type: Array, default: () => [] },
  brand: { type: String, default: 'NCS RegTech' }
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
</style>
