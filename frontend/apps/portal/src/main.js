import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import '@regtech/shared/styles/index.css'
import '@regtech/shared/styles/components.css'

const app = createApp(App)

// 菜单与按钮通过字符串引用图标，这里统一全局注册
Object.entries(ElementPlusIcons).forEach(([name, component]) => {
  app.component(name, component)
})

app.use(createPinia()).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
