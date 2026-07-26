<template>
  <div class="home">
    <!-- ============ Hero ============ -->
    <section class="hero anim" style="--d: 0">
      <div class="hero-left">
        <div class="hero-badge">
          <span class="dot" />AI Code Review Platform
        </div>
        <h1 class="hero-title">
          让每一次提交<br />
          都经过 <span class="grad">AI 的审视</span>
        </h1>
        <p class="hero-sub">
          Argus 接入你的 GitLab 工作流：MR 提交即自动审查, 行级评论精确锚定,
          Finder-Verifier 双段复核压制误报, 每次变更都有 0~100 的质量评分与总评。
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" class="cta" @click="router.push('/new')">
            开始一次审查 →
          </el-button>
          <el-button size="large" class="ghost" @click="router.push('/dashboard')">查看统计看板</el-button>
        </div>
        <div v-if="stats" class="hero-stats">
          <div class="hstat">
            <b>{{ stats.totalReviews }}</b><span>累计审查</span>
          </div>
          <i class="vline" />
          <div class="hstat">
            <b>{{ stats.totalFindings }}</b><span>发现问题</span>
          </div>
          <i class="vline" />
          <div class="hstat">
            <b>100<em>%</em></b><span>评测集召回率</span>
          </div>
        </div>
      </div>

      <!-- 纯 CSS 绘制的审查结果视觉 -->
      <div class="hero-visual" aria-hidden="true">
        <div class="v-card v-card--back">
          <div class="v-row"><span class="pill pill-major">MAJOR</span><span class="v-code">OrderService.java:34</span></div>
          <div class="v-line w80"></div>
          <div class="v-line w60"></div>
        </div>
        <div class="v-card v-card--front">
          <div class="v-head">
            <span class="v-eye">👁</span>
            <div>
              <div class="v-title">审查完成</div>
              <div class="v-time">2 个文件 · 12.4s</div>
            </div>
            <div class="v-score">31<small>/100</small></div>
          </div>
          <div class="v-row"><span class="pill pill-blocker">BLOCKER</span><span class="v-code">UserQueryService.java:20</span></div>
          <div class="v-finding">SQL 语句直接拼接用户输入, 存在注入风险</div>
          <div class="v-fix">建议: 使用 PreparedStatement 参数绑定</div>
          <div class="v-row mt6"><span class="pill pill-major">MAJOR</span><span class="v-code">UserQueryService.java:18</span></div>
          <div class="v-finding">Connection / Statement 未关闭, 资源泄漏</div>
        </div>
      </div>
    </section>

    <!-- ============ 核心能力 ============ -->
    <section class="sect anim" style="--d: 1">
      <h2 class="sect-title">核心能力</h2>
      <p class="sect-sub">不是"把 diff 扔给大模型"的玩具 —— 每一层都为真实工程场景设计</p>
      <div class="feature-grid">
        <div v-for="f in features" :key="f.title" class="feature">
          <div class="f-icon">{{ f.icon }}</div>
          <div class="f-title">{{ f.title }}</div>
          <div class="f-desc">{{ f.desc }}</div>
        </div>
      </div>
    </section>

    <!-- ============ 审查流水线 ============ -->
    <section class="sect anim" style="--d: 2">
      <h2 class="sect-title">审查流水线</h2>
      <p class="sect-sub">从 diff 到行级评论, 每一步都可解释、可度量</p>
      <div class="pipeline">
        <template v-for="(s, i) in pipeline" :key="s.name">
          <div class="pstep">
            <div class="p-no">{{ i + 1 }}</div>
            <div class="p-name">{{ s.name }}</div>
            <div class="p-desc">{{ s.desc }}</div>
          </div>
          <div v-if="i < pipeline.length - 1" class="p-arrow">→</div>
        </template>
      </div>
    </section>

    <!-- ============ 快速开始 ============ -->
    <section class="sect anim" style="--d: 3">
      <h2 class="sect-title">三步开始</h2>
      <div class="steps">
        <div class="step" @click="router.push('/config')">
          <div class="s-no">01</div>
          <div class="s-title">配置模型</div>
          <div class="s-desc">在「系统配置」填入任意 OpenAI 兼容 API 的地址与 Key, 保存即生效; 也可先用 mock 模式体验流程</div>
          <span class="s-link">去配置 →</span>
        </div>
        <div class="step" @click="router.push('/new')">
          <div class="s-no">02</div>
          <div class="s-title">提交变更</div>
          <div class="s-desc">粘贴 git diff、指定本地仓库分支, 或在 GitLab 项目里配好 Webhook 让 MR 自动触发</div>
          <span class="s-link">新建审查 →</span>
        </div>
        <div class="step" @click="router.push('/reviews')">
          <div class="s-no">03</div>
          <div class="s-title">查看结果</div>
          <div class="s-desc">AI 评分与总评、行级问题定位、修复建议、Markdown 报告, GitLab 场景直接回写 MR 评论</div>
          <span class="s-link">审查记录 →</span>
        </div>
      </div>
    </section>

    <!-- ============ 技术栈 ============ -->
    <section class="sect anim tech-sect" style="--d: 4">
      <div class="tech-badges">
        <span v-for="t in techs" :key="t" class="tech">{{ t }}</span>
      </div>
      <p class="foot">👁 Argus · 单命令可运行 · 内嵌 H2 零依赖部署 · MCP 端点 /sse 可被 Claude Code / Cursor 直连</p>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getStats } from '../api'

