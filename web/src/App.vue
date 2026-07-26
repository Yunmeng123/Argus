<template>
  <div class="layout">
    <aside class="aside">
      <div class="logo">
        <div class="logo-badge">👁</div>
        <div>
          <div class="logo-title">Argus</div>
          <div class="logo-sub">AI CODE REVIEW</div>
        </div>
      </div>
      <nav class="nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: activeMenu === item.path }"
        >
          <el-icon :size="16"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
      <div class="aside-foot">Argus v0.1.0 · MVP</div>
    </aside>
    <main class="main">
      <div class="main-inner">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, EditPen, HomeFilled, List, Setting, User } from '@element-plus/icons-vue'

const navItems = [
  { path: '/', label: '项目介绍', icon: HomeFilled },
  { path: '/new', label: '新建审查', icon: EditPen },
  { path: '/reviews', label: '审查记录', icon: List },
  { path: '/dashboard', label: '统计看板', icon: DataAnalysis },
  { path: '/authors', label: '开发者画像', icon: User },
  { path: '/config', label: '系统配置', icon: Setting }
]

const route = useRoute()
const activeMenu = computed(() => {
  if (route.path.startsWith('/reviews')) return '/reviews'
  if (route.path.startsWith('/authors')) return '/authors'
  return route.path
})
</script>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
}
.aside {
  display: flex;
  flex-direction: column;
  width: 228px;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  height: 100vh;
  background: linear-gradient(180deg, #151d33 0%, #0d1426 100%);
}
.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 22px 20px 18px;
}
.logo-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  font-size: 19px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.4);
}
.logo-title {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.02em;
}
.logo-sub {
  margin-top: 2px;
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.22em;
  color: #5b6880;
}
.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 12px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 14px;
  color: #8b96ad;
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}
.nav-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #dbe2ee;
}
.nav-item.active {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.95), rgba(139, 92, 246, 0.85));
  color: #fff;
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.35);
}
.aside-foot {
  margin-top: auto;
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 11px;
  color: #4d5a73;
}
.main {
  flex: 1;
  min-width: 0;
  padding: 30px 36px 56px;
}
.main-inner {
  max-width: 1200px;
  margin: 0 auto;
}
.page-enter-active,
.page-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
