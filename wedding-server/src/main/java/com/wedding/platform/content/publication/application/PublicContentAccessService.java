package com.wedding.platform.content.publication.application;

import com.wedding.platform.platform.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PublicContentAccessService {

    private static final String AUDIENCE = "public-content";
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final int MAX_FAILURES = 10;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final PasswordEncoder passwordEncoder;
    private final String issuer;
    private final Duration ttl;
    private final ConcurrentHashMap<String, FailureState> failures = new ConcurrentHashMap<>();

    public PublicContentAccessService(
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.content-access.issuer}") String issuer,
            @Value("${app.security.content-access.ttl}") Duration ttl
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.passwordEncoder = passwordEncoder;
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public String encodePassword(String password) {
        validatePassword(password);
        return passwordEncoder.encode(password);
    }

    public IssuedSession unlock(
            ContentType contentType,
            Long contentId,
            Long contentVersion,
            String passwordHash,
            String password,
            String clientAddress
    ) {
        String failureKey = contentType + ":" + contentId + ":" + clientAddress;
        requireAttemptAllowed(failureKey);
        if (!StringUtils.hasText(passwordHash)
                || !StringUtils.hasText(password)
                || !passwordEncoder.matches(password, passwordHash)) {
            recordFailure(failureKey);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CONTENT_ACCESS_INVALID",
                    "The access password is incorrect");
        }
        failures.remove(failureKey);
        return issue(contentType, contentId, contentVersion);
    }

    public boolean isValid(
            String token,
            ContentType contentType,
            Long contentId,
            Long contentVersion
    ) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            Map<String, Object> claims = jwt.getClaims();
            return issuer.equals(String.valueOf(claims.get("iss")))
                    && audienceMatches(claims.get("aud"))
                    && contentType.name().equals(String.valueOf(claims.get("contentType")))
                    && contentId.toString().equals(String.valueOf(claims.get("contentId")))
                    && contentVersion.toString().equals(String.valueOf(claims.get("contentVersion")));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean audienceMatches(Object audience) {
        if (audience instanceof Collection<?> values) {
            return values.stream().anyMatch(AUDIENCE::equals);
        }
        return AUDIENCE.equals(audience);
    }

    private IssuedSession issue(ContentType contentType, Long contentId, Long contentVersion) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(AUDIENCE))
                .subject(contentType + ":" + contentId)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("contentType", contentType.name())
                .claim("contentId", contentId.toString())
                .claim("contentVersion", contentVersion.toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedSession(value, expiresAt, ttl);
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)
                || password.length() < 6
                || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCESS_PASSWORD_INVALID",
                    "Access password must contain 6 to 64 characters and at most 72 bytes");
        }
    }

    private void requireAttemptAllowed(String failureKey) {
        Instant now = Instant.now();
        FailureState state = failures.get(failureKey);
        if (state == null) {
            return;
        }
        if (!now.isBefore(state.resetAt())) {
            failures.remove(failureKey, state);
            return;
        }
        if (state.failures() >= MAX_FAILURES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CONTENT_ACCESS_RATE_LIMITED",
                    "Too many access attempts; try again later");
        }
    }

    private void recordFailure(String failureKey) {
        Instant now = Instant.now();
        failures.compute(failureKey, (key, current) -> {
            if (current == null || !now.isBefore(current.resetAt())) {
                return new FailureState(1, now.plus(FAILURE_WINDOW));
            }
            return new FailureState(current.failures() + 1, current.resetAt());
        });
    }

    public enum ContentType {
        PROJECT,
        COLLECTION
    }

    public record IssuedSession(String value, Instant expiresAt, Duration ttl) {
    }

    private record FailureState(int failures, Instant resetAt) {
    }
}
