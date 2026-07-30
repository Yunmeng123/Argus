package com.argus.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 运行时配置管理: 启动时以 application.yml/环境变量为种子, 叠加本地持久化文件
 * (data/argus-config.json, 已 gitignore), 前端配置页修改后立即生效并持久化。
 * version 自增供 LLM 层判断是否需要重建客户端。
 */
@Service
public class RuntimeConfigService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigService.class);
    private static final Path STORE_FILE = Path.of("data", "argus-config.json");

    private final ObjectMapper objectMapper;
    private final AtomicLong version = new AtomicLong(1);
    private volatile RuntimeConfig current;

    public RuntimeConfigService(ArgusProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        RuntimeConfig seed = seedFromProperties(properties);
        this.current = mergeStoredOverSeed(seed);
    }

    /** 当前配置快照。调用方不得修改返回对象 */
    public RuntimeConfig current() {
        return current;
    }

    public long version() {
        return version.get();
    }

    public synchronized RuntimeConfig update(ConfigPatch patch) {
        RuntimeConfig next = current.copy();
        if (notBlank(patch.llmBaseUrl())) {
            next.getLlm().setBaseUrl(patch.llmBaseUrl().trim());
        }
        if (notBlank(patch.llmApiKey())) {
            next.getLlm().setApiKey(patch.llmApiKey().trim());
        }
        if (notBlank(patch.llmModel())) {
            next.getLlm().setModel(patch.llmModel().trim());
        }
        if (patch.llmVerifierModel() != null) {
            next.getLlm().setVerifierModel(patch.llmVerifierModel().trim());
        }
        if (patch.temperature() != null) {
            if (patch.temperature() < 0 || patch.temperature() > 2) {
                throw new IllegalArgumentException("temperature 需在 0~2 之间");
            }
            next.getLlm().setTemperature(patch.temperature());
        }
        if (patch.mock() != null) {
            next.getLlm().setMock(patch.mock());
        }
        if (patch.maxFileChangedLines() != null) {
            if (patch.maxFileChangedLines() < 1) {
                throw new IllegalArgumentException("maxFileChangedLines 需大于 0");
            }
            next.getReview().setMaxFileChangedLines(patch.maxFileChangedLines());
        }
        if (patch.maxFindingsPerFile() != null) {
            if (patch.maxFindingsPerFile() < 1) {
                throw new IllegalArgumentException("maxFindingsPerFile 需大于 0");
            }
            next.getReview().setMaxFindingsPerFile(patch.maxFindingsPerFile());
        }
        if (patch.verifierEnabled() != null) {
            next.getReview().setVerifierEnabled(patch.verifierEnabled());
        }
        if (patch.dailyTokenBudget() != null) {
            if (patch.dailyTokenBudget() < 0) {
                throw new IllegalArgumentException("dailyTokenBudget 不能为负数");
            }
            next.getReview().setDailyTokenBudget(patch.dailyTokenBudget());
        }
        if (patch.gitlabBaseUrl() != null) {
            next.getGitlab().setBaseUrl(patch.gitlabBaseUrl().trim());
        }
        if (notBlank(patch.gitlabToken())) {
            next.getGitlab().setToken(patch.gitlabToken().trim());
        }
        if (notBlank(patch.gitlabWebhookSecret())) {
            next.getGitlab().setWebhookSecret(patch.gitlabWebhookSecret().trim());
        }
        if (notBlank(patch.githubToken())) {
            next.getGithub().setToken(patch.githubToken().trim());
        }
        if (notBlank(patch.githubWebhookSecret())) {
            next.getGithub().setWebhookSecret(patch.githubWebhookSecret().trim());
        }
        if (notBlank(patch.giteeToken())) {
            next.getGitee().setToken(patch.giteeToken().trim());
        }
        if (notBlank(patch.giteeWebhookSecret())) {
            next.getGitee().setWebhookSecret(patch.giteeWebhookSecret().trim());
        }
        if (patch.notifyWebhookUrl() != null) {
            next.getNotify().setWebhookUrl(patch.notifyWebhookUrl().trim());
        }
        persist(next);
        current = next;
        version.incrementAndGet();
        return next;
    }

    private RuntimeConfig seedFromProperties(ArgusProperties properties) {
        RuntimeConfig seed = new RuntimeConfig();
        seed.getLlm().setBaseUrl(properties.getLlm().getBaseUrl());
        seed.getLlm().setApiKey(properties.getLlm().getApiKey());
        seed.getLlm().setModel(properties.getLlm().getModel());
        seed.getLlm().setVerifierModel(properties.getLlm().getVerifierModel());
        seed.getLlm().setTemperature(properties.getLlm().getTemperature());
        seed.getLlm().setMock(properties.getLlm().isMock());
        seed.getReview().setMaxFileChangedLines(properties.getReview().getMaxFileChangedLines());
        seed.getReview().setMaxFindingsPerFile(properties.getReview().getMaxFindingsPerFile());
        seed.getReview().setVerifierEnabled(properties.getReview().isVerifierEnabled());
        seed.getReview().setDailyTokenBudget(properties.getReview().getDailyTokenBudget());
        seed.getGitlab().setBaseUrl(properties.getGitlab().getBaseUrl());
        seed.getGitlab().setToken(properties.getGitlab().getToken());
        seed.getGitlab().setWebhookSecret(properties.getGitlab().getWebhookSecret());
        seed.getGithub().setToken(properties.getGithub().getToken());
        seed.getGithub().setWebhookSecret(properties.getGithub().getWebhookSecret());
        seed.getGitee().setToken(properties.getGitee().getToken());
        seed.getGitee().setWebhookSecret(properties.getGitee().getWebhookSecret());
        seed.getNotify().setWebhookUrl(properties.getNotify().getWebhookUrl());
        return seed;
    }

    /** 持久化文件优先; 机密字段文件里为空时回落到种子值(保证环境变量仍可用) */
    private RuntimeConfig mergeStoredOverSeed(RuntimeConfig seed) {
        if (!Files.exists(STORE_FILE)) {
            return seed;
        }
        try {
            RuntimeConfig stored = objectMapper.readValue(Files.readString(STORE_FILE, StandardCharsets.UTF_8),
                    RuntimeConfig.class);
            applySeedFallbacks(stored, seed);
            log.info("已加载持久化配置: {}", STORE_FILE.toAbsolutePath());
            return stored;
        } catch (IOException e) {
            log.warn("读取持久化配置失败, 使用默认配置: {}", e.getMessage());
            return seed;
        }
    }

    /** 机密字段未在本地文件配置时，保留环境变量注入的种子值。 */
    static void applySeedFallbacks(RuntimeConfig stored, RuntimeConfig seed) {
        if (!notBlank(stored.getLlm().getBaseUrl())) {
            stored.getLlm().setBaseUrl(seed.getLlm().getBaseUrl());
        }
        if (!notBlank(stored.getLlm().getApiKey())) {
            stored.getLlm().setApiKey(seed.getLlm().getApiKey());
        }
        if (!notBlank(stored.getLlm().getModel())) {
            stored.getLlm().setModel(seed.getLlm().getModel());
        }
        if (!notBlank(stored.getGitlab().getToken())) {
            stored.getGitlab().setToken(seed.getGitlab().getToken());
        }
        if (!notBlank(stored.getGitlab().getWebhookSecret())) {
            stored.getGitlab().setWebhookSecret(seed.getGitlab().getWebhookSecret());
        }
        if (!notBlank(stored.getGithub().getToken())) {
            stored.getGithub().setToken(seed.getGithub().getToken());
        }
        if (!notBlank(stored.getGithub().getWebhookSecret())) {
            stored.getGithub().setWebhookSecret(seed.getGithub().getWebhookSecret());
        }
        if (!notBlank(stored.getGitee().getToken())) {
            stored.getGitee().setToken(seed.getGitee().getToken());
        }
        if (!notBlank(stored.getGitee().getWebhookSecret())) {
            stored.getGitee().setWebhookSecret(seed.getGitee().getWebhookSecret());
        }
    }

    private void persist(RuntimeConfig config) {
        try {
            Files.createDirectories(STORE_FILE.getParent());
            Files.writeString(STORE_FILE,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("持久化配置失败(本次修改仍在内存中生效): {}", e.getMessage());
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
