package com.argus.diff;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedDiffParserTest {

    private final UnifiedDiffParser parser = new UnifiedDiffParser();

    @Test
    void parseModifiedFileWithLineNumbers() {
        String diff = String.join("\n",
                "diff --git a/src/Foo.java b/src/Foo.java",
                "index 3f9c2ab..8d1e4f7 100644",
                "--- a/src/Foo.java",
                "+++ b/src/Foo.java",
                "@@ -10,3 +10,4 @@ public class Foo {",
                "     int a = 1;",
                "-    int b = 2;",
                "+    int b = 3;",
                "+    int c = 4;",
                "     int d = 5;",
                "");
        List<FileDiff> files = parser.parse(diff);
        assertEquals(1, files.size());
        FileDiff file = files.get(0);
        assertEquals("src/Foo.java", file.displayPath());
        assertEquals(ChangeType.MODIFIED, file.getChangeType());
        assertEquals(1, file.getHunks().size());

        List<DiffLine> lines = file.getHunks().get(0).getLines();
        assertEquals(5, lines.size());
        // 上下文行: 新旧行号同时推进
        assertEquals(LineType.CONTEXT, lines.get(0).type());
        assertEquals(10, lines.get(0).oldLine());
        assertEquals(10, lines.get(0).newLine());
        // 删除行只推进旧行号
        assertEquals(LineType.REMOVED, lines.get(1).type());
        assertEquals(11, lines.get(1).oldLine());
        assertEquals(-1, lines.get(1).newLine());
        // 新增行只推进新行号
        assertEquals(LineType.ADDED, lines.get(2).type());
        assertEquals(11, lines.get(2).newLine());
        assertEquals(12, lines.get(3).newLine());
        // 收尾上下文行
        assertEquals(12, lines.get(4).oldLine());
        assertEquals(13, lines.get(4).newLine());

        assertEquals(Set.of(11, 12), file.addedLineNumbers());
    }

    @Test
    void parseNewFile() {
        String diff = String.join("\n",
                "diff --git a/New.java b/New.java",
                "new file mode 100644",
                "index 0000000..1111111",
                "--- /dev/null",
                "+++ b/New.java",
                "@@ -0,0 +1,2 @@",
                "+line1",
                "+line2",
                "");
        List<FileDiff> files = parser.parse(diff);
        assertEquals(1, files.size());
        FileDiff file = files.get(0);
        assertEquals(ChangeType.ADDED, file.getChangeType());
        assertEquals("New.java", file.displayPath());
        assertEquals(2, file.addedLineCount());
        assertEquals(Set.of(1, 2), file.addedLineNumbers());
    }

    @Test
    void parseBinaryFile() {
        String diff = String.join("\n",
                "diff --git a/logo.png b/logo.png",
                "index aaa1111..bbb2222 100644",
                "Binary files a/logo.png and b/logo.png differ",
                "");
        List<FileDiff> files = parser.parse(diff);
        assertEquals(1, files.size());
        assertTrue(files.get(0).isBinary());
        // 无 ---/+++ 行时从 "diff --git" 头兜底取路径
        assertEquals("logo.png", files.get(0).displayPath());
    }

    @Test
    void parseQuotedBinaryPathFromGitHeader() {
        String diff = String.join("\n",
                "diff --git \"a/assets/my logo.png\" \"b/assets/my logo.png\"",
                "Binary files \"a/assets/my logo.png\" and \"b/assets/my logo.png\" differ",
                "");

        FileDiff file = parser.parse(diff).get(0);
        assertTrue(file.isBinary());
        assertEquals("assets/my logo.png", file.displayPath());
    }

    @Test
    void decodeGitOctalEscapesInQuotedPath() {
        String diff = String.join("\n",
                "diff --git \"a/docs/\\344\\270\\255\\346\\226\\207.txt\" \"b/docs/\\344\\270\\255\\346\\226\\207.txt\"",
                "Binary files \"a/docs/\\344\\270\\255\\346\\226\\207.txt\" and \"b/docs/\\344\\270\\255\\346\\226\\207.txt\" differ",
                "");

        assertEquals("docs/中文.txt", parser.parse(diff).get(0).displayPath());
    }

    @Test
    void decodeGitEscapedQuoteAndBackslashWithJGit() {
        String diff = String.join("\n",
                "diff --git \"a/docs/a\\\"b\\\\c.txt\" \"b/docs/a\\\"b\\\\c.txt\"",
                "Binary files differ",
                "");

        assertEquals("docs/a\"b\\c.txt", parser.parse(diff).get(0).displayPath());
    }

    @Test
    void parseMultipleFiles() {
        String diff = String.join("\n",
                "diff --git a/A.java b/A.java",
                "--- a/A.java",
                "+++ b/A.java",
                "@@ -1,1 +1,1 @@",
                "-old",
                "+new",
                "diff --git a/B.java b/B.java",
                "new file mode 100644",
                "--- /dev/null",
                "+++ b/B.java",
                "@@ -0,0 +1,1 @@",
                "+content",
                "");
        List<FileDiff> files = parser.parse(diff);
        assertEquals(2, files.size());
        assertEquals("A.java", files.get(0).displayPath());
        assertEquals("B.java", files.get(1).displayPath());
        assertEquals(ChangeType.ADDED, files.get(1).getChangeType());
    }

    @Test
    void parseBareUnifiedDiffWithoutGitHeader() {
        String diff = String.join("\n",
                "--- a/x.txt",
                "+++ b/x.txt",
                "@@ -1 +1 @@",
                "-old",
                "+new",
                "");
        List<FileDiff> files = parser.parse(diff);
        assertEquals(1, files.size());
        FileDiff file = files.get(0);
        assertEquals("x.txt", file.displayPath());
        assertEquals(ChangeType.MODIFIED, file.getChangeType());
        // 省略行数的 hunk 头 "@@ -1 +1 @@" 默认行数为 1
        assertEquals(1, file.addedLineCount());
        assertEquals(1, file.removedLineCount());
    }
}
