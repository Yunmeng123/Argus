package com.argus.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeConfigServiceTest {

    @Test
    void blankStoredWebhookSecretsFallBackToEnvironmentSeed() {
        RuntimeConfig seed = new RuntimeConfig();
        seed.getGithub().setWebhookSecret("github-from-env");
        seed.getGitee().setWebhookSecret("gitee-from-env");

        RuntimeConfig stored = new RuntimeConfig();
        stored.getGithub().setWebhookSecret("");
        stored.getGitee().setWebhookSecret("  ");

        RuntimeConfigService.applySeedFallbacks(stored, seed);

        assertEquals("github-from-env", stored.getGithub().getWebhookSecret());
        assertEquals("gitee-from-env", stored.getGitee().getWebhookSecret());
    }

    @Test
    void configuredStoredWebhookSecretsTakePrecedence() {
        RuntimeConfig seed = new RuntimeConfig();
        seed.getGithub().setWebhookSecret("github-from-env");
        seed.getGitee().setWebhookSecret("gitee-from-env");

        RuntimeConfig stored = new RuntimeConfig();
        stored.getGithub().setWebhookSecret("github-stored");
        stored.getGitee().setWebhookSecret("gitee-stored");

        RuntimeConfigService.applySeedFallbacks(stored, seed);

        assertEquals("github-stored", stored.getGithub().getWebhookSecret());
        assertEquals("gitee-stored", stored.getGitee().getWebhookSecret());
    }
}
