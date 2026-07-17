package com.wedding.platform.system.account.web;

import com.wedding.platform.system.account.application.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AccountService accountService;

    public AuthController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/auth/login")
    public AccountDtos.LoginResponse login(
            @Valid @RequestBody AccountDtos.LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        return accountService.login(request, clientIp(servletRequest));
    }

    @GetMapping("/auth/me")
    public AccountDtos.AccountResponse me(@AuthenticationPrincipal Jwt jwt) {
        return accountService.getCurrentUser(userId(jwt));
    }

    @PutMapping("/account/password")
    public AccountDtos.AccountResponse changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountDtos.ChangePasswordRequest request
    ) {
        return accountService.changePassword(userId(jwt), request);
    }

    @PutMapping("/account/profile")
    public AccountDtos.AccountResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountDtos.UpdateProfileRequest request
    ) {
        return accountService.updateProfile(userId(jwt), request);
    }

    static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
