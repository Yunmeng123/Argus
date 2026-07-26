<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h2 class="page-title">统计看板</h2>
        <p class="page-desc">基于近 200 条审查记录的汇总, 以及评测集质量指标</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="stats mb">
      <div class="stat-card">
        <div class="stat-label">审查次数</div>
        <div class="stat-value">{{ stats.totalReviews }}</div>
        <div class="stat-foot">
          <span v-for="(count, source) in stats.sourceTotals" :key="source" class="src-chip">
            {{ sourceLabel(source) }} {{ count }}
          </span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">累计发现问题</div>
        <div class="stat-value" :class="stats.totalFindings ? 'stat-value--warn' : ''">
          {{ stats.totalFindings }}
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">累计 Token 消耗</div>
        <div class="stat-value">{{ fmtTokens(stats.totalTokens) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">问题严重程度分布</div>
        <div class="sev-bars">
          <div v-for="s in severityOrder" :key="s" class="sev-bar-row">
            <span class="sev-bar-label">{{ s }}</span>
            <div class="sev-bar-track">
              <div
                class="sev-bar-fill"
                :class="'sev-' + s.toLowerCase()"
                :style="{ width: barWidth(stats.severityTotals[s]) }"
              ></div>
            </div>
            <span class="sev-bar-count">{{ stats.severityTotals[s] || 0 }}</span>
          </div>
        </div>
      </div>
    </div>

    <el-card shadow="never" class="mb" :body-style="{ padding: '16px 18px' }">
      <template #header>
        <div class="eval-head">
          <span class="card-title">评测集质量指标</span>
          <el-button type="primary" size="small" :loading="evalRunning" @click="doRunEval">
            {{ evalRunning ? '评测运行中(约1~2分钟)...' : '运行评测集' }}
          </el-button>
        </div>
      </template>
      <template v-if="evalReport">
        <div class="eval-metrics">
          <div class="metric">
            <span class="metric-value">{{ pct(evalReport.recall) }}</span>
            <span class="metric-label">召回率 (埋点 {{ evalReport.totalHits }}/{{ evalReport.totalExpected }})</span>
          </div>
          <div class="metric">
            <span class="metric-value">{{ pct(evalReport.precision) }}</span>
            <span class="metric-label">精确率 (有效 {{ evalReport.matchedFindings }}/{{ evalReport.totalFindings }})</span>
          </div>
          <div class="metric">
            <span class="metric-value">{{ evalReport.totalTokens }}</span>
            <span class="metric-label">本次评测 Token</span>
          </div>
        </div>
        <el-table :data="evalReport.cases" size="small" class="mt">
          <el-table-column prop="name" label="用例" width="150" />
          <el-table-column label="埋点命中" width="100">
            <template #default="{ row }">{{ row.hitCount }} / {{ row.expectedCount }}</template>
          </el-table-column>
          <el-table-column prop="findingCount" label="报告问题数" width="110" />
          <el-table-column label="漏报" min-width="180">
            <template #default="{ row }">
              <span v-if="row.missed.length" class="miss">{{ row.missed.join(', ') }}</span>
              <span v-else class="ok">无</span>
            </template>
          </el-table-column>
          <el-table-column label="额外报告(疑似误报)" min-width="220">
            <template #default="{ row }">
              <span v-if="row.unmatchedFindings.length" class="miss">{{ row.unmatchedFindings.join('; ') }}</span>
              <span v-else class="ok">无</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <p v-else class="eval-empty">
        评测集是 samples/eval/ 下埋了已知问题的 diff 用例。点击「运行评测集」用当前配置的模型跑一遍,
        得到召回率/精确率——每次调整 prompt 或模型后都建议跑一次防退化。
      </p>
    </el-card>

    <el-card shadow="never" :body-style="{ padding: '6px 8px 10px' }">
      <template #header><span class="card-title">最近审查</span></template>
      <el-table
        :data="stats.recent"
        empty-text="暂无审查记录"
        @row-click="(row) => router.push(`/reviews/${row.reviewId}`)"
        class="clickable"
      >
        <el-table-column label="审查 ID" width="230">
          <template #default="{ row }"><span class="rid">{{ row.reviewId }}</span></template>
        </el-table-column>
        <el-table-column label="时间" width="165">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="sourceType(row.source)" disable-transitions>
              {{ sourceLabel(row.source) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="80">
          <template #default="{ row }">
            <span v-if="row.score != null">{{ row.score }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="findingCount" label="问题数" width="90" />
        <el-table-column prop="totalTokens" label="Token" width="100" />
        <el-table-column prop="model" label="模型" min-width="160" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getStats, runEval } from '../api'
import { fmtTime } from '../utils/format'

const router = useRouter()
const loading = ref(true)
const evalRunning = ref(false)
const evalReport = ref(null)
const stats = ref({
  totalReviews: 0,
  totalFindings: 0,
  totalTokens: 0,
  severityTotals: {},
  sourceTotals: {},
  recent: []
})
const severityOrder = ['BLOCKER', 'MAJOR', 'MINOR', 'INFO']

async function load() {
  loading.value = true
  try {
    stats.value = await getStats()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function doRunEval() {
  try {
    await ElMessageBox.confirm('将用当前配置的模型运行全部评测用例(真实调用, 消耗少量 token), 继续?',
      '运行评测集', { type: 'info' })
  } catch {
    return
  }
  evalRunning.value = true
  try {
    evalReport.value = await runEval()
    ElMessage.success('评测完成')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    evalRunning.value = false
  }
}

function barWidth(count) {
  const max = Math.max(1, ...severityOrder.map((s) => stats.value.severityTotals[s] || 0))
  return Math.round(((count || 0) / max) * 100) + '%'
}

function pct(value) {
  return (value * 100).toFixed(1) + '%'
}

function fmtTokens(value) {
  return value > 10000 ? (value / 1000).toFixed(1) + 'k' : String(value)
}

function sourceLabel(source) {
  return { MANUAL: '手动', GITLAB: 'GitLab', GITHUB: 'GitHub', GITEE: 'Gitee', EVAL: '评测' }[source] || source
}

function sourceType(source) {
  return { MANUAL: 'info', GITLAB: 'primary', EVAL: 'warning' }[source] || 'info'
}

onMounted(load)
</script>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}
.stat-card {
  padding: 16px 18px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.stat-label {
  font-size: 12px;
  color: var(--ink-400);
  margin-bottom: 6px;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--ink-900);
  line-height: 1.2;
}
.stat-value--warn {
  color: #d97706;
}
.stat-foot {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ink-400);
}
.src-chip {
  margin-right: 10px;
}
.sev-bars {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 2px;
}
.sev-bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sev-bar-label {
  width: 66px;
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-400);
}
.sev-bar-track {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: #f1f5f9;
  overflow: hidden;
}
.sev-bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.4s;
}
.sev-blocker {
  background: #ef4444;
}
.sev-major {
  background: #f59e0b;
}
.sev-minor {
  background: #facc15;
}
.sev-info {
  background: #34d399;
}
.sev-bar-count {
  width: 30px;
  text-align: right;
  font-size: 12px;
  color: var(--ink-900);
}
.eval-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.eval-metrics {
  display: flex;
  gap: 40px;
}
.metric {
  display: flex;
  flex-direction: column;
}
.metric-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--ink-900);
}
.metric-label {
  font-size: 12px;
  color: var(--ink-400);
}
.mt {
  margin-top: 14px;
}
.eval-empty {
  color: var(--ink-400);
  font-size: 13px;
  line-height: 1.8;
}
.miss {
  color: #d97706;
  font-size: 12px;
}
.ok {
  color: #16a34a;
  font-size: 12px;
}
.rid {
  font-family: var(--font-mono);
  font-size: 12.5px;
  color: var(--el-color-primary);
}
.clickable :deep(.el-table__row) {
  cursor: pointer;
}
</style>
