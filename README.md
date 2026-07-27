# Argus — AI 代码审查 Agent

<img width="1536" height="1024" alt="图片" src="https://github.com/user-attachments/assets/5bfe4c95-28ac-454d-b7ae-bce1ae1bcbec" />

基于 Spring Boot 3 + Spring AI + Vue 3 的 AI 代码审查平台。支持手动提交 diff、审查本地仓库，
以及 **GitLab / GitHub / Gitee Webhook 自动触发 + PR 行级评论回写**（统一 VcsProvider 适配层）。
内建 Finder-Verifier 两段式误报治理、JavaParser 上下文增强、AI 评分总评、**开发者画像（含 AI 成长建议）**、
评测集质量量化、统计看板、MCP Server 与 IM 通知。

> Roadmap 全部落地, 单命令可运行(内嵌 H2, 无外部中间件依赖)。
> 真实模型实测: 评测集召回率 100%, 精确率 75%(未开 Verifier 的基线)。

## 系统架构

![Argus 智能 AI 代码审查 Agent 平台架构图](docs/architecture.jpg)

## 功能全景与核心架构

### 1. 全链路工作流 (End-to-End Workflow)

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       1. 触发层 (Triggers)                                        │
│  ┌───────────────────────┐   ┌─────────────────────────────────────────┐   ┌──────────────────┐  │
│  │   手动提交 diff /      │   │  GitLab / GitHub / Gitee Webhook        │   │  MCP Server      │  │
│  │   本地 Git 仓库引用   │   │  (验签 ➔ 异步队列 ➔ 任务合并 ➔ 幂等去重)  │   │  (Claude/Cursor) │  │
│  └───────────┬───────────┘   └────────────────────┬────────────────────┘   └────────┬─────────┘  │
└──────────────┼────────────────────────────────────┼─────────────────────────────────┼────────────┘
               │                                    │                                 │
               └────────────────────────────────────┼─────────────────────────────────┘
                                                    ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                     2. 核心审查引擎 (Pipeline)                                    │
│                                                                                                  │
│  ┌────────────────────────┐    ┌────────────────────────┐    ┌────────────────────────────────┐  │
│  │  UnifiedDiffParser     │ ➔ │  ReviewFileFilter      │ ➔ │  ContextEnhancer               │  │
│  │  (解析 diff/行号双坐标) │    │  (大文件/类型黑白名单) │    │  (JavaParser AST完整方法提取) │  │
│  └────────────────────────┘    └────────────────────────┘    └────────────────────────────────┘  │
│                                                                               │                  │
│  ┌────────────────────────┐    ┌────────────────────────┐    ┌────────────────┘                  │
│  │  Finder (主 AI 模型)   │ ➔ │  Verifier (复核 AI)    │ ➔ │  Line Number Verifier          │  │
│  │  (文件并行×4 提取问题) │    │  (反驳式质检/误报剔除)  │    │  (行号真实对齐校验 lineVerified)│  │
│  └────────────────────────┘    └────────────────────────┘    └────────────────────────────────┘  │
└───────────────────────────────────────────────────┬──────────────────────────────────────────────┘
                                                    │
                                                    ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                     3. 结果输出与回写 (Outputs)                                   │
│  ┌───────────────────────┐   ┌─────────────────────────────────────────┐   ┌──────────────────┐  │
│  │  Web 管理看板          │   │  代码托管平台回写                        │   │  即时通讯通知    │  │
│  │  (得分/记录/开发者画像)│   │  (MR/PR 行级评论 Inline + Markdown 总结) │   │  (钉钉/企业微信) │  │
│  └───────────────────────┘   └─────────────────────────────────────────┘   └──────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 2. 核心功能矩阵 (Capability Matrix)

