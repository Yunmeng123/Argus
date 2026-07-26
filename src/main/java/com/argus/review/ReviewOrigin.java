package com.argus.review;

/**
 * 一次审查的来源上下文: 手动提交 / 本地仓库 / 代码平台 PR(GitLab/GitHub/Gitee) / 评测集。
 * repoPath/headRef 供上下文增强读取仓库文件; repoKey/prNumber/commitSha 供平台幂等与回写;
 * author 是提交人, 开发者画像的数据源。
 */
public record ReviewOrigin(
        String source,
        String repoKey,
        Long prNumber,
        String commitSha,
        String repoPath,
        String headRef,
        String mrUrl,
        String mrTitle,
        String author) {

    public static final ReviewOrigin MANUAL =
            new ReviewOrigin("MANUAL", null, null, null, null, null, null, null, null);

    public static final ReviewOrigin EVAL =
            new ReviewOrigin("EVAL", null, null, null, null, null, null, null, null);

    public static ReviewOrigin local(String repoPath, String headRef, String author) {
        return new ReviewOrigin("MANUAL", null, null, null, repoPath, headRef, null, null, author);
    }

    /** source 即平台标识大写: GITLAB / GITHUB / GITEE */
    public static ReviewOrigin vcs(String platform, String repoKey, long prNumber, String commitSha,
                                   String mrUrl, String mrTitle, String author) {
        return new ReviewOrigin(platform.toUpperCase(), repoKey, prNumber, commitSha,
                null, null, mrUrl, mrTitle, author);
    }
}
