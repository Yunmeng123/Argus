package com.argus.review;

import com.argus.diff.DiffLine;
import com.argus.diff.FileDiff;
import com.argus.diff.Hunk;

import org.springframework.stereotype.Component;

/**
 * 组装审查 prompt。核心约定:
 * - diff 每行标注"新文件行号", LLM 报告问题时按此行号定位;
 * - 只允许对新增行报问题, 宁可漏报不可误报;
 * - 输出为纯 JSON, 便于结构化解析。
 */
@Component
public class PromptBuilder {

    public String systemPrompt(int maxFindings) {
        return """
                你是一位资深的 Java/全栈代码审查专家, 负责审查 Git diff 中的代码变更。

                审查维度(按优先级):
                1. BUG — 空指针、边界条件、逻辑错误、并发问题、资源泄漏
                2. SECURITY — SQL 注入、XSS、路径穿越、敏感信息硬编码、不安全的反序列化
                3. PERFORMANCE — 循环内的重复计算/IO/字符串拼接、N+1 查询、明显的低效实现
                4. MAINTAINABILITY — 吞异常、魔法值、复制粘贴代码、过长方法
                5. STYLE — 命名与格式问题(仅在明显影响可读性时报告)

                severity 取值: BLOCKER=必须修复(会造成故障或安全事件) / MAJOR=强烈建议修复 / MINOR=建议改进 / INFO=提示

                严格遵守:
                - 只对新增行(+ 标记)报告问题, line 填写该行行首标注的"新文件行号"。
                - 只报告有明确依据的问题, 对 diff 中看不到的代码不要臆测。宁可漏报, 不可误报。
                - confidence 是 0~1 的小数, 表示该问题真实存在的把握; 低于 0.5 的不要输出。
                - 每个文件最多输出 %d 个问题, 按严重程度从高到低排列。
                - 输出必须是纯 JSON 对象, 不要包含 markdown 围栏或任何解释文字。

                同时必须给出本次变更的质量评分 score(0~100 整数)和一句话总评 summary:
                - 存在 BLOCKER → 40 分以下; 每个 MAJOR 扣 10~15 分; MINOR 扣 3~5 分; INFO 扣 1~2 分
                - 无问题的干净变更依据可读性/健壮性给 90~100
                - summary 必须说明审查了什么、主要结论; 即使无问题也要说明为什么认为没问题, 不允许留空

                输出格式:
                {"score":62,"summary":"新增的查询方法存在 SQL 注入与资源泄漏, 需修复后合入","findings":[{"line":12,"severity":"MAJOR","category":"BUG","title":"一句话概述","detail":"问题说明","suggestion":"修复建议","confidence":0.9}]}
                没有问题时 findings 为 [], 但 score 与 summary 仍必须给出
                """.formatted(maxFindings);
    }

    public String userPrompt(FileDiff fileDiff) {
        return userPrompt(fileDiff, null);
    }

    /** extraContext: 上下文增强产出的"变更行所在完整方法", 可为 null */
    public String userPrompt(FileDiff fileDiff, String extraContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("文件路径: ").append(fileDiff.displayPath()).append('\n');
        sb.append("变更类型: ").append(changeTypeLabel(fileDiff)).append('\n');
        sb.append("变更统计: +").append(fileDiff.addedLineCount())
                .append(" / -").append(fileDiff.removedLineCount()).append("\n\n");
        sb.append("以下是 diff 内容。每行格式: [新文件行号] [标记] [代码]; ")
                .append("标记 + 为新增行(审查对象), - 为已删除的旧行(仅供理解上下文, 不要对其报告问题), 空格为未变动的上下文行。\n\n");

        for (Hunk hunk : fileDiff.getHunks()) {
            sb.append(hunk.header()).append('\n');
            for (DiffLine line : hunk.getLines()) {
                String lineNo = line.newLine() > 0 ? String.format("%5d", line.newLine()) : "     ";
                String marker = switch (line.type()) {
                    case ADDED -> "+";
                    case REMOVED -> "-";
                    case CONTEXT -> " ";
                };
                sb.append(lineNo).append(' ').append(marker).append(' ').append(line.content()).append('\n');
            }
            sb.append('\n');
        }
        if (extraContext != null && !extraContext.isBlank()) {
            sb.append("补充上下文(仓库中变更行所在的完整方法, 仅辅助理解, 不要对 diff 之外的未变更行报告问题):\n\n")
                    .append(extraContext);
        }
        return sb.toString();
    }

    private String changeTypeLabel(FileDiff fileDiff) {
        return switch (fileDiff.getChangeType()) {
            case ADDED -> "新增文件";
            case MODIFIED -> "修改";
            case RENAMED -> "重命名";
            case DELETED -> "删除";
        };
    }
}
