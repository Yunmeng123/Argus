<template>
  <div class="page" v-loading="loading">
    <template v-if="result">
      <div class="page-head">
        <div>
          <h2 class="page-title">审查详情</h2>
          <p class="page-desc rid">{{ result.reviewId }} · {{ fmtTime(result.createdAt) }}</p>
        </div>
      </div>

      <div class="stats mb">
        <div class="stat-card">
          <div class="stat-label">AI 评分</div>
          <div class="stat-value" :class="scoreClass(result.score)">
            {{ result.score != null ? result.score : '-' }}
          </div>
          <div class="stat-foot">满分 100</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">模型</div>
          <div class="stat-value stat-value--text" :title="result.model">{{ result.model }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">审查文件</div>
          <div class="stat-value">
            {{ result.reviewedFiles }}<span class="stat-sub">/ {{ result.totalFiles }}</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Token 消耗</div>
          <div class="stat-value">{{ result.tokenUsage.totalTokens }}</div>
          <div class="stat-foot">
            输入 {{ result.tokenUsage.promptTokens }} · 输出 {{ result.tokenUsage.completionTokens }}
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-label">发现问题</div>
          <div class="stat-value" :class="result.findings.length ? 'stat-value--warn' : 'stat-value--ok'">
            {{ result.findings.length }}
          </div>
          <div v-if="result.verifierDropped" class="stat-foot">Verifier 已过滤 {{ result.verifierDropped }} 条疑似误报</div>
          <div v-else-if="!result.findings.length" class="stat-foot">未发现问题 🎉</div>
        </div>
      </div>

      <div v-if="result.summary" class="summary mb">
        <span class="summary-label">🧠 AI 总评</span>
        <p class="summary-text">{{ result.summary }}</p>
      </div>

      <el-card shadow="never" class="mb" :body-style="{ padding: '6px 8px 10px' }">
        <template #header>
          <div class="findings-head">
            <span class="card-title">问题列表 ({{ result.findings.length }})</span>
            <div class="chips">
              <template v-for="s in severityOrder" :key="s">
                <SeverityTag v-if="result.severityCounts[s]" :severity="s" :count="result.severityCounts[s]" />
              </template>
            </div>
          </div>
        </template>
        <el-table :data="result.findings" empty-text="本次审查未发现问题">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="expand">
                <p v-if="row.detail"><b>说明</b>{{ row.detail }}</p>
                <p v-if="row.suggestion"><b>建议</b>{{ row.suggestion }}</p>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="严重程度" width="120">
            <template #default="{ row }">
              <SeverityTag :severity="row.severity" />
            </template>
          </el-table-column>
          <el-table-column label="文件" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="file-path">{{ row.file }}</span>
            </template>
          </el-table-column>
          <el-table-column label="行号" width="90">
            <template #default="{ row }">
              {{ row.line }}
              <el-tooltip v-if="!row.lineVerified" content="行号未能与 diff 新增行对齐, 仅供参考">
                <span class="warn-mark">⚠</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="分类" width="150" />
          <el-table-column prop="title" label="问题" min-width="280" show-overflow-tooltip />
          <el-table-column label="置信度" width="90">
            <template #default="{ row }">{{ row.confidence.toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card v-if="result.skippedFiles.length" shadow="never" class="mb" :body-style="{ padding: '6px 8px 10px' }">
        <template #header>
          <span class="card-title">跳过的文件 ({{ result.skippedFiles.length }})</span>
        </template>
        <el-table :data="result.skippedFiles" size="small">
          <el-table-column label="文件" min-width="300" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="file-path">{{ row.path }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="200" />
        </el-table>
      </el-card>

      <p v-if="result.reportPath" class="report-path">Markdown 报告：{{ result.reportPath }}</p>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getReview } from '../api'
import { fmtTime } from '../utils/format'
import SeverityTag from '../components/SeverityTag.vue'

const route = useRoute()
const result = ref(null)
const loading = ref(true)
const severityOrder = ['BLOCKER', 'MAJOR', 'MINOR', 'INFO']

onMounted(async () => {
  try {
    result.value = await getReview(route.params.id)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
})

function scoreClass(score) {
  if (score == null) return ''
  if (score >= 85) return 'stat-value--ok'
  if (score >= 60) return 'stat-value--warn'
  return 'stat-value--bad'
}
</script>

<style scoped>
.rid {
  font-family: var(--font-mono);
}
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
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
  font-size: 24px;
  font-weight: 700;
  color: var(--ink-900);
  line-height: 1.2;
}
.stat-value--text {
  font-size: 15px;
  font-family: var(--font-mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 30px;
}
.stat-value--warn {
  color: #d97706;
}
.stat-value--ok {
  color: #16a34a;
}
.stat-value--bad {
  color: #dc2626;
}
.summary {
  padding: 14px 18px;
  border: 1px solid var(--line);
  border-left: 4px solid #6366f1;
  border-radius: 10px;
  background: #fff;
}
.summary-label {
  font-size: 12px;
  font-weight: 600;
  color: #6366f1;
}
.summary-text {
  margin-top: 6px;
  font-size: 13.5px;
  line-height: 1.8;
  color: #374151;
  white-space: pre-line;
}
.stat-sub {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-400);
}
.stat-foot {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-400);
}
.findings-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.file-path {
  font-family: var(--font-mono);
  font-size: 12.5px;
}
.expand {
  margin: 6px 12px;
  padding: 12px 18px;
  border-radius: 10px;
  background: #f8fafc;
  line-height: 1.9;
  color: #4b5563;
}
.expand b {
  margin-right: 10px;
  color: var(--ink-900);
}
.warn-mark {
  cursor: help;
}
.report-path {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--ink-400);
}
</style>
