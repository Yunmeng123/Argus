package com.argus.vcs;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ 拓扑与 JSON 消息格式。主队列重试耗尽后进入死信队列，便于人工排查和重放。 */
@Configuration
public class ReviewQueueConfig {

    @Bean
    Queue reviewTaskAmqpQueue(@Value("${argus.queue.name:argus.review.tasks}") String queueName,
                              @Value("${argus.queue.dead-letter-name:argus.review.tasks.dlq}") String deadLetterName) {
        return QueueBuilder.durable(queueName)
                .quorum()
                .deadLetterExchange("")
                .deadLetterRoutingKey(deadLetterName)
                .build();
    }

    @Bean
    Queue reviewTaskDeadLetterQueue(
            @Value("${argus.queue.dead-letter-name:argus.review.tasks.dlq}") String deadLetterName) {
        return QueueBuilder.durable(deadLetterName).quorum().build();
    }

    @Bean
    MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
