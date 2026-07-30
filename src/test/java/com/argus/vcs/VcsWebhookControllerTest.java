package com.argus.vcs;

import com.argus.config.RuntimeConfig;
import com.argus.config.RuntimeConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VcsWebhookControllerTest {
    private ReviewTaskQueue queue;
    private VcsWebhookController controller;

    @BeforeEach void setUp() {
        RuntimeConfig config = new RuntimeConfig();
        config.getGithub().setWebhookSecret("secret");
        config.getGitlab().setWebhookSecret("secret");
        config.getGitee().setWebhookSecret("secret");
        RuntimeConfigService configs = mock(RuntimeConfigService.class);
        when(configs.current()).thenReturn(config);
        ObjectMapper mapper = new ObjectMapper();
        queue = mock(ReviewTaskQueue.class);
        controller = new VcsWebhookController(List.of(new GitHubProvider(configs, mapper),
                new GitLabProvider(configs, mapper), new GiteeProvider(configs, mapper)), queue);
    }

    @Test void acceptsCorrectSignatureFromAllThreePlatforms() throws Exception {
        String github = "{\"action\":\"opened\",\"number\":1,\"repository\":{\"full_name\":\"a/r\"},\"pull_request\":{\"state\":\"open\",\"head\":{\"sha\":\"one\"}}}";
        HttpServletRequest gh = request("X-GitHub-Event", "pull_request", "X-Hub-Signature-256", sign(github));
        assertEquals(202, controller.handle("github", gh, github).getStatusCode().value());
        String gitlab = "{\"object_kind\":\"merge_request\",\"project\":{\"id\":2},\"object_attributes\":{\"iid\":3,\"action\":\"open\",\"state\":\"opened\",\"last_commit\":{\"id\":\"two\"}}}";
        assertEquals(202, controller.handle("gitlab", request("X-Gitlab-Token", "secret"), gitlab).getStatusCode().value());
        String gitee = "{\"action\":\"open\",\"repository\":{\"full_name\":\"a/r\"},\"pull_request\":{\"number\":4,\"head\":{\"sha\":\"three\"}}}";
        assertEquals(202, controller.handle("gitee", request("X-Gitee-Token", "secret", "X-Gitee-Event", "Merge Request Hook"), gitee).getStatusCode().value());
        verify(queue, times(3)).submit(any());
    }

    @Test void rejectsBadSignatures() {
        String body = "{}";
        assertEquals(403, controller.handle("github", request("X-Hub-Signature-256", "sha256=bad"), body).getStatusCode().value());
        assertEquals(403, controller.handle("gitlab", request("X-Gitlab-Token", "bad"), body).getStatusCode().value());
        assertEquals(403, controller.handle("gitee", request("X-Gitee-Token", "bad"), body).getStatusCode().value());
        verifyNoInteractions(queue);
    }

    @Test void ignoresUnrelatedEventsAndMalformedJson() throws Exception {
        String body = "{}";
        assertEquals("ignored", controller.handle("github", request("X-GitHub-Event", "push", "X-Hub-Signature-256", sign(body)), body).getBody().get("status"));
        String malformed = "{";
        ResponseEntity<?> response = controller.handle("github", request("X-GitHub-Event", "pull_request", "X-Hub-Signature-256", sign(malformed)), malformed);
        assertEquals(200, response.getStatusCode().value());
        verifyNoInteractions(queue);
    }

    @Test void duplicateAndUpdatedCommitsAreForwardedForQueueDeduplication() throws Exception {
        String template = "{\"action\":\"opened\",\"number\":1,\"repository\":{\"full_name\":\"a/r\"},\"pull_request\":{\"state\":\"open\",\"head\":{\"sha\":\"%s\"}}}";
        for (String sha : List.of("one", "one", "two")) {
            String body = template.formatted(sha);
            controller.handle("github", request("X-GitHub-Event", "pull_request", "X-Hub-Signature-256", sign(body)), body);
        }
        verify(queue, times(3)).submit(any());
    }

    private HttpServletRequest request(String... pairs) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        for (int i = 0; i < pairs.length; i += 2) when(request.getHeader(pairs[i])).thenReturn(pairs[i + 1]);
        return request;
    }
    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
