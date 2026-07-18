package com.wedding.platform.platform.security;

import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class AccountSetupFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_SETUP_PATHS = Set.of(
            "/api/auth/me",
            "/api/account/password",
            "/api/account/avatar",
            "/api/account/profile"
    );

    private final SystemUserRepository userRepository;

    public AccountSetupFilter(SystemUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String accountType = jwtAuthentication.getToken().getClaimAsString("accountType");
            if (!"CREATOR".equals(accountType) && !"CUSTOMER".equals(accountType)) {
                filterChain.doFilter(request, response);
                return;
            }
            Long userId = Long.valueOf(jwtAuthentication.getToken().getClaimAsString("uid"));
            SystemUser user = userRepository.findById(userId).orElse(null);
            if (user == null || !"ACTIVE".equals(user.getAccountStatus()) || Boolean.TRUE.equals(user.getDeleted())) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "ACCOUNT_DISABLED", "Account is not available");
                return;
            }
            boolean setupRequired = Boolean.TRUE.equals(user.getMustChangePassword())
                    || !Boolean.TRUE.equals(user.getProfileCompleted());
            if (setupRequired && !isSetupPath(request.getRequestURI())) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "ACCOUNT_SETUP_REQUIRED", "Complete account setup first");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isSetupPath(String requestUri) {
        return requestUri.startsWith("/api/public/") || ALLOWED_SETUP_PATHS.contains(requestUri);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
