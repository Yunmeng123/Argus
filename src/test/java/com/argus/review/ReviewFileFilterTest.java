package com.argus.review;

import java.util.List;

import com.argus.diff.FileDiff;
import com.argus.diff.UnifiedDiffParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewFileFilterTest {

    private final ReviewFileFilter filter = new ReviewFileFilter();
    private final UnifiedDiffParser parser = new UnifiedDiffParser();

    @Test
    void javaFileWithAdditionsIsReviewable() {
        FileDiff file = parseSingle(
                "diff --git a/src/Foo.java b/src/Foo.java",
                "--- a/src/Foo.java",
                "+++ b/src/Foo.java",
                "@@ -1,1 +1,2 @@",
                " keep",
                "+added");
        assertTrue(filter.skipReason(file, 800).isEmpty());
    }

    @Test
    void lockFileIsSkipped() {
        FileDiff file = parseSingle(
                "diff --git a/package-lock.json b/package-lock.json",
                "--- a/package-lock.json",
                "+++ b/package-lock.json",
                "@@ -1,1 +1,1 @@",
                "-a",
                "+b");
        assertTrue(filter.skipReason(file, 800).isPresent());
    }

    @Test
    void deletedFileIsSkipped() {
        FileDiff file = parseSingle(
                "diff --git a/Old.java b/Old.java",
                "deleted file mode 100644",
                "--- a/Old.java",
                "+++ /dev/null",
                "@@ -1,2 +0,0 @@",
                "-a",
                "-b");
        assertTrue(filter.skipReason(file, 800).isPresent());
    }

    @Test
    void oversizedChangeIsSkipped() {
        FileDiff file = parseSingle(
                "diff --git a/src/Foo.java b/src/Foo.java",
                "--- a/src/Foo.java",
                "+++ b/src/Foo.java",
                "@@ -1,1 +1,2 @@",
                " keep",
                "+added");
        assertTrue(filter.skipReason(file, 0).isPresent());
    }

    private FileDiff parseSingle(String... lines) {
        List<FileDiff> files = parser.parse(String.join("\n", lines) + "\n");
        return files.get(0);
    }
}
