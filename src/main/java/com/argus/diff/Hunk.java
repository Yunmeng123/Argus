package com.argus.diff;

import java.util.ArrayList;
import java.util.List;

public class Hunk {

    private final int oldStart;
    private final int oldCount;
    private final int newStart;
    private final int newCount;
    private final String sectionHeading;
    private final List<DiffLine> lines = new ArrayList<>();

    public Hunk(int oldStart, int oldCount, int newStart, int newCount, String sectionHeading) {
        this.oldStart = oldStart;
        this.oldCount = oldCount;
        this.newStart = newStart;
        this.newCount = newCount;
        this.sectionHeading = sectionHeading == null ? "" : sectionHeading;
    }

    public void addLine(DiffLine line) {
        lines.add(line);
    }

    public int getOldStart() {
        return oldStart;
    }

    public int getOldCount() {
        return oldCount;
    }

    public int getNewStart() {
        return newStart;
    }

    public int getNewCount() {
        return newCount;
    }

    public String getSectionHeading() {
        return sectionHeading;
    }

    public List<DiffLine> getLines() {
        return lines;
    }

    public String header() {
        String heading = sectionHeading.isEmpty() ? "" : " " + sectionHeading;
        return "@@ -%d,%d +%d,%d @@%s".formatted(oldStart, oldCount, newStart, newCount, heading);
    }
}
