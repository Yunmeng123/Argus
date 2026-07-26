<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">新建审查</h2>
        <p class="page-desc">粘贴 unified diff 或指定本地仓库, 由 AI 逐文件审查变更并给出行级问题</p>
      </div>
    </div>
    <el-alert
      v-if="mockOn"
      type="warning"
      show-icon
      :closable="false"
      class="mb"
      title="当前为 mock 模式：不会调用真实模型, 只返回示例结果。可在「系统配置」中关闭。"
    />
    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane label="粘贴 Diff" name="diff">
          <el-input
            v-model="diffText"
            type="textarea"
            :rows="18"
            class="mono"
            placeholder="粘贴 git diff 输出的 unified diff 文本, 例如:
diff --git a/src/Foo.java b/src/Foo.java
--- a/src/Foo.java
+++ b/src/Foo.java
@@ -10,3 +10,4 @@
..."
          />
        </el-tab-pane>
        <el-tab-pane label="本地仓库" name="local">
          <el-form :model="localForm" label-width="110px" class="local-form">
            <el-form-item label="仓库路径" required>
              <el-input v-model="localForm.repoPath" placeholder="例: D:/repos/my-project" />
            </el-form-item>
            <el-form-item label="基准引用">
              <el-input v-model="localForm.baseRef" placeholder="例: master (两个引用都留空 = 审查未提交的工作区变更)" />
            </el-form-item>
            <el-form-item label="目标引用">
              <el-input v-model="localForm.headRef" placeholder="例: feature/xxx (与基准引用按 merge-base 取差异, 同 MR 语义)" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <div v-if="loading" class="progress-box">
        <el-progress :percentage="progressPct" :stroke-width="10" :show-text="false" striped striped-flow />
        <p class="progress-text">{{ progressText }}</p>
      </div>
      <div class="actions">
        <el-button type="primary" size="large" class="cta" :loading="loading" @click="submit">
          {{ loading ? '审查中...' : '开始审查' }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getConfig, getReviewJob, submitReviewJob } from '../api'

const router = useRouter()
const tab = ref('diff')
const diffText = ref('')
const localForm = ref({ repoPath: '', baseRef: '', headRef: '' })
const loading = ref(false)
const mockOn = ref(false)
const job = ref(null)
let pollTimer = null

const progressPct = computed(() => {
  if (!job.value || !job.value.totalFiles) return 8
  return Math.min(96, Math.round((job.value.doneFiles / job.value.totalFiles) * 100))
})

const progressText = computed(() => {
  if (!job.value) return '提交中...'
  if (job.value.status === 'QUEUED') return '任务排队中...'
  if (!job.value.totalFiles) return '解析 diff 并准备审查...'
  const current = job.value.currentFile ? ` · ${job.value.currentFile.split('/').pop()}` : ''
  return `正在审查 ${job.value.doneFiles}/${job.value.totalFiles} 个文件${current}`
})

onMounted(async () => {
  try {
    const config = await getConfig()
    mockOn.value = config.llm.mock
  } catch {
    /* 配置读取失败不阻塞页面 */
  }
})

onUnmounted(() => clearInterval(pollTimer))

async function submit() {
  const payload = {}
  if (tab.value === 'diff') {
    if (!diffText.value.trim()) {
      ElMessage.warning('请先粘贴 diff 内容')
      return
    }
    payload.diff = diffText.value
  } else {
    if (!localForm.value.repoPath.trim()) {
      ElMessage.warning('请填写仓库路径')
      return
    }
    payload.repoPath = localForm.value.repoPath.trim()
    payload.baseRef = localForm.value.baseRef.trim() || null
    payload.headRef = localForm.value.headRef.trim() || null
  }
  loading.value = true
  job.value = null
  try {
    job.value = await submitReviewJob(payload)
    pollTimer = setInterval(poll, 1500)
  } catch (e) {
    loading.value = false
    ElMessage.error(e.message)
  }
}

async function poll() {
  try {
    job.value = await getReviewJob(job.value.jobId)
  } catch (e) {
    stopPolling()
    ElMessage.error(e.message)
    return
  }
  if (job.value.status === 'DONE') {
    stopPolling()
    ElMessage.success(`审查完成: AI 评分 ${job.value.score != null ? job.value.score : '-'} 分, 发现 ${job.value.findingCount} 个问题`)
    router.push(`/reviews/${job.value.reviewId}`)
  } else if (job.value.status === 'FAILED') {
    stopPolling()
    ElMessage.error(job.value.error || '审查失败')
  }
}

function stopPolling() {
  clearInterval(pollTimer)
  pollTimer = null
  loading.value = false
}
</script>

<style scoped>
.actions {
  margin-top: 16px;
  text-align: right;
}
.local-form {
  max-width: 640px;
  padding-top: 8px;
}
.cta {
  padding: 0 30px;
  border: none;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.35);
}
.cta:hover:not(.is-loading) {
  opacity: 0.9;
}
.progress-box {
  margin-top: 18px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fafbff;
}
.progress-text {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--ink-400);
}
</style>
