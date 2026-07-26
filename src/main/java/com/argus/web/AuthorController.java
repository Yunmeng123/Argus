package com.argus.web;

import java.util.List;

import com.argus.profile.AuthorProfileService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/authors")
public class AuthorController {

    private final AuthorProfileService profileService;

    public AuthorController(AuthorProfileService profileService) {
        this.profileService = profileService;
    }

    /** 开发者列表(按审查次数倒序) */
    @GetMapping
    public List<AuthorProfileService.AuthorCard> list() {
        return profileService.listAuthors();
    }

    /** 单个开发者完整画像 */
    @GetMapping("/{author}")
    public AuthorProfileService.AuthorProfile profile(@PathVariable String author) {
        return profileService.profile(author);
    }

    /** 生成/刷新 AI 成长画像(真实调用模型, 结果缓存) */
    @PostMapping("/{author}/ai-summary")
    public AuthorProfileService.AiSummaryView generateAiSummary(@PathVariable String author) {
        return profileService.generateAiSummary(author);
    }
}
