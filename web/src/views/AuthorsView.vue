<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h2 class="page-title">开发者画像</h2>
        <p class="page-desc">基于审查数据的个人质量档案 · 定位是成长助手, 不是绩效考核</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div v-if="authors.length" class="author-grid">
      <div v-for="a in authors" :key="a.author" class="author-card" @click="router.push(`/authors/${encodeURIComponent(a.author)}`)">
        <div class="a-head">
          <div class="avatar">{{ a.author.slice(0, 1).toUpperCase() }}</div>
          <div class="a-name-box">
            <div class="a-name">{{ a.author }}</div>
            <div class="a-sub">最近活跃 {{ fmtTime(a.lastActive) }}</div>
          </div>
          <div class="a-score" :class="scoreCls(a.avgScore)">
            {{ a.avgScore != null ? a.avgScore : '-' }}
          </div>
        </div>
        <div class="a-metrics">
          <div class="a-metric"><b>{{ a.reviews }}</b><span>审查次数</span></div>
          <div class="a-metric"><b>{{ a.findings }}</b><span>发现问题</span></div>
          <div class="a-metric"><b>{{ fmtTokens(a.tokens) }}</b><span>Token</span></div>
        </div>
      </div>
    </div>

    <el-empty v-else-if="!loading" description="还没有带作者信息的审查记录">
      <p class="empty-hint">
        作者信息来自: GitLab/GitHub/Gitee 的 PR 提交人, 或「本地仓库」审查时自动读取的 git 提交人。<br />
        跑一次本地仓库审查或接入代码平台后, 这里就会出现开发者档案。
      </p>
    </el-empty>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listAuthors } from '../api'
import { fmtTime } from '../utils/format'

const router = useRouter()
const loading = ref(true)
const authors = ref([])

async function load() {
  loading.value = true
  try {
    authors.value = await listAuthors()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function scoreCls(score) {
  if (score == null) return ''
  if (score >= 85) return 'good'
  if (score >= 60) return 'mid'
  return 'bad'
}

function fmtTokens(value) {
  return value > 10000 ? (value / 1000).toFixed(1) + 'k' : String(value ?? 0)
}

onMounted(load)
</script>

<style scoped>
.author-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}
.author-card {
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  cursor: pointer;
  transition: transform 0.18s, box-shadow 0.18s, border-color 0.18s;
}
.author-card:hover {
  transform: translateY(-3px);
  border-color: #d0d1fb;
  box-shadow: 0 12px 28px rgba(99, 102, 241, 0.12);
}
.a-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  font-size: 19px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  flex-shrink: 0;
}
.a-name-box {
  min-width: 0;
}
.a-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--ink-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.a-sub {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--ink-400);
}
.a-score {
  margin-left: auto;
  font-size: 26px;
  font-weight: 800;
  color: var(--ink-400);
}
.a-score.good {
  color: #16a34a;
}
.a-score.mid {
  color: #d97706;
}
.a-score.bad {
  color: #dc2626;
}
.a-metrics {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}
.a-metric {
  flex: 1;
  padding: 10px 0;
  border-radius: 10px;
  background: #f8fafd;
  text-align: center;
}
.a-metric b {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: var(--ink-900);
}
.a-metric span {
  font-size: 11px;
  color: var(--ink-400);
}
.empty-hint {
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--ink-400);
}
</style>
