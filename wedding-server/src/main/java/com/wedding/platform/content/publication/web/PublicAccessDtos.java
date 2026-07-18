package com.wedding.platform.content.publication.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class PublicAccessDtos {

    private PublicAccessDtos() {
    }

    public record AccessRequest(
            @NotBlank(message = "Access password is required")
            @Size(min = 6, max = 64, message = "Access password must contain 6 to 64 characters")
            String password
    ) {
    }

    public record AccessSessionResponse(Instant expiresAt) {
    }
}
