package com.argus.diff;

/**
 * diff 中的一行。oldLine/newLine 为 -1 表示该侧不存在此行
 * (新增行没有旧行号, 删除行没有新行号)。
 */
public record DiffLine(LineType type, int oldLine, int newLine, String content) {
}
