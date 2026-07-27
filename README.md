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

## 功能全景

```
                          ┌── 手动提交 diff / 本地仓库(带上下文增强)
触发 ──┼── GitLab Webhook(验签→异步队列→任务合并→幂等)      ──┐
                          └── MCP 客户端(Claude/Cursor 经 /sse 调用)         │
                                                                             ▼
UnifiedDiffParser(行号双坐标) → ReviewFileFilter → ContextEnhancer(JavaParser 提取变更行所在完整方法)
        → PromptBuilder → [并行×4] Finder(主模型) → Verifier(复核模型, 反驳式剔除误报)
        → 行号校验(lineVerified) → H2 入库 + Markdown 报告 + IM 通知(钉钉/企微)
                                                                             │
输出 ──┼── Web 界面(看板/记录/详情) ── GitLab MR 行级评论+总结 ──┘
```

## 快速开始

```powershell
# 1. 构建前端(仅前端代码变化后需要)
cd web; npm install; npm run build; cd ..

# 2. 启动(唯一依赖: JDK17+ 与 Maven; 数据库为内嵌 H2 无需安装)
mvn spring-boot:run

# 3. 打开 http://localhost:18080
```

首次使用到「系统配置」页填 LLM 接口地址/模型/API Key(任意 OpenAI 兼容 API), 保存即生效;
没有 Key 可先开 mock 模式跑通流程。

**安全**: 默认开放模式(本机自用)。部署到局域网时设置环境变量 `ARGUS_ACCESS_TOKEN=你的令牌` 后启动,
所有 `/api/**` 与 `/sse` 均需携带 `X-Argus-Token` 头(前端会在 401 时引导输入并记住)。
H2 控制台默认关闭, 调试时把 `spring.h2.console.enabled` 临时改 true。

### 接入代码平台(自动审查 PR/MR)

统一入口 `http://<部署机IP>:18080/api/webhook/{platform}`, 三个平台在配置页各自填 Token + Webhook Secret:

| 平台 | Webhook 设置 | 验签方式 | 回写能力 |
|---|---|---|---|
| GitLab | Settings → Webhooks, 勾选 Merge request events | X-Gitlab-Token 明文比对 | 行级评论(discussions position) + 总结 |
| GitHub | Settings → Webhooks, Content type 选 json, 勾选 Pull requests | **HMAC-SHA256**(X-Hub-Signature-256) | 行级评论(line+side) + 总结 |
| Gitee | 仓库管理 → WebHooks, 密码方式, 勾选 Pull Request | X-Gitee-Token 密码比对 | 总结评论(行级暂降级) |

提交/更新 PR 即自动审查; 同一 commit 幂等不重复审, 高频 push 自动合并任务只审最新。
新增平台只需实现 `VcsProvider` 接口(webhook 解析/取 diff/回写评论), 队列、幂等、编排全部复用。

### 接入 MCP(让 Claude Code/Cursor 调用审查)

MCP 客户端配置 SSE 地址 `http://localhost:18080/sse`, 即可使用工具 `argus_review_diff`。

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
