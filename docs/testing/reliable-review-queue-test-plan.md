# 测试计划：可靠审查任务链路

## 1. 测试范围

- RabbitMQ 发布、Confirm、消费、重试和 DLQ。
- Webhook 在队列正常与异常时的 HTTP 语义。
- 队列拓扑、持久化和应用重启恢复。
- 敏感信息保护与并发稳定性。

## 2. 白盒测试

| 编号 | 测试点 | 方法 | 预期 |
| --- | --- | --- | --- |
| WB-01 | 发布参数 | Mock `RabbitTemplate` | 默认交换机、正确 routing key 和 `PrTask` |
| WB-02 | Confirm ACK | 完成 `CorrelationData` future | `submit` 正常返回 |
| WB-03 | Confirm NACK | future 返回 nack | 抛出 `ReviewQueueUnavailableException` |
| WB-04 | 消费委派 | 直接调用 listener | 调用 `PrReviewService.process` |
| WB-05 | 异常传播 | 审查服务抛异常 | listener 不吞异常，允许容器重试 |
| WB-06 | API 脱敏 | 调用异常处理器 | HTTP 503，响应不包含内部连接信息 |
| WB-07 | Git 路径解析回归 | quoted/octal diff 样例 | 返回真实 UTF-8 路径 |

## 3. 黑盒测试

| 编号 | 前置条件 | 操作 | 预期 |
| --- | --- | --- | --- |
| BB-01 | RabbitMQ 正常 | 发送合法 GitHub Webhook | HTTP 202；队列消息增加或被消费者获取 |
| BB-02 | RabbitMQ 停止 | 发送合法 Webhook | HTTP 503；无 `queued` 假成功 |
| BB-03 | 主队列存在 | 发布合法任务 | 消费者日志出现同一 task key |
| BB-04 | 审查稳定失败 | 发布故障任务 | 三次尝试后 DLQ 增加 1 |
| BB-05 | 暂停消费者 | 发布任务并重启 Argus/RabbitMQ | 消息仍在主队列 |
| BB-06 | 两个 Argus 实例 | 连续发布不同任务 | 每条消息只由一个实例获取 |
| BB-07 | 非法签名 | 发送伪造 Webhook | HTTP 403，不产生消息 |
| BB-08 | 未知平台 | 请求未知 platform | HTTP 404，不产生消息 |

## 4. 非功能测试

- 容量：持续发布 1,000 条轻量测试消息，无丢失，积压可恢复。
- 延迟：本地网络下 Publisher Confirm P95 小于 200 ms。
- 稳定性：消费者运行 2 小时，无线程持续增长和连接泄漏。
- 安全：日志、HTTP 响应、提交文件均不出现 RabbitMQ 密码。

## 5. 发布门禁

- 所有白盒自动化测试通过。
- BB-01、BB-02、BB-04、BB-05 必须在真实 RabbitMQ 环境通过。
- `mvn test`、应用启动检查和 `git diff --check` 通过。
- 未完成项必须记录风险、负责人和计划版本，不允许写成“已测试”。

