<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h2 class="page-title">系统配置</h2>
        <p class="page-desc">修改后即时生效, 持久化到 data/argus-config.json</p>
      </div>
    </div>

    <el-card shadow="never" class="mb config-card">
      <template #header><span class="card-title">LLM 模型</span></template>
      <el-form :model="form" label-width="140px">
        <el-form-item label="Mock 模式">
          <div class="field">
            <el-switch v-model="form.llm.mock" />
            <p class="hint">开启后不调用真实模型, 返回示例结果(无 API Key 时可先跑通流程)</p>
          </div>
        </el-form-item>
        <el-form-item label="接口地址">
          <div class="field">
            <el-input v-model="form.llm.baseUrl" placeholder="OpenAI 兼容接口地址, 例: https://api.deepseek.com" />
            <p class="hint">系统会在该地址后拼接 /v1/chat/completions</p>
          </div>
        </el-form-item>
        <el-form-item label="模型名称">
          <div class="field">
            <el-input v-model="form.llm.model" placeholder="例: deepseek-chat" />
          </div>
        </el-form-item>
        <el-form-item label="API Key">
          <div class="field">
            <el-input v-model="form.llm.apiKey" type="password" show-password :placeholder="apiKeyPlaceholder" />
            <p class="hint">只写不读: 保存后仅显示掩码, 留空表示保持不变</p>
          </div>
        </el-form-item>
        <el-form-item label="Temperature">
          <div class="field">
            <el-input-number v-model="form.llm.temperature" :min="0" :max="2" :step="0.1" />
            <p class="hint">越低越稳定, 代码审查建议 0.2 以下</p>
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mb config-card">
      <template #header><span class="card-title">审查参数</span></template>
      <el-form :model="form" label-width="140px">
        <el-form-item label="单文件变更行上限">
          <div class="field">
            <el-input-number v-model="form.review.maxFileChangedLines" :min="1" :max="100000" />
            <p class="hint">变更行数超过上限的文件跳过审查(大文件塞满上下文反而降低质量)</p>
          </div>
        </el-form-item>
        <el-form-item label="单文件问题数上限">
          <div class="field">
            <el-input-number v-model="form.review.maxFindingsPerFile" :min="1" :max="50" />
          </div>
        </el-form-item>
        <el-form-item label="每日 Token 预算">
          <div class="field">
            <el-input-number v-model="form.review.dailyTokenBudget" :min="0" :max="100000000" :step="10000" />
            <p class="hint">0 = 不限制; 当日累计消耗达到预算后拒绝新审查, 防止 webhook 风暴烧钱</p>
          </div>
        </el-form-item>
        <el-form-item label="Verifier 复核">
          <div class="field">
            <el-switch v-model="form.review.verifierEnabled" />
            <p class="hint">Finder-Verifier 两段式: 每个文件的发现会再经一次"反驳式"复核, 剔除误报(成本约翻倍)</p>
          </div>
        </el-form-item>
        <el-form-item label="复核模型">
          <div class="field">
            <el-input v-model="form.llm.verifierModel" placeholder="留空 = 与主模型相同; 填不同模型即为级联复核" />
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mb config-card">
      <template #header><span class="card-title">GitLab 集成</span></template>
      <el-form :model="form" label-width="140px">
        <el-form-item label="GitLab 地址">
          <div class="field">
            <el-input v-model="form.gitlab.baseUrl" placeholder="例: https://gitlab.example.com (不带 /api/v4)" />
          </div>
        </el-form-item>
        <el-form-item label="Access Token">
          <div class="field">
            <el-input v-model="form.gitlab.token" type="password" show-password :placeholder="gitlabTokenPlaceholder" />
            <p class="hint">需要 api 权限的 Personal/Project Access Token, 用于拉取 MR 变更和回写评论</p>
          </div>
        </el-form-item>
        <el-form-item label="Webhook Secret">
          <div class="field">
            <el-input
              v-model="form.gitlab.webhookSecret"
              type="password"
              show-password
              :placeholder="form.gitlab.webhookSecretSet ? '已配置, 留空保持不变' : '自定义一个密码串'"
            />
            <p class="hint">
              在 GitLab 项目 Settings → Webhooks 添加: URL 填
              <code>http://本机IP:18080/api/webhook/gitlab</code>, Secret token 填此值, 勾选 Merge request events
            </p>
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mb config-card">
      <template #header><span class="card-title">GitHub 集成</span></template>
      <el-form :model="form" label-width="140px">
        <el-form-item label="Access Token">
          <div class="field">
            <el-input v-model="form.github.token" type="password" show-password
              :placeholder="form.github.tokenSet ? `已配置(${form.github.tokenMasked}), 留空保持不变` : '需要 repo 权限的 Personal Access Token'" />
          </div>
        </el-form-item>
        <el-form-item label="Webhook Secret">
          <div class="field">
            <el-input v-model="form.github.webhookSecret" type="password" show-password
              :placeholder="form.github.webhookSecretSet ? '已配置, 留空保持不变' : '自定义一个密码串'" />
            <p class="hint">
              仓库 Settings → Webhooks 添加: URL 填 <code>http://本机IP:18080/api/webhook/github</code>,
              Content type 选 application/json, Secret 填此值, 事件勾选 Pull requests。
              签名按 HMAC-SHA256 校验(X-Hub-Signature-256)
            </p>
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mb config-card">
      <template #header><span class="card-title">Gitee 集成</span></template>
      <el-form :model="form" label-width="140px">
        <el-form-item label="私人令牌">
          <div class="field">
            <el-input v-model="form.gitee.token" type="password" show-password
              :placeholder="form.gitee.tokenSet ? `已配置(${form.gitee.tokenMasked}), 留空保持不变` : 'Gitee 私人令牌(projects/pull_requests 权限)'" />
          </div>
        </el-form-item>
        <el-form-item label="Webhook 密码">
          <div class="field">
            <el-input v-model="form.gitee.webhookSecret" type="password" show-password
              :placeholder="form.gitee.webhookSecretSet ? '已配置, 留空保持不变' : '自定义一个密码串'" />
            <p class="hint">
              仓库管理 → WebHooks 添加: URL 填 <code>http://本机IP:18080/api/webhook/gitee</code>,
              选择"密码"验证方式填此值, 勾选 Pull Request 事件。当前版本 Gitee 以总结评论回写(不做行级锚定)
            </p>
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mb config-card">
      <template #header><span class="card-title">IM 通知</span></template>
      <el-form :model="form" label-width="140px">
        <el-form-item label="机器人 Webhook">
          <div class="field">
            <el-input v-model="form.notification.webhookUrl" placeholder="钉钉/企微自定义机器人 webhook 地址, 留空不通知" />
            <p class="hint">审查完成后推送结果摘要, 按 URL 自动识别钉钉/企微报文格式</p>
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="config-actions">
      <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
      <el-button @click="load">重置</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getConfig, updateConfig } from '../api'