| 核心模块 | 功能特性 | 技术实现与工程设计亮点 |
|---|---|---|
| **多通道接入** | 平台适配与协议代理 | 支援 **Web UI 手动粘贴 Diff / 本地仓库路径**；支持 **GitLab / GitHub / Gitee** 三平台 Webhook；内置 **MCP Server (`/sse`)**，直接对接 Claude Code / Cursor。 |
| **异步可靠性** | 高并发与风暴防护 | Webhook 接收端秒级响应 202，解耦后台任务队列；具备 **同一 Commit 幂等去重** 与 **高频 Push 自动任务合并**；内置 **每日 Token 预算闸门**。 |
| **上下文工程** | AST 语法树增强 | 基于 **JavaParser** 提取变更行所在完整方法上下文，解决传统 Diff 片段审阅造成的“断章取义”问题；自研 **UnifiedDiffParser** 维护新旧双坐标行号。 |
| **误报治理体系** | Finder-Verifier 双阶段机制 | **Finder (主模型)** 并行（×4）抽取潜在缺陷 ➔ **Verifier (复核模型)** 进行“反驳式”质检降误报 ➔ **LineVerified 校验** 确保行号准确精准锚定。 |
| **评价与统计** | 质量量化与指标评估 | 基于变更行数加权算法生成 **AI 代码质量评分（0-100分）** 与 Markdown 总结；内置 **评测集 (`samples/eval/`) 跑分引擎**，自动化评估召回率 (Recall) 与精确率 (Precision)。 |
| **开发者画像** | AI 成长助手与质量档案 | 按提交人维度聚合历史审查数据，生成**代码得分趋势、问题类型分布、中性能力标签与 AI 成长建议**（侧重指导成长，非绩效考核工具）。 |
| **系统控制** | 运行时配置与安全 | 支持 **图形化运行时配置中心**（API Key/模型改动即时生效）；**机密字段掩码+只写不读**；支持 `ARGUS_ACCESS_TOKEN` 访问鉴权。 |

## 快速开始与部署指南

### 1. 环境依赖说明

在启动 Argus 之前，请确保您的开发运行环境满足以下要求：

- **Java JDK**: JDK 17 或以上版本（推荐 OpenJDK 17 / Eclipse Temurin 17 / Amazon Corretto 17）。
- **Build Tool**: Maven 3.6+。
- **Node.js**: Node 18.x+ / 20.x+ (配套 npm 9+，仅在构建或二次开发 Vue3 前端时需要)。
- **数据库**: **零额外安装依赖**！内置 H2 文件模式数据库（数据存储在 `./data/argus.mv.db` 中，启动时自动创建表结构）。

---

### 2. 快速启动（三种体验模式）

#### 模式 A：单体整合一键启动（推荐生产/日常体验）

前端 Vue3 构建产物会直接输出至 Spring Boot 的 `static` 目录中，实现单端口（`18080`）一站式部署：

```powershell
# 1. 克隆/进入项目根目录
cd D:\Project\argus

# 2. 编译前端静态资源（产物自动放入 src/main/resources/static/）
cd web
npm install
npm run build
cd ..

# 3. 启动后端应用
mvn spring-boot:run

# 4. 浏览器访问
# 打开 http://localhost:18080
```

#### 模式 B：Jar 包打包部署

支持将其打包为可独立运行的平铺 Jar 包，适合服务器部署：

```powershell
# 打包应用（跳过单元测试）
mvn clean package -DskipTests

# 启动运行 Jar
java -jar target/argus-0.1.0-SNAPSHOT.jar
```

#### 模式 C：Mock 极速体验模式（免 LLM Key 快速跑通）

如果您暂时没有 OpenAI 兼容的 API Key，也可以直接开启 Mock 模式跑通全流程：
1. 启动应用后打开 `http://localhost:18080` 进入 **「系统配置」** 页面。
2. 将 **Mock 模式** 开关切换为 `启用` 并保存。
3. 提交任何 diff 或本地仓库引用，系统将返回样例审查报告，供您快速评估交互流程。

---

### 3. 前后端分离二次开发模式

如果您需要修改前端页面或扩展 UI 功能，推荐使用 Vite 的热重载开发模式：

1. **启动后端服务**：
   ```powershell
   mvn spring-boot:run
   ```
   *后端服务监听端口：`18080`*

2. **启动前端开发服务**：
   ```powershell
   cd web
   npm install
   npm run dev
   ```
   *前端服务监听端口：`5173`，所有以 `/api` 开头的请求均会自动代理转发至 `http://localhost:18080`*

