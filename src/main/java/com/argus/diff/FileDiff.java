package com.argus.diff;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FileDiff {

    private static final String DEV_NULL = "/dev/null";

    private String oldPath;
    private String newPath;
    private ChangeType changeType;
    private boolean binary;
    private final List<Hunk> hunks = new ArrayList<>();

    /** 展示用路径: 优先新路径, 文件被删除时用旧路径 */
    public String displayPath() {
        if (newPath != null && !DEV_NULL.equals(newPath)) {
            return newPath;
        }
        return oldPath != null ? oldPath : "(unknown)";
    }

    /** 头信息解析完成后调用, 补全未显式声明的变更类型 */
    public void finalizeMeta() {
        if (changeType == null) {
            if (DEV_NULL.equals(oldPath)) {
                changeType = ChangeType.ADDED;
            } else if (DEV_NULL.equals(newPath)) {
                changeType = ChangeType.DELETED;
            } else {
                changeType = ChangeType.MODIFIED;
            }
        }
    }

    public int addedLineCount() {
        return countLines(LineType.ADDED);
    }

    public int removedLineCount() {
        return countLines(LineType.REMOVED);
    }

    public int changedLineCount() {
        return addedLineCount() + removedLineCount();
    }

    /** 新文件侧所有新增行的行号, 用于校验 LLM 报告的行号是否真实落在变更上 */
    public Set<Integer> addedLineNumbers() {
        Set<Integer> result = new LinkedHashSet<>();
        for (Hunk hunk : hunks) {
            for (DiffLine line : hunk.getLines()) {
                if (line.type() == LineType.ADDED) {
                    result.add(line.newLine());
                }
            }
        }
        return result;
    }

    private int countLines(LineType type) {
        int count = 0;
        for (Hunk hunk : hunks) {
            for (DiffLine line : hunk.getLines()) {
                if (line.type() == type) {
                    count++;
                }
            }
        }
        return count;
    }

    public void addHunk(Hunk hunk) {
        hunks.add(hunk);
    }

    public String getOldPath() {
        return oldPath;
    }

    public void setOldPath(String oldPath) {
        this.oldPath = oldPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public void setNewPath(String newPath) {
        this.newPath = newPath;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }

    public boolean isBinary() {
        return binary;
    }

    public void setBinary(boolean binary) {
        this.binary = binary;
    }

    public List<Hunk> getHunks() {
        return hunks;
    }
}