const router = useRouter()
const stats = ref(null)

const features = [
  { icon: '🔀', title: 'GitLab MR 自动审查', desc: 'Webhook 触发, 秒回异步处理; 行级评论按 diff 双坐标精确锚定; 同 commit 幂等, 高频 push 自动合并任务' },
  { icon: '🛡️', title: 'Finder-Verifier 双段复核', desc: '复核模型对每条发现做"反驳式"质检, 站不住脚的直接剔除; 复核模型可独立配置, 天然支持级联' },
  { icon: '💯', title: 'AI 评分与总评', desc: '每次审查输出 0~100 质量分(按变更行数加权)与结论总评 —— 即使没有问题, 也告诉你为什么' },
  { icon: '🧠', title: '上下文增强', desc: 'JavaParser 解析仓库源文件, 把变更行所在的完整方法喂给模型, 拒绝"只看 diff 片段"的断章取义' },
  { icon: '📏', title: '评测集量化', desc: '内置埋点用例, 一键跑出召回率 / 精确率; prompt、模型、上下文策略的每次调整都有数字护航' },
  { icon: '🔌', title: 'MCP Server', desc: '审查能力通过 MCP 协议暴露, Claude Code / Cursor 等 AI 客户端可直接调用 argus_review_diff 工具' }
]

const pipeline = [
  { name: '解析', desc: 'unified diff → 行号双坐标' },
  { name: '过滤', desc: '锁文件/二进制/超大变更' },
  { name: '增强', desc: '提取变更行完整方法' },
  { name: 'Finder', desc: '多维度并行审查 ×4' },
  { name: 'Verifier', desc: '反驳式复核降误报' },
  { name: '交付', desc: '评分 · 报告 · MR 评论' }
]

const techs = ['Spring Boot 3', 'Spring AI', 'Vue 3', 'Element Plus', 'H2 Database', 'JavaParser', 'MCP', 'GitLab API']

onMounted(async () => {
  try {
    stats.value = await getStats()
  } catch {
    /* 未鉴权或后端未就绪时静默, 首页仍可浏览 */
  }
})
</script>

<style scoped>
/* ---------- 进场动画 ---------- */
.anim {
  opacity: 0;
  animation: rise 0.55s cubic-bezier(0.22, 1, 0.36, 1) forwards;
  animation-delay: calc(var(--d) * 90ms);
}
@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* ---------- Hero ---------- */
.hero {
  display: flex;
  align-items: center;
  gap: 48px;
  padding: 34px 8px 46px;
}
.hero-left {
  flex: 1;
  min-width: 0;
}
.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: #6366f1;
  background: #eff0fe;
  border: 1px solid #dfe0fc;
}
.hero-badge .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #6366f1;
  animation: pulse 1.8s infinite;
}
@keyframes pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(99, 102, 241, 0.45);
  }
  55% {
    box-shadow: 0 0 0 6px rgba(99, 102, 241, 0);
  }
}
.hero-title {
  margin: 18px 0 14px;
  font-size: 40px;
  line-height: 1.22;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--ink-900);
}
.grad {
  background: linear-gradient(120deg, #6366f1, #a855f7);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.hero-sub {
  max-width: 520px;
  font-size: 14.5px;
  line-height: 1.9;
  color: var(--ink-500);
}
.hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}
.cta {
  padding: 0 26px;
  border: none;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 6px 18px rgba(99, 102, 241, 0.35);
}
.cta:hover {
  opacity: 0.92;
}
.ghost {
  border-radius: 10px;
}
.hero-stats {
  display: flex;
  align-items: center;
  gap: 22px;
  margin-top: 30px;
}
.hstat b {
  display: block;
  font-size: 24px;
  font-weight: 800;
  color: var(--ink-900);
}
.hstat b em {
  font-style: normal;
  font-size: 14px;
}
.hstat span {
  font-size: 12px;
  color: var(--ink-400);
}
.vline {
  width: 1px;
  height: 30px;
  background: var(--line);
}

