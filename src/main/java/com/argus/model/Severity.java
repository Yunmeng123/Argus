package com.argus.model;

/**
 * 问题严重程度, 枚举顺序即排序优先级(越靠前越严重)。
 */
public enum Severity {
    BLOCKER, MAJOR, MINOR, INFO;

    public static Severity from(String raw) {
        if (raw == null) {
            return INFO;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO;
        }
    }
}
