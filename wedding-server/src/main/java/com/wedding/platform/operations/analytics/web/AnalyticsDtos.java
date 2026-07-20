package com.wedding.platform.operations.analytics.web;

import com.wedding.platform.operations.analytics.persistence.entity.SiteVisitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record RecordVisitRequest(
            @NotBlank(message = "Visitor id is required")
            @Size(max = 100, message = "Visitor id is too long")
            @Pattern(
                    regexp = "^[A-Za-z0-9_-]{16,100}$",
                    message = "Visitor id format is invalid"
            )
            String visitorId,
            @NotNull(message = "Visit type is required")
            SiteVisitType type,
            @Positive(message = "Target id must be positive")
            Long targetId
    ) {
    }

    public record TrafficSummary(
            long pageViews,
            long uniqueVisitors,
            long collectionViews,
            long inquiryCount,
            long creatorUploadCount,
            long pendingContent,
            long rejectedContent,
            long publishedContent,
            long offlineContent
    ) {
    }

    public record DailyTrend(
            LocalDate date,
            long pageViews,
            long uniqueVisitors,
            long collectionViews,
            long inquiryCount,
            long creatorUploadCount
    ) {
    }

    public record PopularContent(
            Long targetId,
            String title,
            long views,
            long uniqueVisitors
    ) {
    }

    public record AnalyticsOverview(
            int days,
            LocalDate startDate,
            LocalDate endDate,
            TrafficSummary summary,
            List<DailyTrend> trend,
            List<PopularContent> topCollections
    ) {
    }
}
