package com.argus.vcs;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 审查任务队列: webhook 只负责可靠投递, 消费端在后台执行耗时审查。
 * 队列持久化并配置死信队列；消费异常向外抛出，由 Spring AMQP 执行有限重试。
 */
@Component
public class ReviewTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(ReviewTaskQueue.class);

    private final PrReviewService prReviewService;
    private final RabbitTemplate rabbitTemplate;
    private final String queueName;
    private final Duration publisherConfirmTimeout;

    public ReviewTaskQueue(PrReviewService prReviewService,
                           RabbitTemplate rabbitTemplate,
                           @Value("${argus.queue.name:argus.review.tasks}") String queueName,
                           @Value("${argus.queue.publisher-confirm-timeout:5s}") Duration publisherConfirmTimeout) {
        this.prReviewService = prReviewService;
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
        this.publisherConfirmTimeout = publisherConfirmTimeout;
    }

    public void submit(PrTask task) {
        CorrelationData correlation = new CorrelationData(task.key() + ":" + task.commitSha());
        try {
            rabbitTemplate.convertAndSend("", queueName, task, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(publisherConfirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new ReviewQueueUnavailableException("RabbitMQ 未确认审查任务: " + confirm.getReason());
            }
            log.info("PR 审查任务已确认: {} sha={}", task.key(), task.commitSha());
        } catch (ReviewQueueUnavailableException e) {
            throw e;
        } catch (AmqpException e) {
            throw new ReviewQueueUnavailableException("RabbitMQ 连接或投递失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReviewQueueUnavailableException("等待 RabbitMQ 确认时被中断", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new ReviewQueueUnavailableException("等待 RabbitMQ 确认超时或失败", e);
        }
    }

    @RabbitListener(queues = "${argus.queue.name:argus.review.tasks}")
    public void consume(PrTask task) {
        log.info("开始消费 PR 审查任务: {} sha={}", task.key(), task.commitSha());
        prReviewService.process(task);
    }
}
