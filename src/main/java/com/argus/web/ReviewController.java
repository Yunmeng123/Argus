package com.argus.web;

import com.argus.git.LocalGitDiffService;
import com.argus.model.ReviewResult;
import com.argus.review.ReviewService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;
    private final LocalGitDiffService gitDiffService;

    public ReviewController(ReviewService reviewService, LocalGitDiffService gitDiffService) {
        this.reviewService = reviewService;
        this.gitDiffService = gitDiffService;
    }

    /** 直接提交 unified diff 文本进行审查 */
    @PostMapping(value = "/diff", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ReviewResult reviewDiff(@RequestBody String diffText) {
        return reviewService.review(diffText);
    }

    /** 审查本地 git 仓库两个引用之间的差异(或未提交变更), 带仓库上下文增强 */
    @PostMapping("/local")
    public ReviewResult reviewLocal(@RequestBody LocalReviewRequest request) {
        String diff = gitDiffService.diff(request.repoPath(), request.baseRef(), request.headRef());
        if (diff.isBlank()) {
            throw new IllegalArgumentException("指定范围内没有差异");
        }
        return reviewService.review(diff, com.argus.review.ReviewOrigin.local(request.repoPath(), request.headRef(),
                gitDiffService.commitAuthor(request.repoPath(), request.headRef())));
    }
}
