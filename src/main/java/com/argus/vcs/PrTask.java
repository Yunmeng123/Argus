package com.argus.vcs;

/**
 * 一次待处理的 PR/MR 审查任务。repoKey: GitLab 用项目数字 ID, GitHub/Gitee 用 owner/repo。
 */
public record PrTask(String platform, String repoKey, long prNumber, String commitSha, String author) {

    public String key() {
        return platform + ":" + repoKey + ":" + prNumber;
    }
}
