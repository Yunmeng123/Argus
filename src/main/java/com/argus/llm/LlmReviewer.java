package com.argus.llm;

import com.argus.diff.FileDiff;

public interface LlmReviewer {

    LlmReviewOutcome review(FileDiff fileDiff, String systemPrompt, String userPrompt);
}
