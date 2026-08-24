package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.dto.auth.LoginRequest;
import com.duanju.dto.auth.RefreshRequest;
import com.duanju.dto.auth.RegisterRequest;
import com.duanju.service.AuthService;
import com.duanju.service.entity.DramaEntityService;
import com.duanju.service.entity.DramaEpisodeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService authService;
    private final DramaEntityService dramaEntityService;
    private final DramaEpisodeService dramaEpisodeService;

    public AuthController(AuthService authService,
                          DramaEntityService dramaEntityService,
                          DramaEpisodeService dramaEpisodeService) {
        this.authService = authService;
        this.dramaEntityService = dramaEntityService;
        this.dramaEpisodeService = dramaEpisodeService;
    }

    @GetMapping("/auth/captcha")
    public R<Map<String, Object>> captcha(@RequestParam(defaultValue = "LOGIN") String scene,
                                           @RequestParam(required = false) String receiver,
                                           HttpServletRequest servletRequest) {
        return R.ok(authService.generateCaptcha(scene, receiver, authService.clientIp(servletRequest)));
    }

    @PostMapping("/auth/register")
    public R<Map<String, Object>> register(@Validated @RequestBody RegisterRequest request) {
        return R.ok(authService.register(request.username(), request.password(), request.nickname(),
                request.captchaId(), request.captchaCode()));
    }

    @PostMapping("/auth/login")
    public R<Map<String, Object>> login(@Validated @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return R.ok(authService.login(request.username(), request.password(),
                request.captchaId(), request.captchaCode(), servletRequest));
    }

    @PostMapping("/admin/auth/login")
    public R<Map<String, Object>> adminLogin(@Validated @RequestBody LoginRequest request) {
        return R.ok(authService.adminLogin(request.username(), request.password(),
                request.captchaId(), request.captchaCode()));
    }

    @GetMapping("/admin/auth/me")
    public R<Map<String, Object>> adminMe() {
        return R.ok(authService.adminMe());
    }

    @PostMapping("/auth/refresh")
    public R<Map<String, Object>> refresh(@Validated @RequestBody RefreshRequest request) {
        return R.ok(authService.refresh(request.refreshToken()));
    }

    @GetMapping("/auth/public-stats")
    public R<Map<String, Object>> publicStats() {
        long dramaCount = dramaEntityService.lambdaQuery()
                .eq(com.duanju.entity.Drama::getStatus, 1)
                .count();
        long episodeCount = dramaEpisodeService.lambdaQuery()
                .eq(com.duanju.entity.DramaEpisode::getStatus, 1)
                .count();
        return R.ok(Map.of("dramaCount", dramaCount, "episodeCount", episodeCount));
    }
}