const loading = ref(true)
const saving = ref(false)
const apiKeySet = ref(false)
const apiKeyMasked = ref('')
const gitlabTokenSet = ref(false)
const gitlabTokenMasked = ref('')
const form = ref({
  llm: { baseUrl: '', model: '', verifierModel: '', temperature: 0.2, mock: false, apiKey: '' },
  review: { maxFileChangedLines: 800, maxFindingsPerFile: 8, verifierEnabled: false, dailyTokenBudget: 0 },
  gitlab: { baseUrl: '', token: '', webhookSecret: '', webhookSecretSet: false },
  github: { token: '', webhookSecret: '', tokenSet: false, tokenMasked: '', webhookSecretSet: false },
  gitee: { token: '', webhookSecret: '', tokenSet: false, tokenMasked: '', webhookSecretSet: false },
  notification: { webhookUrl: '' }
})

const apiKeyPlaceholder = computed(() =>
  apiKeySet.value ? `已配置(${apiKeyMasked.value}), 留空保持不变` : '未配置, 请输入 API Key'
)

const gitlabTokenPlaceholder = computed(() =>
  gitlabTokenSet.value ? `已配置(${gitlabTokenMasked.value}), 留空保持不变` : '未配置'
)

async function load() {
  loading.value = true
  try {
    const config = await getConfig()
    form.value.llm.baseUrl = config.llm.baseUrl
    form.value.llm.model = config.llm.model
    form.value.llm.verifierModel = config.llm.verifierModel || ''
    form.value.llm.temperature = config.llm.temperature
    form.value.llm.mock = config.llm.mock
    form.value.llm.apiKey = ''
    form.value.review.maxFileChangedLines = config.review.maxFileChangedLines
    form.value.review.maxFindingsPerFile = config.review.maxFindingsPerFile
    form.value.review.verifierEnabled = config.review.verifierEnabled
    form.value.review.dailyTokenBudget = config.review.dailyTokenBudget || 0
    form.value.gitlab.baseUrl = config.gitlab.baseUrl || ''
    form.value.gitlab.token = ''
    form.value.gitlab.webhookSecret = ''
    form.value.gitlab.webhookSecretSet = config.gitlab.webhookSecretSet
    for (const platform of ['github', 'gitee']) {
      form.value[platform].token = ''
      form.value[platform].webhookSecret = ''
      form.value[platform].tokenSet = config[platform].tokenSet
      form.value[platform].tokenMasked = config[platform].tokenMasked || ''
      form.value[platform].webhookSecretSet = config[platform].webhookSecretSet
    }
    form.value.notification.webhookUrl = config.notification.webhookUrl || ''
    apiKeySet.value = config.llm.apiKeySet
    apiKeyMasked.value = config.llm.apiKeyMasked || ''
    gitlabTokenSet.value = config.gitlab.tokenSet
    gitlabTokenMasked.value = config.gitlab.tokenMasked || ''
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await updateConfig({
      llm: {
        baseUrl: form.value.llm.baseUrl,
        model: form.value.llm.model,
        verifierModel: form.value.llm.verifierModel,
        temperature: form.value.llm.temperature,
        mock: form.value.llm.mock,
        apiKey: form.value.llm.apiKey || null
      },
      review: {
        maxFileChangedLines: form.value.review.maxFileChangedLines,
        maxFindingsPerFile: form.value.review.maxFindingsPerFile,
        verifierEnabled: form.value.review.verifierEnabled,
        dailyTokenBudget: form.value.review.dailyTokenBudget
      },
      gitlab: {
        baseUrl: form.value.gitlab.baseUrl,
        token: form.value.gitlab.token || null,
        webhookSecret: form.value.gitlab.webhookSecret || null
      },
      github: {
        token: form.value.github.token || null,
        webhookSecret: form.value.github.webhookSecret || null
      },
      gitee: {
        token: form.value.gitee.token || null,
        webhookSecret: form.value.gitee.webhookSecret || null
      },
      notification: {
        webhookUrl: form.value.notification.webhookUrl
      }
    })
    ElMessage.success('配置已保存并即时生效')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.config-card {
  max-width: 860px;
}
.config-card :deep(.el-form) {
  max-width: 680px;
  padding-top: 4px;
}
.field {
  width: 100%;
}
.hint {
  margin-top: 5px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink-400);
}
.config-actions {
  max-width: 860px;
}
</style>
