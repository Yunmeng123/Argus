package com.argus.diff;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * git unified diff 解析器。
 *
 * 两个关键设计:
 * 1. 依据 hunk 头声明的行数计数判断 hunk 边界, 避免行内容(如以 "--" 开头的删除行)
 *    与文件头(---/+++)混淆;
 * 2. 为每一行追踪新旧行号, 这是后续行级评论精确定位的基础。
 */
public final class UnifiedDiffParser {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@ ?(.*)$");

    public List<FileDiff> parse(String diffText) {
        List<FileDiff> files = new ArrayList<>();
        if (diffText == null || diffText.isBlank()) {
            return files;
        }

        String[] lines = diffText.replace("\r\n", "\n").split("\n", -1);

        FileDiff file = null;
        Hunk hunk = null;
        int oldRemaining = 0;
        int newRemaining = 0;
        int oldLine = 0;
        int newLine = 0;

        for (String line : lines) {
            boolean inHunk = hunk != null && (oldRemaining > 0 || newRemaining > 0);

            if (!inHunk) {
                if (line.startsWith("diff --git ")) {
                    file = new FileDiff();
                    files.add(file);
                    hunk = null;
                    parseGitHeaderPaths(file, line);
                    continue;
                }
                if (line.startsWith("--- ")) {
                    // 兼容不带 "diff --git" 头的裸 unified diff: 上一个文件已有 hunk 则视为新文件开始
                    if (file == null || !file.getHunks().isEmpty()) {
                        file = new FileDiff();
                        files.add(file);
                        hunk = null;
                    }
                    file.setOldPath(parsePath(line.substring(4)));
                    continue;
                }
                if (file == null) {
                    continue;
                }
                if (line.startsWith("+++ ")) {
                    file.setNewPath(parsePath(line.substring(4)));
                    continue;
                }
                if (line.startsWith("new file mode")) {
                    file.setChangeType(ChangeType.ADDED);
                    continue;
                }
                if (line.startsWith("deleted file mode")) {
                    file.setChangeType(ChangeType.DELETED);
                    continue;
                }
                if (line.startsWith("rename from ")) {
                    file.setChangeType(ChangeType.RENAMED);
                    file.setOldPath(line.substring("rename from ".length()));
                    continue;
                }
                if (line.startsWith("rename to ")) {
                    file.setNewPath(line.substring("rename to ".length()));
                    continue;
                }
                if (line.startsWith("Binary files ") || line.startsWith("GIT binary patch")) {
                    file.setBinary(true);
                    continue;
                }
                Matcher m = HUNK_HEADER.matcher(line);
                if (m.matches()) {
                    int oldStart = Integer.parseInt(m.group(1));
                    int oldCount = m.group(2) != null ? Integer.parseInt(m.group(2)) : 1;
                    int newStart = Integer.parseInt(m.group(3));
                    int newCount = m.group(4) != null ? Integer.parseInt(m.group(4)) : 1;
                    hunk = new Hunk(oldStart, oldCount, newStart, newCount, m.group(5).trim());
                    file.addHunk(hunk);
                    oldRemaining = oldCount;
                    newRemaining = newCount;
                    oldLine = oldStart;
                    newLine = newStart;
                }
                continue;
            }

            // hunk 体内: 按 hunk 头声明的行数消费
            if (line.startsWith("\\")) {
                // "\ No newline at end of file" 标记, 不占行号
                continue;
            }
            if (line.startsWith("+")) {
                hunk.addLine(new DiffLine(LineType.ADDED, -1, newLine++, line.substring(1)));
                newRemaining--;
            } else if (line.startsWith("-")) {
                hunk.addLine(new DiffLine(LineType.REMOVED, oldLine++, -1, line.substring(1)));
                oldRemaining--;
            } else {
                // 上下文行(含被外部工具去掉了前导空格的空行)
                String content = line.isEmpty() ? "" : line.substring(1);
                hunk.addLine(new DiffLine(LineType.CONTEXT, oldLine++, newLine++, content));
                oldRemaining--;
                newRemaining--;
            }
        }

        files.forEach(FileDiff::finalizeMeta);
        return files;
    }

    /** 从 "diff --git a/x b/x" 中提取路径, 供二进制/纯重命名等没有 ---/+++ 行的场景兜底 */
    private void parseGitHeaderPaths(FileDiff file, String line) {
        String rest = line.substring("diff --git ".length());
        List<String> paths = splitGitHeaderPaths(rest);
        if (paths.size() == 2) {
            file.setOldPath(parsePath(paths.get(0)));
            file.setNewPath(parsePath(paths.get(1)));
        }
    }

    /** Git 会对含空格或特殊字符的路径加双引号, 不能直接按空格或 " b/" 拆分。 */
    private List<String> splitGitHeaderPaths(String value) {
        List<String> result = new ArrayList<>(2);
        int index = 0;
        while (index < value.length() && result.size() < 2) {
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            if (index >= value.length()) {
                break;
            }
            int start = index;
            if (value.charAt(index) == '"') {
                index++;
                boolean escaped = false;
                while (index < value.length()) {
                    char current = value.charAt(index++);
                    if (current == '"' && !escaped) {
                        break;
                    }
                    if (current == '\\' && !escaped) {
                        escaped = true;
                    } else {
                        escaped = false;
                    }
                }
            } else {
                while (index < value.length() && !Character.isWhitespace(value.charAt(index))) {
                    index++;
                }
            }
            result.add(value.substring(start, index));
        }
        return result;
    }

    /** 去掉 a/、b/ 前缀与可能的引号、时间戳后缀 */
    private String parsePath(String raw) {
        String path = raw.trim();
        int tab = path.indexOf('\t');
        if (tab >= 0) {
            path = path.substring(0, tab);
        }
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = decodeGitQuotedPath(path.substring(1, path.length() - 1));
        }
        if (path.startsWith("a/") || path.startsWith("b/")) {
            path = path.substring(2);
        }
        return path;
    }

    /** 解码 core.quotePath 使用的 C 风格转义；八进制序列表示 UTF-8 原始字节。 */
    private String decodeGitQuotedPath(String value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int index = 0; index < value.length();) {
            char current = value.charAt(index++);
            if (current != '\\' || index >= value.length()) {
                bytes.writeBytes(String.valueOf(current).getBytes(StandardCharsets.UTF_8));
                continue;
            }
            char escaped = value.charAt(index++);
            if (escaped >= '0' && escaped <= '7') {
                int decoded = escaped - '0';
                int digits = 1;
                while (digits < 3 && index < value.length()
                        && value.charAt(index) >= '0' && value.charAt(index) <= '7') {
                    decoded = decoded * 8 + value.charAt(index++) - '0';
                    digits++;
                }
                bytes.write(decoded);
                continue;
            }
            char decoded = switch (escaped) {
                case 'a' -> '\u0007';
                case 'b' -> '\b';
                case 't' -> '\t';
                case 'n' -> '\n';
                case 'v' -> '\u000B';
                case 'f' -> '\f';
                case 'r' -> '\r';
                default -> escaped;
            };
            bytes.writeBytes(String.valueOf(decoded).getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
