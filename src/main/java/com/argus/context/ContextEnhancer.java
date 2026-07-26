package com.argus.context;

import java.util.LinkedHashSet;
import java.util.Set;

import com.argus.diff.ChangeType;
import com.argus.diff.FileDiff;
import com.argus.git.LocalGitDiffService;
import com.argus.review.ReviewOrigin;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 上下文增强: 只看 diff 时 LLM 看不到被截断的方法全貌, 容易漏判/误判。
 * 当审查来源带仓库路径时, 用 JavaParser 解析变更文件, 把"变更行所在的完整方法"
 * 作为补充上下文喂给模型。解析失败/非 Java 文件一律安静降级为不增强。
 */
@Component
public class ContextEnhancer {

    private static final Logger log = LoggerFactory.getLogger(ContextEnhancer.class);
    private static final int MAX_CONTEXT_CHARS = 6000;

    private final LocalGitDiffService gitService;

    public ContextEnhancer(LocalGitDiffService gitService) {
        this.gitService = gitService;
    }

    public String enhance(ReviewOrigin origin, FileDiff fileDiff) {
        if (origin == null || origin.repoPath() == null || origin.repoPath().isBlank()) {
            return null;
        }
        String path = fileDiff.displayPath();
        if (!path.endsWith(".java") || fileDiff.getChangeType() == ChangeType.ADDED) {
            // 新增文件的 diff 本身就是全量内容, 无需增强
            return null;
        }
        String content = gitService.fileAtRef(origin.repoPath(), origin.headRef(), path);
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return extractEnclosingMethods(content, fileDiff.addedLineNumbers());
        } catch (Exception e) {
            log.debug("上下文增强失败, 降级为纯 diff 审查: {} ({})", path, e.getMessage());
            return null;
        }
    }

    private String extractEnclosingMethods(String content, Set<Integer> addedLines) {
        CompilationUnit unit = new JavaParser().parse(content).getResult().orElse(null);
        if (unit == null || addedLines.isEmpty()) {
            return null;
        }
        Set<CallableDeclaration<?>> enclosing = new LinkedHashSet<>();
        unit.findAll(MethodDeclaration.class).forEach(method -> {
            if (coversAnyLine(method, addedLines)) {
                enclosing.add(method);
            }
        });
        unit.findAll(ConstructorDeclaration.class).forEach(ctor -> {
            if (coversAnyLine(ctor, addedLines)) {
                enclosing.add(ctor);
            }
        });
        if (enclosing.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (CallableDeclaration<?> callable : enclosing) {
            int begin = callable.getRange().map(r -> r.begin.line).orElse(-1);
            int end = callable.getRange().map(r -> r.end.line).orElse(-1);
            String snippet = "// ==== 完整方法 " + callable.getNameAsString()
                    + " (源文件第 " + begin + "-" + end + " 行) ====\n" + callable + "\n\n";
            if (sb.length() + snippet.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            sb.append(snippet);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private boolean coversAnyLine(CallableDeclaration<?> callable, Set<Integer> lines) {
        return callable.getRange().map(range -> {
            for (int line : lines) {
                if (line >= range.begin.line && line <= range.end.line) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }
}
