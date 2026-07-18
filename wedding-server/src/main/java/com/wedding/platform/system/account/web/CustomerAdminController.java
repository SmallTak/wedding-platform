package com.wedding.platform.system.account.web;

import com.wedding.platform.system.account.application.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/customers")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('/accounts/customers')")
public class CustomerAdminController {

    private final AccountService accountService;

    public CustomerAdminController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountDtos.AccountResponse> customers() {
        return accountService.listCustomers();
    }

    @PatchMapping("/{customerId}/status")
    public AccountDtos.AccountResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long customerId,
            @Valid @RequestBody AccountDtos.UpdateAccountStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        return accountService.updateCustomerStatus(
                AuthController.userId(jwt), customerId, request.status(), clientIp(servletRequest));
    }

    @PostMapping("/{customerId}/reset-password")
    public AccountDtos.AccountResponse resetPassword(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long customerId,
            @Valid @RequestBody AccountDtos.ResetPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        return accountService.resetCustomerPassword(
                AuthController.userId(jwt), customerId, request.initialPassword(), clientIp(servletRequest));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
    }
}
