package com.wedding.platform.operations.feedback.web;

import java.time.Instant;
import java.util.List;

public final class PublicFeedbackDtos {

    private PublicFeedbackDtos() {
    }

    public record Reply(String content, Instant publishedAt) {
    }

    public record Feedback(
            Long id,
            Long projectId,
            String projectTitle,
            Long creatorId,
            String creatorDisplayName,
            List<String> professionalRoles,
            String customerDisplayName,
            Integer rating,
            String content,
            Instant publishedAt,
            Reply reply
    ) {
    }

    public record FeedbackPage(
            List<Feedback> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
