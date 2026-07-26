package com.argus.review;

import java.util.List;
import java.util.Optional;

import com.argus.diff.ChangeType;
import com.argus.diff.FileDiff;

import org.springframework.stereotype.Component;

/**
 * 判断一个文件变更是否值得送给 LLM 审查。
 * 过滤无意义的文件既省 token 也降噪。
 */
@Component
public class ReviewFileFilter {

    private static final List<String> SKIP_SUFFIXES = List.of(
            ".lock", ".min.js", ".min.css", ".map", ".svg", ".png", ".jpg", ".jpeg",
            ".gif", ".ico", ".woff", ".woff2", ".ttf", ".jar", ".pdf");

    private static final List<String> SKIP_FILE_NAMES = List.of(
            "package-lock.json", "pnpm-lock.yaml", "yarn.lock", "go.sum",
            "mvnw", "mvnw.cmd", "gradlew", "gradlew.bat");

    private static final List<String> SKIP_PATH_FRAGMENTS = List.of(
            "node_modules/", "target/", "dist/", ".idea/", "generated/", "__snapshots__/");

    public Optional<String> skipReason(FileDiff fileDiff, int maxChangedLines) {
        if (fileDiff.isBinary()) {
            return Optional.of("二进制文件");
        }
        if (fileDiff.getChangeType() == ChangeType.DELETED) {
            return Optional.of("文件已删除");
        }
        String lower = fileDiff.displayPath().toLowerCase();
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        if (SKIP_FILE_NAMES.contains(fileName)) {
            return Optional.of("锁文件/构建脚本");
        }
        for (String suffix : SKIP_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return Optional.of("按扩展名跳过(" + suffix + ")");
            }
        }
        for (String fragment : SKIP_PATH_FRAGMENTS) {
            if (lower.contains(fragment)) {
                return Optional.of("按路径跳过(" + fragment + ")");
            }
        }
        if (fileDiff.addedLineCount() == 0) {
            return Optional.of("没有新增/修改行");
        }
        int changed = fileDiff.changedLineCount();
        if (changed > maxChangedLines) {
            return Optional.of("变更行数 " + changed + " 超过上限 " + maxChangedLines);
        }
        return Optional.empty();
    }
}
