package com.wedding.platform.content.publication.web;

import com.wedding.platform.content.publication.application.PublicContentAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;

final class PublicAccessCookies {

    static final String COOKIE_NAME = "wedding_content_access";

    private PublicAccessCookies() {
    }

    static void write(
            HttpServletRequest request,
            HttpServletResponse response,
            String path,
            PublicContentAccessService.IssuedSession session
    ) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, session.value())
                .httpOnly(true)
                .secure(isSecure(request))
                .sameSite("Lax")
                .path(path)
                .maxAge(session.ttl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    static String clientAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static boolean isSecure(HttpServletRequest request) {
        return request.isSecure()
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
}
