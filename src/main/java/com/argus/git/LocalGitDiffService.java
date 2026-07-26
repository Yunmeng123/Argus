package com.argus.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * 调用本地 git 命令获取 diff 文本。
 */
@Service
public class LocalGitDiffService {

    /** 只允许分支名/tag/commit 等安全字符, 防止拼出恶意参数 */
    private static final Pattern SAFE_REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/^~\\-]*$");
    private static final long TIMEOUT_SECONDS = 60;

    /**
     * baseRef+headRef 都传: 审查 base...head (与 MR 相同的 merge-base 语义);
     * 只传 baseRef: 审查 baseRef 到工作区的差异;
     * 都不传: 审查未提交的工作区变更 (git diff HEAD)。
     */
    public String diff(String repoPath, String baseRef, String headRef) {
        if (repoPath == null || repoPath.isBlank()) {
            throw new IllegalArgumentException("repoPath 不能为空");
        }
        if (!Files.isDirectory(Path.of(repoPath))) {
            throw new IllegalArgumentException("仓库目录不存在: " + repoPath);
        }
        validateRef(baseRef);
        validateRef(headRef);

        List<String> command = new ArrayList<>(List.of("git", "-C", repoPath, "diff", "--no-color", "--unified=3"));
        if (baseRef != null && headRef != null) {
            command.add(baseRef + "..." + headRef);
        } else if (baseRef != null) {
            command.add(baseRef);
        } else {
            command.add("HEAD");
        }

        try {
            Process process = new ProcessBuilder(command).start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("git diff 执行超时");
            }
            if (process.exitValue() != 0) {
                throw new IllegalArgumentException("git diff 执行失败: " + stderr.trim());
            }
            return stdout;
        } catch (IOException e) {
            throw new IllegalStateException("无法执行 git 命令: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git diff 被中断", e);
        }
    }

    /**
     * 提交人识别(开发者画像数据源): headRef 给定时取该提交的作者;
     * 未给定(审查工作区未提交变更)时取本机 git 配置的用户名。取不到返回 null。
     */
    public String commitAuthor(String repoPath, String headRef) {
        try {
            String[] command = (headRef == null || headRef.isBlank())
                    ? new String[]{"git", "-C", repoPath, "config", "user.name"}
                    : new String[]{"git", "-C", repoPath, "log", "-1", "--format=%an", headRef};
            if (headRef != null && !headRef.isBlank()) {
                validateRef(headRef);
            }
            Process process = new ProcessBuilder(command).start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.getErrorStream().readAllBytes();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                return null;
            }
            String author = stdout.trim();
            return author.isEmpty() ? null : author;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 读取仓库中某文件内容, 供上下文增强使用。
     * ref 为空读工作区文件; 否则 git show ref:path。读不到返回 null(降级为不增强)。
     */
    public String fileAtRef(String repoPath, String ref, String relativePath) {
        try {
            if (ref == null || ref.isBlank()) {
                Path repo = Path.of(repoPath).toAbsolutePath().normalize();
                Path file = repo.resolve(relativePath).normalize();
                if (!file.startsWith(repo) || !Files.isRegularFile(file)) {
                    return null;
                }
                return Files.readString(file, StandardCharsets.UTF_8);
            }
            validateRef(ref);
            Process process = new ProcessBuilder("git", "-C", repoPath, "show", ref + ":" + relativePath).start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.getErrorStream().readAllBytes();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                return null;
            }
            return stdout;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void validateRef(String ref) {
        if (ref != null && !SAFE_REF.matcher(ref).matches()) {
            throw new IllegalArgumentException("非法的 git 引用: " + ref);
        }
    }
}
