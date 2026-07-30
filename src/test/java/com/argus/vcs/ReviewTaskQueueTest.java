package com.argus.vcs;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReviewTaskQueueTest {

    private final PrReviewService reviewService = mock(PrReviewService.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ReviewTaskQueue queue = new ReviewTaskQueue(
            reviewService, rabbitTemplate, "review.queue", Duration.ofMillis(100));
    private final PrTask task = new PrTask("github", "owner/repo", 12, "abc123", "alice");

    @Test
    void submitPublishesTaskToRabbitMq() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(""), eq("review.queue"), eq(task), any(CorrelationData.class));

        queue.submit(task);

        verify(rabbitTemplate).convertAndSend(
                eq(""), eq("review.queue"), eq(task), any(CorrelationData.class));
    }

    @Test
    void submitFailsWhenBrokerNacksTask() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "queue unavailable"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), eq(task), any(CorrelationData.class));

        assertThrows(ReviewQueueUnavailableException.class, () -> queue.submit(task));
    }

    @Test
    void consumeProcessesTask() {
        queue.consume(task);

        verify(reviewService).process(task);
    }

    @Test
    void consumePropagatesFailureSoBrokerCanRetry() {
        doThrow(new IllegalStateException("review failed")).when(reviewService).process(task);

        assertThrows(IllegalStateException.class, () -> queue.consume(task));
    }
}
