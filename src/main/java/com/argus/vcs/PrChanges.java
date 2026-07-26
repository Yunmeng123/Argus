package com.argus.vcs;

/** PR 变更内容与行级评论锚定所需的引用信息 */
public record PrChanges(
        String diffText,
        String baseSha,
        String startSha,
        String headSha,
        String title,
        String webUrl) {
}
