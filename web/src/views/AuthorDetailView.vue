<template>
  <div class="page" v-loading="loading">
    <template v-if="profile">
      <div class="page-head">
        <div class="head-left">
          <div class="avatar">{{ profile.card.author.slice(0, 1).toUpperCase() }}</div>
          <div>
            <h2 class="page-title">{{ profile.card.author }}</h2>
            <p class="page-desc">
              {{ profile.card.reviews }} 次审查 · {{ profile.card.findings }} 个问题 ·
              最近活跃 {{ fmtTime(profile.card.lastActive) }}
            </p>
          </div>
        </div>
        <el-button @click="router.push('/authors')">← 返回列表</el-button>
      </div>

      <div v-if="profile.tags.length" class="tags mb">
        <span v-for="t in profile.tags" :key="t" class="tag">{{ t }}</span>
      </div>

      <div class="grid mb">
        <el-card shadow="never" :body-style="{ padding: '18px 20px' }">
          <template #header><span class="card-title">质量分趋势(近 {{ profile.scoreTrend.length }} 次)</span></template>
          <div class="trend-wrap">
            <div class="avg-score" :class="scoreCls(profile.card.avgScore)">
              {{ profile.card.avgScore != null ? profile.card.avgScore : '-' }}<small>平均分</small>
            </div>
            <svg v-if="trendPoints.length > 1" class="trend-svg" viewBox="0 0 300 90" preserveAspectRatio="none">
              <polyline :points="trendLine" fill="none" stroke="#6366f1" stroke-width="2.5"
                stroke-linecap="round" stroke-linejoin="round" />
              <circle v-for="(p, i) in trendPoints" :key="i" :cx="p.x" :cy="p.y" r="3.5" fill="#6366f1" />
            </svg>
            <p v-else class="trend-empty">分数样本不足, 再多跑几次审查</p>
          </div>
        </el-card>

        <el-card shadow="never" :body-style="{ padding: '18px 20px' }">
          <template #header><span class="card-title">问题分类分布</span></template>
          <div v-if="categoryRows.length" class="bars">
            <div v-for="row in categoryRows" :key="row.name" class="bar-row">
              <span class="bar-label">{{ row.name }}</span>
              <div class="bar-track">
                <div class="bar-fill" :style="{ width: row.pct + '%' }"></div>
              </div>
              <span class="bar-count">{{ row.count }}</span>
            </div>
          </div>
          <p v-else class="trend-empty">暂无问题记录 🎉</p>
        </el-card>
      </div>

      <el-card shadow="never" class="mb" :body-style="{ padding: '18px 20px' }">
        <template #header>
          <div class="ai-head">
            <span class="card-title">🧠 AI 成长画像</span>
            <el-button type="primary" size="small" :loading="generating" @click="generate">
              {{ profile.aiSummary ? (profile.aiSummary.stale ? '有新数据, 刷新画像' : '重新生成') : '生成画像' }}
            </el-button>
          </div>
        </template>
        <template v-if="profile.aiSummary">
          <p class="ai-text">{{ profile.aiSummary.content }}</p>
          <p class="ai-meta">
            生成于 {{ fmtTime(profile.aiSummary.generatedAt) }} · 基于 {{ profile.aiSummary.reviewCountAtGeneration }} 次审查
            <span v-if="profile.aiSummary.stale" class="stale">(此后有新审查数据, 建议刷新)</span>
          </p>
        </template>
        <p v-else class="trend-empty">
          点击「生成画像」, AI 会基于该开发者的审查数据总结优势、问题模式和改设建议(结果缓存, 不会重复计费)。
        </p>
      </el-card>

      <el-card shadow="never" :body-style="{ padding: '6px 8px 10px' }">
        <template #header><span class="card-title">近期问题({{ profile.recentFindings.length }})</span></template>
        <el-table :data="profile.recentFindings" size="small" empty-text="近期无问题记录"
          @row-click="(row) => router.push(`/reviews/${row.reviewId}`)" class="clickable">
          <el-table-column label="严重程度" width="110">
            <template #default="{ row }"><SeverityTag :severity="row.severity" /></template>
          </el-table-column>
          <el-table-column prop="category" label="分类" width="150" />
          <el-table-column label="文件" min-width="220" show-overflow-tooltip>
            <template #default="{ row }"><span class="file-path">{{ row.file }}:{{ row.line }}</span></template>
          </el-table-column>
          <el-table-column prop="title" label="问题" min-width="260" show-overflow-tooltip />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { genAuthorAiSummary, getAuthorProfile } from '../api'
