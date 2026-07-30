# PRD：可靠审查任务链路

## 1. 背景与问题

代码平台要求 Webhook 快速响应，而一次 AI 审查可能持续数分钟。进程内队列在应用重启时会丢任务；仅调用 RabbitMQ 发送 API 但不等待 Broker Confirm，也可能在连接抖动时误报“已排队”。

## 2. 目标

1. Webhook 只在 RabbitMQ 明确 ACK 后返回 `202 queued`。
2. RabbitMQ 不可连接、NACK、Return 或 Confirm 超时时返回 `503`，由代码平台重试。
3. 消费失败最多重试三次，之后进入 DLQ，禁止无限循环。
4. 队列与消息持久化，支持服务重启和横向扩展。

## 3. 非目标

- 本迭代不提供 DLQ 管理页面和人工重放。
- 本迭代不改变 GitHub、GitLab、Gitee 的验签规则。
- 本迭代不承诺 exactly-once；业务结果仍需依赖 commit 级幂等检查。

## 4. 用户故事

- 作为开发者，我希望平台显示 Webhook 成功时审查任务确实已进入可靠队列。
- 作为运维人员，我希望失败任务不会无限重试拖垮系统，并能在 DLQ 中定位。
- 作为项目负责人，我希望多实例部署时任务只被一个消费者获取。

## 5. 业务流程

1. 代码平台发送 Webhook。
2. Argus 完成平台识别、验签和事件过滤。
3. Argus 发布 `PrTask` 并等待 Publisher Confirm。
4. ACK 后返回 202；NACK、Return、超时或连接失败返回 503。
5. 消费者执行审查；成功 ACK，异常由 Spring AMQP 重试。
6. 三次均失败后拒绝消息，RabbitMQ 路由至 DLQ。

## 6. 验收标准

| 编号 | 场景 | 预期 |
| --- | --- | --- |
| AC-01 | Broker ACK | Webhook 返回 202，消息可被消费者获取 |
| AC-02 | Broker NACK | Webhook 返回 503，不返回 queued |
| AC-03 | Confirm 超时 | 超过配置时限返回 503 |
| AC-04 | Broker 连接失败 | 返回 503，响应不泄露账号、主机或堆栈 |
| AC-05 | 消费成功 | 调用一次 `PrReviewService.process` 并 ACK |
| AC-06 | 消费连续失败 | 重试三次后消息进入 DLQ |
| AC-07 | 应用重启 | 已确认但未消费的消息仍存在 |

## 7. 风险与后续

- RabbitMQ 提供 at-least-once 投递，消费者可能收到重复消息；下一迭代应增加带租约的数据库任务收据，消除并发重复消费窗口。
- 默认开发账号仅用于本机 Compose；生产环境必须覆盖账号密码并限制管理端口。
- DLQ 不应自动无条件重放，否则可能形成故障循环；重放功能需要权限、原因记录和速率限制。

