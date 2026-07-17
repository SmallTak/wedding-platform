package com.wedding.platform.system.account.application;

import com.wedding.platform.system.account.persistence.entity.SystemRole;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemRoleRepository;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap.admin", name = "enabled", havingValue = "true")
public class LocalAdminInitializer implements ApplicationRunner {

    private final SystemUserRepository userRepository;
    private final SystemRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String mobile;
    private final String password;

    public LocalAdminInitializer(
            SystemUserRepository userRepository,
            SystemRoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.mobile}") String mobile,
            @Value("${app.bootstrap.admin.password}") String password
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.mobile = mobile;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByAccountTypeAndDeletedFalse("ADMIN")) {
            return;
        }
        SystemRole adminRole = roleRepository.findByCodeAndStatus("ADMIN", "ACTIVE").orElseThrow();
        SystemUser admin = new SystemUser();
        admin.setMobile(mobile);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setDisplayName("平台管理员");
        admin.setAccountType("ADMIN");
        admin.setAccountStatus("ACTIVE");
        admin.setMustChangePassword(true);
        admin.setProfileCompleted(true);
        admin.setDeleted(false);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
    }
}
