<template>
  <div class="login-page">
    <section class="brand-panel">
      <div class="grid-bg"></div>
      <div class="brand-head">
        <div class="brand-logo">N</div>
        <div>
          <div class="brand-name">NCS RegTech</div>
          <div class="brand-sub">监管科技平台 · Regulatory Technology</div>
        </div>
      </div>

      <div class="brand-body">
        <div class="eyebrow">法规知识 · 知识图谱 · 本体推理</div>
        <h1>让监管要求<br />可检索、可推理、可追溯</h1>
        <p class="brand-desc">
          覆盖沪 / 港 / 台三地法规知识库，基于 pgvector 的条文语义检索，
          结合知识图谱与本体论 RAG，为合规团队提供可溯源的监管问答与差异比对。
        </p>

        <ul class="features">
          <li v-for="item in features" :key="item.title">
            <span class="mark"></span>
            <div>
              <div class="feature-title">{{ item.title }}</div>
              <div class="feature-desc">{{ item.desc }}</div>
            </div>
          </li>
        </ul>
      </div>

      <div class="brand-foot">
        <span>已接入 API 网关平台 · 全链路调用可观测</span>
        <span class="version">v1.0.0</span>
      </div>
    </section>

    <section class="form-panel">
      <div class="login-card">
        <div class="login-hd">
          <h2>登录控制台</h2>
          <p>使用平台账号登录，所有操作将记录审计日志</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onLogin">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="账号" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="密码"
              :prefix-icon="Lock"
            />
          </el-form-item>

          <div class="login-opts">
            <el-checkbox v-model="form.remember">记住我</el-checkbox>
            <span class="hint">忘记密码请联系管理员</span>
          </div>

          <el-button type="primary" class="login-btn" :loading="loading" @click="onLogin">
            登 录
          </el-button>
        </el-form>

        <div class="login-tip">
          内置管理员：<b>admin</b> / <b>Admin@123</b>
        </div>
      </div>

      <div class="form-foot">© 2026 NCS RegTech · 监管科技平台</div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore, login } from '@regtech/shared'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'Admin@123', remember: true })

const features = [
  { title: '法规知识库', desc: 'pgvector 语义召回 + 关键词混检，条文级溯源' },
  { title: '法律知识图谱', desc: '法规 / 条款 / 主题三层关联，可视化探索' },
  { title: '本体驱动 RAG', desc: '概念扩展检索，回答附本体命中与条文引用' }
]

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const onLogin = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await login({ username: form.username, password: form.password })
    store.setSession(
      data.token,
      { id: data.id, username: data.username, nickname: data.nickname, role: data.role },
      form.remember
    )
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect ? redirect : '/overview')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  background: #fff;
}

.brand-panel {
  position: relative;
  width: 56%;
  min-width: 520px;
  background: linear-gradient(150deg, #0C2A55 0%, #12386B 45%, #1B4E8C 100%);
  color: #fff;
  padding: 34px 48px 26px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.grid-bg {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(255, 255, 255, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.045) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(circle at 30% 30%, #000 0%, transparent 78%);
}

.brand-panel::after {
  content: '';
  position: absolute;
  right: -160px;
  bottom: -200px;
  width: 520px;
  height: 520px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(198, 161, 91, 0.22) 0%, transparent 65%);
}

.brand-head {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-logo {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, #C6A15B, #EBD9AE);
  color: #12386B;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.brand-sub {
  font-size: 10.5px;
  color: rgba(255, 255, 255, 0.45);
  margin-top: 2px;
}

.brand-body {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  max-width: 520px;
}

.eyebrow {
  font-size: 11.5px;
  letter-spacing: 0.18em;
  color: var(--rt-gold);
  margin-bottom: 16px;
}

h1 {
  font-size: 38px;
  line-height: 1.32;
  font-weight: 600;
  margin: 0 0 18px;
  letter-spacing: -0.01em;
}

.brand-desc {
  font-size: 13px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.6);
  margin: 0 0 30px;
}

.features {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 14px;
}

.features li {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.mark {
  width: 6px;
  height: 6px;
  margin-top: 7px;
  border-radius: 50%;
  background: var(--rt-gold);
  box-shadow: 0 0 0 3px rgba(198, 161, 91, 0.16);
  flex-shrink: 0;
}

.feature-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 2px;
}

.feature-desc {
  font-size: 11.5px;
  color: rgba(255, 255, 255, 0.5);
}

.brand-foot {
  position: relative;
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.36);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  padding-top: 14px;
}

.form-panel {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #F3F8FE;
  padding: 24px;
}

.login-card {
  width: 360px;
}

.login-hd {
  margin-bottom: 22px;
}

.login-hd h2 {
  font-size: 21px;
  margin: 0 0 6px;
  font-weight: 600;
}

.login-hd p {
  font-size: 12px;
  color: var(--rt-text-3);
  margin: 0;
}

.login-opts {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.hint {
  font-size: 11.5px;
  color: var(--rt-text-3);
}

.login-btn {
  width: 100%;
  height: 40px;
  font-size: 14px;
  letter-spacing: 0.2em;
}

.login-tip {
  margin-top: 18px;
  font-size: 11.5px;
  color: var(--rt-text-3);
  text-align: center;
}

.form-foot {
  position: absolute;
  bottom: 22px;
  font-size: 11px;
  color: #B4BAC5;
}

@media (max-width: 1024px) {
  .brand-panel {
    display: none;
  }
}
</style>
