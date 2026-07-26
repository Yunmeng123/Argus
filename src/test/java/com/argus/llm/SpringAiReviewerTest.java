package com.argus.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpringAiReviewerTest {

    @Test
    void extractJsonStripsMarkdownFence() {
        String fenced = "```json\n{\"findings\":[]}\n```";
        assertEquals("{\"findings\":[]}", SpringAiReviewer.extractJson(fenced));
    }

    @Test
    void extractJsonStripsSurroundingText() {
        String noisy = "好的, 审查结果如下:\n{\"findings\":[{\"line\":1}]}\n以上。";
        assertEquals("{\"findings\":[{\"line\":1}]}", SpringAiReviewer.extractJson(noisy));
    }

    @Test
    void extractJsonKeepsPlainJson() {
        assertEquals("{\"findings\":[]}", SpringAiReviewer.extractJson("{\"findings\":[]}"));
    }
}
