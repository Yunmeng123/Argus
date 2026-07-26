<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">审查记录</h2>
        <p class="page-desc">按时间倒序展示历史审查, 点击任意行查看详情</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>
    <el-card shadow="never" :body-style="{ padding: '6px 8px 10px' }">
      <el-table
        :data="items"
        v-loading="loading"
        empty-text="还没有审查记录, 去「新建审查」跑一次吧"
        @row-click="(row) => router.push(`/reviews/${row.reviewId}`)"
        class="clickable"
      >
        <el-table-column label="审查 ID" width="240">
          <template #default="{ row }">
            <span class="rid">{{ row.reviewId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
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
            <span v-if="row.score != null" class="score" :class="scoreCls(row.score)">{{ row.score }}</span>
            <span v-else class="score-none">-</span>
          </template>
        </el-table-column>
        <el-table-column label="作者" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.author">{{ row.author }}</span>
            <span v-else class="score-none">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="模型" width="170" show-overflow-tooltip />
        <el-table-column label="文件" width="100">
          <template #default="{ row }">{{ row.reviewedFiles }} / {{ row.totalFiles }}</template>
        </el-table-column>
        <el-table-column label="问题分布" min-width="260">
          <template #default="{ row }">
            <div v-if="row.findingCount" class="chips">
              <template v-for="s in severityOrder" :key="s">
                <SeverityTag v-if="row.severityCounts[s]" :severity="s" :count="row.severityCounts[s]" />
              </template>
            </div>
            <span v-else class="ok">✓ 无问题</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listReviews } from '../api'
import { fmtTime } from '../utils/format'
import SeverityTag from '../components/SeverityTag.vue'

const router = useRouter()
const items = ref([])
const loading = ref(false)
const severityOrder = ['BLOCKER', 'MAJOR', 'MINOR', 'INFO']

async function load() {
  loading.value = true
  try {
    items.value = await listReviews()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function sourceLabel(source) {
  return { MANUAL: '手动', GITLAB: 'GitLab', GITHUB: 'GitHub', GITEE: 'Gitee', EVAL: '评测' }[source] || source
}

function scoreCls(score) {
  if (score >= 85) return 'score-good'
  if (score >= 60) return 'score-mid'
  return 'score-bad'
}

function sourceType(source) {
  return { MANUAL: 'info', GITLAB: 'primary', EVAL: 'warning' }[source] || 'info'
}

onMounted(load)
</script>

<style scoped>
.rid {
  font-family: var(--font-mono);
  font-size: 12.5px;
  color: var(--el-color-primary);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ok {
  color: #16a34a;
  font-size: 13px;
  font-weight: 500;
}
.score {
  font-weight: 700;
  font-size: 14px;
}
.score-good {
  color: #16a34a;
}
.score-mid {
  color: #d97706;
}
.score-bad {
  color: #dc2626;
}
.score-none {
  color: var(--ink-400);
}
.clickable :deep(.el-table__row) {
  cursor: pointer;
}
</style>
