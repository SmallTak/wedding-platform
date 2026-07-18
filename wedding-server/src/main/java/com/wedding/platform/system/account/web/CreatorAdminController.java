package com.wedding.platform.system.account.web;

import com.wedding.platform.system.account.application.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('/accounts/creators')")
public class CreatorAdminController {

    private final AccountService accountService;

    public CreatorAdminController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/creators")
    public List<AccountDtos.AccountResponse> creators() {
        return accountService.listCreators();
    }

    @PostMapping("/creators")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDtos.AccountResponse createCreator(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountDtos.CreateCreatorRequest request,
            HttpServletRequest servletRequest
    ) {
        return accountService.createCreator(AuthController.userId(jwt), request, clientIp(servletRequest));
    }

    @PatchMapping("/creators/{creatorId}/status")
    public AccountDtos.AccountResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long creatorId,
            @Valid @RequestBody AccountDtos.UpdateAccountStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        return accountService.updateCreatorStatus(AuthController.userId(jwt), creatorId, request.status(), clientIp(servletRequest));
    }

    @PostMapping("/creators/{creatorId}/reset-password")
    public AccountDtos.AccountResponse resetPassword(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long creatorId,
            @Valid @RequestBody AccountDtos.ResetPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        return accountService.resetCreatorPassword(AuthController.userId(jwt), creatorId, request.initialPassword(), clientIp(servletRequest));
    }

    @DeleteMapping("/creators/{creatorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCreator(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long creatorId,
            @RequestParam Long version,
            HttpServletRequest servletRequest
    ) {
        accountService.deleteCreator(AuthController.userId(jwt), creatorId, version, clientIp(servletRequest));
    }

    @GetMapping("/professional-roles")
    public List<AccountDtos.ProfessionalRoleResponse> professionalRoles() {
        return accountService.listProfessionalRoles();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