3. **访问开发环境**：
   打开 `http://localhost:5173`，修改 `web/src/` 代码可实现秒级 HMR 实时热更新。

---

### 4. 系统配置与环境变量说明

系统启动后，配置生效有两种途径：

#### 途径一：Web 图形化配置中心（推荐）
访问 `http://localhost:18080` -> 左侧菜单栏 **「系统配置」**，在线修改保存后**实时生效**（持久化写入根目录 `data/argus-config.json`，无需重启服务）。

#### 途径二：环境变量覆盖（适合 Docker / K8s / CI/CD 自动化）
可在启动时传入以下环境变量覆盖默认种子配置：

| 环境变量 | 默认值 / 示例 | 说明 |
|---|---|---|
| `ARGUS_LLM_BASE_URL` | `https://api.deepseek.com` | OpenAI 兼容接口地址（**警告**：末尾请勿带 `/v1`，Spring AI 会自动拼接） |
| `ARGUS_LLM_API_KEY` | `sk-xxxxxxxx` | LLM API 密钥（机密字段，日志全程掩码） |
| `ARGUS_LLM_MODEL` | `deepseek-chat` | 主审查 AI 模型名称 |
| `ARGUS_LLM_VERIFIER_MODEL` | *(空)* | 复核模型（级联反驳式剔除误报，留空表示使用主模型） |
| `ARGUS_ACCESS_TOKEN` | *(空)* | 访问鉴权令牌（留空为开放模式；配置后接口及 `/sse` 需带 `X-Argus-Token` 头） |
| `ARGUS_NOTIFY_WEBHOOK_URL` | *(空)* | 钉钉 / 企业微信机器人 Webhook 推送地址 |
| `ARGUS_GITLAB_BASE_URL` | `https://gitlab.example.com` | GitLab 自建实例 Base URL |
| `ARGUS_GITLAB_TOKEN` | `glpat-xxxxxxxx` | GitLab API Access Token (需 `api` 权限，用于回写 MR 行级评论) |
| `ARGUS_GITLAB_WEBHOOK_SECRET`| `secret-key` | GitLab Webhook 验签 Token |
| `ARGUS_GITHUB_TOKEN` | `ghp_xxxxxxxx` | GitHub Personal Access Token (用于 PR 回写) |
| `ARGUS_GITHUB_WEBHOOK_SECRET`| `secret-key` | GitHub Webhook Secret (HMAC-SHA256 验签) |

---

### 5. 平台集成与 MCP 扩展

#### (1) 代码托管平台自动审查 (GitLab / GitHub / Gitee Webhook)

系统提供统一 Webhook 入口：`http://<部署机IP>:18080/api/webhook/{platform}`：

| 平台 | Webhook 配置路径 | 验签机制 | 自动回写能力 |
|---|---|---|---|
| **GitLab** | 仓库 -> Settings -> Webhooks (勾选 `Merge request events`) | `X-Gitlab-Token` 明文比对 | 行级评论 (Diff position 锚定) + Markdown 汇总 |
| **GitHub** | 仓库 -> Settings -> Webhooks (Content type 选择 `json`，勾选 `Pull requests`) | **HMAC-SHA256** (`X-Hub-Signature-256`) | 行级评论 (line + side 映射) + Markdown 汇总 |
| **Gitee** | 仓库 -> 仓库管理 -> WebHooks (选择密码方式，勾选 `Pull Request`) | `X-Gitee-Token` 密码比对 | PR 总结评论 (行级暂自动降级) |

提交/更新 PR 即自动审查；同一 commit 具备幂等去重特性，高频 push 自动合并任务只审查最新 commit。

#### (2) MCP Server 接入 (让 Cursor / Claude Code 调用审查)

Argus 内置 MCP Server，暴露 SSE 端点 `http://localhost:18080/sse`。
在你的 MCP 客户端（如 Cursor 的 `mcp.json` 或 Claude Desktop 配置文件）中添加：

```json
{
  "mcpServers": {
    "argus": {
      "url": "http://localhost:18080/sse"
    }
  }
}
```
配置完成后即可在对话中直接调用工具 `argus_review_diff`。

---

### 6. 常见踩坑与 FAQ

