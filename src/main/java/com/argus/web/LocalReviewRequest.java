package com.argus.web;

/**
 * 本地仓库审查请求。
 * baseRef+headRef 都传: 审查 base...head; 只传 baseRef: baseRef 到工作区; 都不传: 未提交的工作区变更。
 */
public record LocalReviewRequest(String repoPath, String baseRef, String headRef) {
}
