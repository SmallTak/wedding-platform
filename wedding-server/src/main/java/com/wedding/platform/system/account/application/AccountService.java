package com.wedding.platform.system.account.application;

import com.wedding.platform.platform.audit.AuditLogService;
import com.wedding.platform.platform.security.JwtService;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.CreatorProfile;
import com.wedding.platform.system.account.persistence.entity.ProfessionalRole;
import com.wedding.platform.system.account.persistence.entity.SystemRole;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.CreatorProfileRepository;
import com.wedding.platform.system.account.persistence.repository.PermissionRepository;
import com.wedding.platform.system.account.persistence.repository.ProfessionalRoleRepository;
import com.wedding.platform.system.account.persistence.repository.SystemRoleRepository;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import com.wedding.platform.system.account.web.AccountDtos;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AccountService {

    private final SystemUserRepository userRepository;
    private final SystemRoleRepository roleRepository;
    private final ProfessionalRoleRepository professionalRoleRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;

    public AccountService(
            SystemUserRepository userRepository,
            SystemRoleRepository roleRepository,
            ProfessionalRoleRepository professionalRoleRepository,
            CreatorProfileRepository creatorProfileRepository,
            PermissionRepository permissionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.professionalRoleRepository = professionalRoleRepository;
        this.creatorProfileRepository = creatorProfileRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AccountDtos.LoginResponse login(AccountDtos.LoginRequest request, String ipAddress) {
        SystemUser user = userRepository.findByMobileAndDeletedFalse(request.mobile()).orElse(null);
        if (user == null) {
            auditLogService.record(null, null, "ACCOUNT", "LOGIN_FAILED", "SYS_USER", null, "Unknown mobile", ipAddress);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Mobile or password is incorrect");
        }
        if (!"ACTIVE".equals(user.getAccountStatus())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditLogService.record(user.getId(), user.getAccountType(), "ACCOUNT", "LOGIN_FAILED", "SYS_USER", user.getId(), "Invalid credentials", ipAddress);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Mobile or password is incorrect");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        List<String> permissions = permissions(user.getId());
        JwtService.IssuedToken token = jwtService.issue(user, permissions);
        auditLogService.record(user.getId(), user.getAccountType(), "ACCOUNT", "LOGIN", "SYS_USER", user.getId(), "Login succeeded", ipAddress);
        return new AccountDtos.LoginResponse(token.value(), token.expiresAt(), toResponse(user, permissions));
    }

    @Transactional(readOnly = true)
    public AccountDtos.AccountResponse getCurrentUser(Long userId) {
        SystemUser user = getUser(userId);
        return toResponse(user, permissions(userId));
    }

    @Transactional
    public AccountDtos.AccountResponse changePassword(Long userId, AccountDtos.ChangePasswordRequest request) {
        SystemUser user = getUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "New password must be different");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        auditLogService.record(userId, user.getAccountType(), "ACCOUNT", "CHANGE_PASSWORD", "SYS_USER", userId, "Password changed", null);
        return toResponse(user, permissions(userId));
    }

    @Transactional
    public AccountDtos.AccountResponse updateProfile(Long userId, AccountDtos.UpdateProfileRequest request) {
        SystemUser user = getUser(userId);
        user.setDisplayName(request.displayName().trim());
        user.setAvatarPath(request.avatarPath().trim());
        user.setProfileCompleted(true);

        if ("CREATOR".equals(user.getAccountType())) {
            CreatorProfile profile = creatorProfileRepository.findById(userId).orElseGet(() -> {
                CreatorProfile created = new CreatorProfile();
                created.setUserId(userId);
                return created;
            });
            profile.setPositionText(request.positionText().trim());
            profile.setServiceArea(trimToNull(request.serviceArea()));
            profile.setIntroduction(trimToNull(request.introduction()));
            creatorProfileRepository.save(profile);
        }
        userRepository.save(user);
        auditLogService.record(userId, user.getAccountType(), "ACCOUNT", "UPDATE_PROFILE", "SYS_USER", userId, "Profile completed", null);
        return toResponse(user, permissions(userId));
    }

    @Transactional(readOnly = true)
    public List<AccountDtos.AccountResponse> listCreators() {
        return userRepository.findAllByAccountTypeAndDeletedFalseOrderByCreatedAtDesc("CREATOR").stream()
                .map(user -> toResponse(user, permissions(user.getId())))
                .toList();
    }

    @Transactional
    public AccountDtos.AccountResponse createCreator(
            Long operatorId,
            AccountDtos.CreateCreatorRequest request,
            String ipAddress
    ) {
        if (userRepository.existsByMobile(request.mobile())) {
            throw new ApiException(HttpStatus.CONFLICT, "MOBILE_EXISTS", "This mobile number is already registered");
        }
        SystemRole creatorRole = roleRepository.findByCodeAndStatus("CREATOR", "ACTIVE")
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ROLE_MISSING", "Creator role is not configured"));
        List<ProfessionalRole> professionalRoles = professionalRoleRepository.findAllById(request.professionalRoleIds());
        if (professionalRoles.size() != new HashSet<>(request.professionalRoleIds()).size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROFESSIONAL_ROLE_INVALID", "One or more professional roles are invalid");
        }

        SystemUser user = new SystemUser();
        user.setMobile(request.mobile());
        user.setPasswordHash(passwordEncoder.encode(request.initialPassword()));
        user.setDisplayName(trimToNull(request.displayName()));
        user.setAccountType("CREATOR");
        user.setAccountStatus("ACTIVE");
        user.setMustChangePassword(true);
        user.setProfileCompleted(false);
        user.setDeleted(false);
        user.setRoles(new HashSet<>(Set.of(creatorRole)));
        user.setProfessionalRoles(new HashSet<>(professionalRoles));
        user = userRepository.save(user);

        auditLogService.record(operatorId, "ADMIN", "ACCOUNT", "CREATE_CREATOR", "SYS_USER", user.getId(), "Creator account opened", ipAddress);
        return toResponse(user, permissions(user.getId()));
    }

    @Transactional
    public AccountDtos.AccountResponse updateCreatorStatus(Long operatorId, Long creatorId, String status, String ipAddress) {
        SystemUser user = getCreator(creatorId);
        user.setAccountStatus(status);
        userRepository.save(user);
        auditLogService.record(operatorId, "ADMIN", "ACCOUNT", "UPDATE_CREATOR_STATUS", "SYS_USER", creatorId, status, ipAddress);
        return toResponse(user, permissions(creatorId));
    }

    @Transactional
    public AccountDtos.AccountResponse resetCreatorPassword(Long operatorId, Long creatorId, String password, String ipAddress) {
        SystemUser user = getCreator(creatorId);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(true);
        userRepository.save(user);
        auditLogService.record(operatorId, "ADMIN", "ACCOUNT", "RESET_CREATOR_PASSWORD", "SYS_USER", creatorId, "Initial password reset", ipAddress);
        return toResponse(user, permissions(creatorId));
    }

    @Transactional(readOnly = true)
    public List<AccountDtos.ProfessionalRoleResponse> listProfessionalRoles() {
        return professionalRoleRepository.findAllByStatusAndDeletedFalseOrderBySortOrderAsc("ACTIVE").stream()
                .map(role -> new AccountDtos.ProfessionalRoleResponse(role.getId(), role.getName(), role.getDescription()))
                .toList();
    }

    public SystemUser getUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND", "Account is not available"));
    }

    private SystemUser getCreator(Long userId) {
        SystemUser user = getUser(userId);
        if (!"CREATOR".equals(user.getAccountType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_CREATOR", "The selected account is not a creator");
        }
        return user;
    }

    private List<String> permissions(Long userId) {
        return permissionRepository.findResourcesByUserId(userId);
    }

    private AccountDtos.AccountResponse toResponse(SystemUser user, List<String> permissions) {
        CreatorProfile profile = "CREATOR".equals(user.getAccountType())
                ? creatorProfileRepository.findById(user.getId()).orElse(null)
                : null;
        List<String> roles = user.getRoles().stream().map(SystemRole::getCode).sorted().toList();
        List<AccountDtos.ProfessionalRoleResponse> professionalRoles = user.getProfessionalRoles().stream()
                .filter(role -> !Boolean.TRUE.equals(role.getDeleted()))
                .sorted(Comparator.comparing(ProfessionalRole::getSortOrder))
                .map(role -> new AccountDtos.ProfessionalRoleResponse(role.getId(), role.getName(), role.getDescription()))
                .toList();
        boolean setupRequired = Boolean.TRUE.equals(user.getMustChangePassword()) || !Boolean.TRUE.equals(user.getProfileCompleted());
        return new AccountDtos.AccountResponse(
                user.getId(),
                user.getMobile(),
                user.getDisplayName(),
                user.getAvatarPath(),
                user.getAccountType(),
                user.getAccountStatus(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                Boolean.TRUE.equals(user.getProfileCompleted()),
                setupRequired,
                profile == null ? null : profile.getPositionText(),
                profile == null ? null : profile.getServiceArea(),
                profile == null ? null : profile.getIntroduction(),
                roles,
                permissions,
                professionalRoles,
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
