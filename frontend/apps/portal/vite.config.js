import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const r = (p) => fileURLToPath(new URL(p, import.meta.url))

/**
 * 所有 /api 请求统一走 ai-gateway 的 jv-gateway-proxy（28090）：
 *  - 网关按 tb_gw_route 把 /api/regtech/** 分发到各微服务；
 *  - 控制台每次请求都会被网关采集，可在「日志追踪」中看到；
 *  - 后端换端口只改路由表，前端无需改动。
 */
const SERVICES = {
  '/api': 'http://127.0.0.1:28090'
}

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': r('./src'),
      '@regtech/shared': r('../../packages/shared/src'),
      '@regtech/ui': r('../../packages/ui/src'),
      '@regtech/module-overview': r('../../modules/overview'),
      '@regtech/module-kb': r('../../modules/kb'),
      '@regtech/module-graph': r('../../modules/graph'),
      '@regtech/module-ai': r('../../modules/ai'),
      '@regtech/module-ontology': r('../../modules/ontology'),
      '@regtech/module-system': r('../../modules/system')
    }
  },
  server: {
    host: '127.0.0.1',
    port: 28200,
    open: false,
    fs: { allow: [r('../..')] },
    proxy: Object.fromEntries(
      Object.entries(SERVICES).map(([prefix, target]) => [
        prefix,
        { target, changeOrigin: true }
      ])
    )
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1400,
    rollupOptions: {
      output: {
        manualChunks: {
          echarts: ['echarts'],
          element: ['element-plus', '@element-plus/icons-vue'],
          vendor: ['vue', 'vue-router', 'pinia', 'axios']
        }
      }
    }
  }
})