/* ---------- Hero 视觉卡片 ---------- */
.hero-visual {
  position: relative;
  width: 400px;
  height: 320px;
  flex-shrink: 0;
}
.v-card {
  position: absolute;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.1);
  padding: 18px 20px;
}
.v-card--back {
  top: 6px;
  right: 0;
  width: 300px;
  transform: rotate(4deg);
  opacity: 0.65;
}
.v-card--front {
  top: 52px;
  left: 0;
  width: 330px;
  animation: floaty 5s ease-in-out infinite;
}
@keyframes floaty {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}
.v-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid #f1f4f9;
}
.v-eye {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  font-size: 17px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
}
.v-title {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--ink-900);
}
.v-time {
  font-size: 11px;
  color: var(--ink-400);
}
.v-score {
  margin-left: auto;
  font-size: 24px;
  font-weight: 800;
  color: #dc2626;
}
.v-score small {
  font-size: 12px;
  color: var(--ink-400);
  font-weight: 600;
}
.v-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mt6 {
  margin-top: 10px;
}
.pill {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
}
.pill-blocker {
  color: #b91c1c;
  background: #fdeeee;
}
.pill-major {
  color: #c2410c;
  background: #fff2e6;
}
.v-code {
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--ink-500);
}
.v-finding {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--ink-700);
}
.v-fix {
  margin-top: 4px;
  font-size: 11.5px;
  color: #16a34a;
}
.v-line {
  height: 9px;
  border-radius: 5px;
  background: #eef1f7;
  margin-top: 10px;
}
.w80 {
  width: 80%;
}
.w60 {
  width: 60%;
}

/* ---------- 通用 section ---------- */
.sect {
  padding: 34px 8px 10px;
}
.sect-title {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--ink-900);
  text-align: center;
}
.sect-sub {
  margin-top: 8px;
  margin-bottom: 26px;
  text-align: center;
  font-size: 13px;
  color: var(--ink-400);
}

/* ---------- 能力网格 ---------- */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 14px;
}
.feature {
  padding: 22px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  transition: transform 0.18s, box-shadow 0.18s, border-color 0.18s;
}
.feature:hover {
  transform: translateY(-3px);
  border-color: #d0d1fb;
  box-shadow: 0 12px 28px rgba(99, 102, 241, 0.12);
}
.f-icon {
  font-size: 24px;
}
.f-title {
  margin: 10px 0 6px;
  font-size: 15px;
  font-weight: 700;
  color: var(--ink-900);
}
.f-desc {
  font-size: 13px;
  line-height: 1.8;
  color: var(--ink-500);
}

/* ---------- 流水线 ---------- */
.pipeline {
  display: flex;
  align-items: stretch;
  gap: 6px;
  overflow-x: auto;
  padding: 4px 2px 12px;
}
.pstep {
  flex: 1;
  min-width: 130px;
  padding: 16px 14px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
  text-align: center;
  transition: border-color 0.18s, box-shadow 0.18s;
}
.pstep:hover {
  border-color: #d0d1fb;
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.1);
}
.p-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
}
.p-name {
  margin-top: 8px;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--ink-900);
}
.p-desc {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--ink-400);
}
.p-arrow {
  align-self: center;
  color: #c7cdf8;
  font-weight: 700;
}

/* ---------- 三步开始 ---------- */
.steps {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px;
}
.step {
  position: relative;
  padding: 22px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  cursor: pointer;
  transition: transform 0.18s, box-shadow 0.18s, border-color 0.18s;
}
.step:hover {
  transform: translateY(-3px);
  border-color: #d0d1fb;
  box-shadow: 0 12px 28px rgba(99, 102, 241, 0.12);
}
.s-no {
  font-size: 26px;
  font-weight: 800;
  background: linear-gradient(120deg, #c7cdf8, #ddd0fb);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.s-title {
  margin: 6px 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--ink-900);
}
.s-desc {
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--ink-500);
}
.s-link {
  display: inline-block;
  margin-top: 12px;
  font-size: 12.5px;
  font-weight: 600;
  color: #6366f1;
}

/* ---------- 技术栈 ---------- */
.tech-sect {
  padding-bottom: 30px;
}
.tech-badges {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}
.tech {
  padding: 6px 16px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-500);
  background: #fff;
}
.foot {
  margin-top: 26px;
  text-align: center;
  font-size: 12px;
  color: var(--ink-400);
}

/* ---------- 响应式 ---------- */
@media (max-width: 1080px) {
  .hero-visual {
    display: none;
  }
}
</style>
