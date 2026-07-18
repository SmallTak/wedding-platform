package com.wedding.platform.system.account.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Mobile is required")
            @Pattern(regexp = "^1\\d{10}$", message = "Mobile must be an 11-digit mainland China number")
            String mobile,
            @NotBlank(message = "Password is required") String password
    ) {
    }

    public record LoginResponse(String accessToken, Instant expiresAt, AccountResponse user) {
    }

    public record RegisterCustomerRequest(
            @NotBlank(message = "Mobile is required")
            @Pattern(regexp = "^1\\d{10}$", message = "Mobile must be an 11-digit mainland China number")
            String mobile,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must contain 8 to 72 characters")
            String password,
            @NotBlank(message = "Nickname is required")
            @Size(max = 100, message = "Nickname is too long")
            String nickname
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 72, message = "New password must contain 8 to 72 characters")
            String newPassword
    ) {
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "Display name is required")
            @Size(max = 100, message = "Display name is too long")
            String displayName,
            @NotBlank(message = "Avatar path is required")
            @Size(max = 500, message = "Avatar path is too long")
            String avatarPath,
            @NotBlank(message = "Position is required")
            @Size(max = 100, message = "Position is too long")
            String positionText,
            @Size(max = 300, message = "Service area is too long") String serviceArea,
            @Size(max = 1000, message = "Introduction is too long") String introduction
    ) {
    }

    public record UpdateCustomerProfileRequest(
            @NotBlank(message = "Nickname is required")
            @Size(max = 100, message = "Nickname is too long")
            String nickname
    ) {
    }

    public record CreateCreatorRequest(
            @NotBlank(message = "Mobile is required")
            @Pattern(regexp = "^1\\d{10}$", message = "Mobile must be an 11-digit mainland China number")
            String mobile,
            @NotBlank(message = "Initial password is required")
            @Size(min = 8, max = 72, message = "Initial password must contain 8 to 72 characters")
            String initialPassword,
            @Size(max = 100, message = "Display name is too long") String displayName,
            @NotEmpty(message = "At least one professional role is required") List<Long> professionalRoleIds
    ) {
    }

    public record UpdateAccountStatusRequest(
            @NotBlank(message = "Status is required")
            @Pattern(regexp = "ACTIVE|DISABLED", message = "Status must be ACTIVE or DISABLED") String status
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Initial password is required")
            @Size(min = 8, max = 72, message = "Initial password must contain 8 to 72 characters")
            String initialPassword
    ) {
    }

    public record ProfessionalRoleResponse(Long id, String name, String description) {
    }

    public record AccountResponse(
            Long id,
            String mobile,
            String displayName,
            String nickname,
            String avatarPath,
            String accountType,
            String accountStatus,
            boolean mustChangePassword,
            boolean profileCompleted,
            boolean setupRequired,
            String positionText,
            String serviceArea,
            String introduction,
            List<String> roles,
            List<String> permissions,
            List<ProfessionalRoleResponse> professionalRoles,
            Long version,
            Instant lastLoginAt,
            Instant createdAt
    ) {
    }
}