import { fmtTime } from '../utils/format'
import SeverityTag from '../components/SeverityTag.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const generating = ref(false)
const profile = ref(null)

const trendPoints = computed(() => {
  if (!profile.value) return []
  // 后端按时间倒序, 图上按时间正序画
  const points = [...profile.value.scoreTrend].reverse().filter((p) => p.score != null)
  if (!points.length) return []
  const stepX = points.length > 1 ? 280 / (points.length - 1) : 0
  return points.map((p, i) => ({
    x: 10 + i * stepX,
    y: 82 - (p.score / 100) * 72
  }))
})

const trendLine = computed(() => trendPoints.value.map((p) => `${p.x},${p.y}`).join(' '))

const categoryRows = computed(() => {
  if (!profile.value) return []
  const entries = Object.entries(profile.value.categoryDistribution)
  const max = Math.max(1, ...entries.map(([, c]) => c))
  return entries.map(([name, count]) => ({ name, count, pct: Math.round((count / max) * 100) }))
})

async function load() {
  loading.value = true
  try {
    profile.value = await getAuthorProfile(route.params.author)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function generate() {
  generating.value = true
  try {
    const summary = await genAuthorAiSummary(route.params.author)
    profile.value.aiSummary = summary
    ElMessage.success('AI 画像已生成')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    generating.value = false
  }
}

function scoreCls(score) {
  if (score == null) return ''
  if (score >= 85) return 'good'
  if (score >= 60) return 'mid'
  return 'bad'
}

onMounted(load)
</script>

<style scoped>
.head-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  font-size: 23px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tag {
  padding: 4px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #6366f1;
  background: #eff0fe;
  border: 1px solid #dfe0fc;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
@media (max-width: 900px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
.trend-wrap {
  display: flex;
  align-items: center;
  gap: 18px;
}
.avg-score {
  font-size: 34px;
  font-weight: 800;
  color: var(--ink-400);
  flex-shrink: 0;
}
.avg-score small {
  display: block;
  font-size: 11px;
  font-weight: 500;
  color: var(--ink-400);
}
.avg-score.good {
  color: #16a34a;
}
.avg-score.mid {
  color: #d97706;
}
.avg-score.bad {
  color: #dc2626;
}
.trend-svg {
  flex: 1;
  height: 90px;
  min-width: 0;
}
.trend-empty {
  font-size: 12.5px;
  color: var(--ink-400);
  line-height: 1.8;
}
.bars {
  display: flex;
  flex-direction: column;
  gap: 9px;
}
.bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.bar-label {
  width: 130px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ink-500);
}
.bar-track {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: #f1f5f9;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #6366f1, #8b5cf6);
  transition: width 0.4s;
}
.bar-count {
  width: 28px;
  text-align: right;
  font-size: 12px;
  color: var(--ink-900);
}
.ai-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ai-text {
  font-size: 13.5px;
  line-height: 2;
  color: #374151;
  white-space: pre-line;
}
.ai-meta {
  margin-top: 10px;
  font-size: 11.5px;
  color: var(--ink-400);
}
.stale {
  color: #d97706;
}
.file-path {
  font-family: var(--font-mono);
  font-size: 12px;
}
.clickable :deep(.el-table__row) {
  cursor: pointer;
}
</style>