- ❓ **Q: LLM 请求返回 404，或者提示无法解析 `/v1/v1/chat/completions`？**
  - **原因**：Spring AI 的 OpenAI 模块在 `base-url` 之后会自动拼接 `/v1/chat/completions`。
  - **解法**：在配置页或环境变量中填写 Base URL 时，**千万不要以 `/v1` 结尾**！例如 DeepSeek 填 `https://api.deepseek.com` 即可。

- ❓ **Q: PowerShell 控制台 curl/接口测试时返回的中文显示乱码？**
  - **原因**：PowerShell 5.1 默认解码格式未强制使用 UTF-8。
  - **解法**：可参考项目内脚本 [scripts/review-sample.ps1](file:///D:/Project/argus/scripts/review-sample.ps1) 使用 `[System.Text.Encoding]::UTF8` 转换流；或建议使用 Git Bash / Linux / Postman 进行接口调试。

- ❓ **Q: 如何可视化查看内嵌 H2 数据库的数据？**
  - **解法**：修改 `src/main/resources/application.yml` 中的 `spring.h2.console.enabled: true`，然后访问 `http://localhost:18080/h2-console`。JDBC URL 填写 `jdbc:h2:file:./data/argus`，用户名 `sa`，密码留空即可。

- ❓ **Q: 如何保证部署在公网/局域网时的安全？**
  - **解法**：在启动时配置环境变量 `ARGUS_ACCESS_TOKEN=你的自定义Token`。系统开启鉴权模式后，所有 `/api/**` 接口与 `/sse` 均需附带 `X-Argus-Token: 你的自定义Token` 请求头（前端会在返回 401 时自动弹框提示引导输入并本地记住）。

## 页面

| 页面 | 功能 |
|---|---|
| 新建审查 | 粘贴 diff / 本地仓库两个引用, 提交并跳转结果页 |
| 审查记录 | 历史列表(来源标签/问题分布/token), 点击进详情 |
| 审查详情 | **AI 质量评分(0~100)** + AI 总评 + 问题表格(展开看说明与建议) + Verifier 过滤数 + 跳过文件 |
| 统计看板 | 审查次数/问题总量/Token 成本/严重程度分布 + **一键运行评测集**(召回率/精确率) |
| 开发者画像 | 按提交人聚合的质量档案: 平均分/分数趋势/问题分类分布/中性标签 + **AI 成长画像**(优势/问题模式/改进建议, 结果缓存) |
| 系统配置 | LLM/审查参数/Verifier/GitLab/GitHub/Gitee/IM 通知, 全部即时生效; 机密字段只写不读 |

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/review/jobs` | **异步提交审查**(diff 或本地仓库), 秒回 jobId |
| GET | `/api/review/jobs/{id}` | 查任务进度(正在审查第几个文件)与结果 |
| POST | `/api/review/diff` | 同步审查 unified diff(供脚本/MCP 用) |
| POST | `/api/review/local` | 同步审查本地仓库(带 JavaParser 上下文增强) |
| POST | `/api/webhook/{platform}` | gitlab/github/gitee Webhook 统一入口(各自验签, 秒回 202) |
| GET | `/api/reviews` / `/api/reviews/{id}` | 审查记录列表/详情 |
| GET | `/api/authors` / `/api/authors/{author}` | 开发者列表 / 完整画像 |
| POST | `/api/authors/{author}/ai-summary` | 生成/刷新 AI 成长画像(缓存) |
| GET | `/api/stats` | 看板聚合数据 |
| POST | `/api/eval/run` | 运行评测集, 返回召回率/精确率 |
| GET/PUT | `/api/config` | 运行时配置(机密字段掩码/只写) |
| GET | `/sse` | MCP Server SSE 端点 |

## 设计要点

- **行号双坐标追踪**: 解析 diff 时每行维护 old/new 行号, GitLab 行级评论的 position 锚定全靠它;
  LLM 报告的行号还要经过"是否真实落在新增行"校验(lineVerified), 锚不上的降级进总结评论。
- **误报治理**: prompt 约束(宁可漏报) + confidence 阈值 + **Finder-Verifier 两段式**——
  复核模型对每条发现做"反驳式"质检, 站不住脚的剔除; 复核模型可配置为不同模型(级联)。
- **上下文工程**: 本地仓库审查时用 JavaParser 提取"变更行所在完整方法"喂给模型,
  解决"只看 diff 片段导致的断章取义"; 解析失败安静降级。
- **异步可靠性**: webhook 秒回 202(GitLab 10s 超时会重推) → 进程内队列异步审查;
  同 MR 高频 push 任务合并只审最新 commit; 同 commit 幂等(DB 唯一性判定)。
  前端手动审查同样异步化(job + 轮询进度), 大 MR 不再同步等待超时。
  队列接口有意收敛, 需要横向扩展时可平移到 RabbitMQ。
- **成本与容错**: LLM 调用显式重试(3 次指数退避); 每日 Token 预算闸门(超预算拒绝新审查,
  防 webhook 风暴烧钱); 访问令牌鉴权(机密字段只写不读 + h2-console 默认关闭)。
- **效果评测**: samples/eval/ 埋点用例 + `/api/eval/run` 量化召回/精确率,
  prompt/模型/上下文策略每次调整跑一遍防退化。实测 recall=1.0 / precision=0.75。
- **运行时配置**: 全部配置改完即生效(客户端按版本号懒重建), 机密字段只写不读、掩码回显,
  持久化文件 gitignore, 日志全程不落 Key。
- **存储分层**: H2 两张表(record 冗余 JSON 列免联查 + finding 明细), 存取收敛在 ReviewStore,
  换 MySQL 只改 datasource。

## 配置项(种子值, 运行期以配置页为准)

| 环境变量 | 说明 |
|---|---|
| `ARGUS_LLM_BASE_URL` / `ARGUS_LLM_API_KEY` / `ARGUS_LLM_MODEL` | OpenAI 兼容 LLM(地址不带 /v1) |
| `ARGUS_LLM_VERIFIER_MODEL` | Verifier 复核模型, 空=同主模型 |
| `ARGUS_GITLAB_BASE_URL` / `ARGUS_GITLAB_TOKEN` / `ARGUS_GITLAB_WEBHOOK_SECRET` | GitLab 集成 |
| `ARGUS_GITHUB_TOKEN` / `ARGUS_GITHUB_WEBHOOK_SECRET` | GitHub 集成 |
| `ARGUS_GITEE_TOKEN` / `ARGUS_GITEE_WEBHOOK_SECRET` | Gitee 集成 |
| `ARGUS_NOTIFY_WEBHOOK_URL` | 钉钉/企微机器人 |

## Roadmap

- [x] **Week 1**: 单体 MVP——diff 解析、文件过滤、LLM 审查、行号校验、Markdown 报告
- [x] **Week 1.5**: Vue3 前端、运行时配置中心、审查记录持久化与查询
- [x] **Week 1.6**: 内嵌 H2 数据库(Spring Data JPA, record+finding 两张表)
- [x] **Week 1.7**: 前端 UI 现代化(全局设计系统、靛蓝主题、自绘侧边栏、详情页统计瓷贴、严重程度药丸标签)
- [x] **Week 2**: GitLab Webhook 自动触发 + MR 行级评论回写 + 幂等去重 + 任务合并
- [x] **Week 3-4**: JavaParser 上下文增强、文件级并行审查、Finder-Verifier 降误报(支持级联模型)
- [x] **Week 5-6**: 评测集量化召回/精确率、统计看板、异步任务队列(MQ-ready 接口)
- [x] **加分项**: MCP Server(`/sse`)、级联模型、钉钉/企微 IM 通知
- [x] **产品化加固**: AI 评分+总评、访问令牌鉴权、手动审查异步化+实时进度、LLM 重试、每日 Token 预算
- [x] **多平台与画像**: VcsProvider 适配层(GitLab/GitHub/Gitee 三平台 Webhook+回写)、提交人自动采集、
      开发者画像(趋势/分类分布/中性标签/AI 成长建议)
- [ ] 后续演进: 队列平移 RabbitMQ、微服务拆分、反馈闭环(👍👎 沉淀误报库)、评测集扩充至 30+ 用例、Gitee 行级评论
