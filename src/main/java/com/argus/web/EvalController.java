package com.argus.web;

import com.argus.eval.EvalService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    /** 跑一遍评测集, 返回召回率/精确率。会真实调用当前配置的模型 */
    @PostMapping("/run")
    public EvalService.EvalReport run() {
        return evalService.run();
    }
}
