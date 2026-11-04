<template>
  <div class="rt-stat" :class="{ accent }">
    <div class="rt-stat-label">{{ label }}</div>
    <div class="rt-stat-value">{{ display }}</div>
    <div class="rt-stat-foot">
      <span v-if="foot">{{ foot }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: { type: String, default: '' },
  value: { type: [Number, String], default: 0 },
  foot: { type: String, default: '' },
  accent: { type: Boolean, default: false }
})

const display = computed(() => {
  if (typeof props.value === 'number') {
    return props.value.toLocaleString('zh-CN')
  }
  return props.value ?? '-'
})
</script>
