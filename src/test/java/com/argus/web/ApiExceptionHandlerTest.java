package com.argus.web;

import java.util.Map;

import com.argus.vcs.ReviewQueueUnavailableException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    @Test
    void queueFailureReturnsRetryableServiceUnavailableWithoutLeakingDetails() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<Map<String, String>> response = handler.queueUnavailable(
                new ReviewQueueUnavailableException("amqp://user:secret@internal-host failed"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(Map.of("error", "审查任务队列暂不可用，请稍后重试"), response.getBody());
    }
}
