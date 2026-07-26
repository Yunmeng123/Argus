package com.argus.web;

import com.argus.config.ConfigPatch;
import com.argus.config.RuntimeConfig;
import com.argus.config.RuntimeConfigService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统配置读写。机密字段(LLM Key/GitLab Token/Webhook Secret)只写不读:
 * GET 只返回掩码, PUT 传空/不传表示保持不变。
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final RuntimeConfigService configService;

    public ConfigController(RuntimeConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public ConfigResponse get() {
        return toResponse(configService.current());
    }

    @PutMapping
    public ConfigResponse update(@RequestBody ConfigUpdateRequest request) {
        LlmConfigDto llm = request.llm() == null
                ? new LlmConfigDto(null, null, null, null, null, null) : request.llm();
        ReviewConfigDto review = request.review() == null
                ? new ReviewConfigDto(null, null, null, null) : request.review();
        GitlabConfigDto gitlab = request.gitlab() == null
                ? new GitlabConfigDto(null, null, null) : request.gitlab();
        PlatformConfigDto github = request.github() == null
                ? new PlatformConfigDto(null, null) : request.github();
        PlatformConfigDto gitee = request.gitee() == null
                ? new PlatformConfigDto(null, null) : request.gitee();
        NotifyConfigDto notify = request.notification() == null
                ? new NotifyConfigDto(null) : request.notification();
        RuntimeConfig updated = configService.update(new ConfigPatch(
                llm.baseUrl(), llm.apiKey(), llm.model(), llm.verifierModel(), llm.temperature(), llm.mock(),
                review.maxFileChangedLines(), review.maxFindingsPerFile(), review.verifierEnabled(),
                review.dailyTokenBudget(),
                gitlab.baseUrl(), gitlab.token(), gitlab.webhookSecret(),
                github.token(), github.webhookSecret(),
                gitee.token(), gitee.webhookSecret(),
                notify.webhookUrl()));
        return toResponse(updated);
    }

    private ConfigResponse toResponse(RuntimeConfig config) {
        String apiKey = config.getLlm().getApiKey();
        String gitlabToken = config.getGitlab().getToken();
        String webhookSecret = config.getGitlab().getWebhookSecret();
        return new ConfigResponse(
                new LlmView(config.getLlm().getBaseUrl(), config.getLlm().getModel(),
                        config.getLlm().getVerifierModel(), config.getLlm().getTemperature(),
                        config.getLlm().isMock(), isSet(apiKey), mask(apiKey)),
                new ReviewView(config.getReview().getMaxFileChangedLines(),
                        config.getReview().getMaxFindingsPerFile(),
                        config.getReview().isVerifierEnabled(),
                        config.getReview().getDailyTokenBudget()),
                new GitlabView(config.getGitlab().getBaseUrl(), isSet(gitlabToken), mask(gitlabToken),
                        isSet(webhookSecret)),
                new PlatformView(isSet(config.getGithub().getToken()), mask(config.getGithub().getToken()),
                        isSet(config.getGithub().getWebhookSecret())),
                new PlatformView(isSet(config.getGitee().getToken()), mask(config.getGitee().getToken()),
                        isSet(config.getGitee().getWebhookSecret())),
                new NotifyView(config.getNotify().getWebhookUrl()));
    }

    private boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private String mask(String secret) {
        if (!isSet(secret)) {
            return null;
        }
        String tail = secret.length() > 4 ? secret.substring(secret.length() - 4) : secret;
        return "****" + tail;
    }

    public record ConfigUpdateRequest(LlmConfigDto llm, ReviewConfigDto review,
                                      GitlabConfigDto gitlab, PlatformConfigDto github,
                                      PlatformConfigDto gitee, NotifyConfigDto notification) {
    }

    public record PlatformConfigDto(String token, String webhookSecret) {
    }

    public record LlmConfigDto(String baseUrl, String apiKey, String model, String verifierModel,
                               Double temperature, Boolean mock) {
    }

    public record ReviewConfigDto(Integer maxFileChangedLines, Integer maxFindingsPerFile,
                                  Boolean verifierEnabled, Integer dailyTokenBudget) {
    }

    public record GitlabConfigDto(String baseUrl, String token, String webhookSecret) {
    }

    public record NotifyConfigDto(String webhookUrl) {
    }

    public record ConfigResponse(LlmView llm, ReviewView review, GitlabView gitlab,
                                 PlatformView github, PlatformView gitee, NotifyView notification) {
    }

    public record PlatformView(boolean tokenSet, String tokenMasked, boolean webhookSecretSet) {
    }

    public record LlmView(String baseUrl, String model, String verifierModel, double temperature,
                          boolean mock, boolean apiKeySet, String apiKeyMasked) {
    }

    public record ReviewView(int maxFileChangedLines, int maxFindingsPerFile, boolean verifierEnabled,
                             int dailyTokenBudget) {
    }

    public record GitlabView(String baseUrl, boolean tokenSet, String tokenMasked, boolean webhookSecretSet) {
    }

    public record NotifyView(String webhookUrl) {
    }
}
