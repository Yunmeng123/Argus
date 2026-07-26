package com.argus.vcs;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VcsProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ---------- GitLab ----------

    @Test
    void gitlabOpenActionTriggersTask() throws Exception {
        Optional<PrTask> task = GitLabProvider.decide(gitlabPayload("open", "opened", true));
        assertTrue(task.isPresent());
        assertEquals("gitlab", task.get().platform());
        assertEquals("42", task.get().repoKey());
        assertEquals(7L, task.get().prNumber());
        assertEquals("abc123", task.get().commitSha());
        assertEquals("alice", task.get().author());
    }

    @Test
    void gitlabUpdateWithoutOldrevIsIgnored() throws Exception {
        assertTrue(GitLabProvider.decide(gitlabPayload("update", "opened", false)).isEmpty());
    }

    @Test
    void gitlabClosedMrIsIgnored() throws Exception {
        assertTrue(GitLabProvider.decide(gitlabPayload("update", "merged", true)).isEmpty());
    }

    @Test
    void gitlabAssembleUnifiedDiffAddsHeaders() throws Exception {
        String changes = """
                [{"old_path":"src/A.java","new_path":"src/A.java","new_file":false,"deleted_file":false,
                  "diff":"@@ -1,1 +1,1 @@\\n-old\\n+new\\n"}]
                """;
        String diff = GitLabProvider.assembleUnifiedDiff(mapper.readTree(changes));
        assertTrue(diff.contains("diff --git a/src/A.java b/src/A.java"));
        assertTrue(diff.contains("--- a/src/A.java"));
        assertTrue(diff.contains("+++ b/src/A.java"));
    }

    // ---------- GitHub ----------

    @Test
    void githubSignatureVerification() {
        String body = "{\"hello\":\"world\"}";
        // 预先计算: HMAC-SHA256("secret123", body)
        String valid = "sha256=" + hmacHex("secret123", body);
        assertTrue(GitHubProvider.verifySignature("secret123", body, valid));
        assertFalse(GitHubProvider.verifySignature("secret123", body, "sha256=deadbeef"));
        assertFalse(GitHubProvider.verifySignature("secret123", body, null));
        assertFalse(GitHubProvider.verifySignature("wrong", body, valid));
    }

    @Test
    void githubOpenedPrTriggersTask() throws Exception {
        JsonNode payload = mapper.readTree("""
                {"action":"opened","number":15,
                 "repository":{"full_name":"alice/demo"},
                 "pull_request":{"number":15,"state":"open",
                   "head":{"sha":"fedcba9"},"user":{"login":"alice"}}}
                """);
        Optional<PrTask> task = GitHubProvider.decide(payload);
        assertTrue(task.isPresent());
        assertEquals("github", task.get().platform());
        assertEquals("alice/demo", task.get().repoKey());
        assertEquals(15L, task.get().prNumber());
        assertEquals("fedcba9", task.get().commitSha());
        assertEquals("alice", task.get().author());
    }

    @Test
    void githubLabeledActionIsIgnored() throws Exception {
        JsonNode payload = mapper.readTree("""
                {"action":"labeled","number":15,
                 "repository":{"full_name":"alice/demo"},
                 "pull_request":{"state":"open","head":{"sha":"fedcba9"}}}
                """);
        assertTrue(GitHubProvider.decide(payload).isEmpty());
    }

    // ---------- Gitee ----------

    @Test
    void giteeOpenActionTriggersTask() throws Exception {
        JsonNode payload = mapper.readTree("""
                {"action":"open",
                 "repository":{"full_name":"alice/demo"},
                 "pull_request":{"number":3,"head":{"sha":"a1b2c3"},"user":{"login":"alice"}}}
                """);
        Optional<PrTask> task = GiteeProvider.decide(payload);
        assertTrue(task.isPresent());
        assertEquals("gitee", task.get().platform());
        assertEquals("alice/demo", task.get().repoKey());
        assertEquals(3L, task.get().prNumber());
    }

    @Test
    void giteeFilesAssembleDiff() throws Exception {
        JsonNode files = mapper.readTree("""
                [{"filename":"src/B.java","status":"added",
                  "patch":{"diff":"@@ -0,0 +1,1 @@\\n+content\\n"}}]
                """);
        String diff = GiteeProvider.assembleDiffFromFiles(files);
        assertTrue(diff.contains("diff --git a/src/B.java b/src/B.java"));
        assertTrue(diff.contains("--- /dev/null"));
        assertTrue(diff.contains("+++ b/src/B.java"));
    }

    private JsonNode gitlabPayload(String action, String state, boolean withOldrev) throws Exception {
        String oldrev = withOldrev ? "\"oldrev\":\"def456\"," : "";
        String json = """
                {"object_kind":"merge_request",
                 "user":{"username":"alice","name":"Alice"},
                 "project":{"id":42},
                 "object_attributes":{"iid":7,"action":"%s","state":"%s",%s
                   "last_commit":{"id":"abc123"}}}
                """.formatted(action, state, oldrev);
        return mapper.readTree(json);
    }

    private String hmacHex(String secret, String body) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
