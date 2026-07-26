package com.argus.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.argus.config.ArgusProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 访问令牌鉴权。令牌来自 yml/环境变量(ARGUS_ACCESS_TOKEN), 有意不做成运行时可改——
 * 否则拿到一次访问权就能改令牌自提权。
 * 为空 = 开放模式(本机自用); 配置后保护 /api/** 与 MCP 端点。
 * /api/webhook/** 豁免(GitLab 无法带自定义头, 它有自己的 Secret 验签)。
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private final ArgusProperties properties;

    public AuthFilter(ArgusProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String accessToken = properties.getSecurity().getAccessToken();
        if (accessToken == null || accessToken.isBlank() || !isProtected(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        if (accessToken.equals(extractToken(request))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(
                "{\"error\":\"未授权: 请通过 X-Argus-Token 请求头提供访问令牌\"}".getBytes(StandardCharsets.UTF_8));
    }

    private boolean isProtected(String uri) {
        if (uri.startsWith("/api/webhook/")) {
            return false;
        }
        return uri.startsWith("/api/") || uri.equals("/sse") || uri.startsWith("/mcp");
    }

    /** 支持 X-Argus-Token 头 / Authorization Bearer / ?token= 查询参数(SSE 客户端不便带头时用) */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("X-Argus-Token");
        if (header != null && !header.isBlank()) {
            return header;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return request.getParameter("token");
    }
}
