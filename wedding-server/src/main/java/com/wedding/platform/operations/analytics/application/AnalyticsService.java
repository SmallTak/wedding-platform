package com.wedding.platform.operations.analytics.application;

import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.media.persistence.repository.CollectionPhotoRepository;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import com.wedding.platform.operations.analytics.persistence.entity.SiteVisitEvent;
import com.wedding.platform.operations.analytics.persistence.entity.SiteVisitType;
import com.wedding.platform.operations.analytics.persistence.repository.SiteVisitEventRepository;
import com.wedding.platform.operations.analytics.web.AnalyticsDtos;
import com.wedding.platform.operations.inquiry.persistence.entity.ConsultationLead;
import com.wedding.platform.operations.inquiry.persistence.repository.ConsultationLeadRepository;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long SESSION_BUCKET_SECONDS = 30 * 60;
    private static final int MIN_DAYS = 7;
    private static final int MAX_DAYS = 90;

    private final SiteVisitEventRepository visitRepository;
    private final WorkCollectionRepository collectionRepository;
    private final ConsultationLeadRepository inquiryRepository;
    private final CollectionPhotoRepository photoRepository;
    private final SystemUserRepository userRepository;

    public AnalyticsService(
            SiteVisitEventRepository visitRepository,
            WorkCollectionRepository collectionRepository,
            ConsultationLeadRepository inquiryRepository,
            CollectionPhotoRepository photoRepository,
            SystemUserRepository userRepository
    ) {
        this.visitRepository = visitRepository;
        this.collectionRepository = collectionRepository;
        this.inquiryRepository = inquiryRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
    }

    public void recordVisit(AnalyticsDtos.RecordVisitRequest request) {
        Long targetId = normalizeTarget(request.type(), request.targetId());
        if (!trackable(request.type(), targetId)) {
            return;
        }

        Instant now = Instant.now();
        String visitorHash = hashVisitor(request.visitorId().trim());
        long sessionBucket = now.getEpochSecond() / SESSION_BUCKET_SECONDS;
        if (visitRepository.existsByEventTypeAndTargetIdAndVisitorHashAndSessionBucket(
                request.type(),
                targetId,
                visitorHash,
                sessionBucket
        )) {
            return;
        }

        SiteVisitEvent event = new SiteVisitEvent();
        event.setEventDate(now.atZone(BUSINESS_ZONE).toLocalDate());
        event.setEventType(request.type());
        event.setTargetId(targetId);
        event.setVisitorHash(visitorHash);
        event.setSessionBucket(sessionBucket);
        event.setCreatedAt(now);
        try {
            visitRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ignored) {
            // Concurrent duplicate visits in the same session bucket count once.
        }
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.AnalyticsOverview overview(Long adminId, int days) {
        requireAdmin(adminId);
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANALYTICS_RANGE_INVALID",
                    "Analytics days must be between " + MIN_DAYS + " and " + MAX_DAYS);
        }

        LocalDate endDate = LocalDate.now(BUSINESS_ZONE);
        LocalDate startDate = endDate.minusDays(days - 1L);
        Instant startInstant = startDate.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        Map<LocalDate, DailyAccumulator> trend = initializeTrend(startDate, endDate);

        applyDailyCounts(trend, visitRepository.dailyCounts(
                SiteVisitType.SITE, startDate, endDate), DailyMetric.PAGE_VIEWS);
        applyDailyCounts(trend, visitRepository.dailyUniqueVisitors(
                SiteVisitType.SITE, startDate, endDate), DailyMetric.UNIQUE_VISITORS);
        applyDailyCounts(trend, visitRepository.dailyCounts(
                SiteVisitType.COLLECTION, startDate, endDate), DailyMetric.COLLECTION_VIEWS);

        List<ConsultationLead> inquiries =
                inquiryRepository.findAllByDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        startInstant,
                        endInstant
                );
        inquiries.forEach(inquiry -> trend.get(inquiry.getCreatedAt().atZone(BUSINESS_ZONE).toLocalDate())
                .inquiryCount++);

        List<CollectionPhoto> creatorUploads =
                photoRepository.findCreatorUploadsBetween(startInstant, endInstant, "CREATOR");
        creatorUploads.forEach(photo -> trend.get(photo.getCreatedAt().atZone(BUSINESS_ZONE).toLocalDate())
                .creatorUploadCount++);

        AnalyticsDtos.TrafficSummary summary = new AnalyticsDtos.TrafficSummary(
                visitRepository.countByEventTypeAndEventDateBetween(
                        SiteVisitType.SITE, startDate, endDate),
                visitRepository.countUniqueVisitors(
                        SiteVisitType.SITE, startDate, endDate),
                visitRepository.countByEventTypeAndEventDateBetween(
                        SiteVisitType.COLLECTION, startDate, endDate),
                inquiries.size(),
                creatorUploads.size(),
                collectionRepository.countByDeletedFalseAndReviewStatus(ReviewStatus.PENDING),
                collectionRepository.countByDeletedFalseAndReviewStatus(ReviewStatus.PARTIALLY_REJECTED),
                collectionRepository.countByDeletedFalseAndPublishStatus(PublishStatus.PUBLISHED),
                collectionRepository.countByDeletedFalseAndPublishStatus(PublishStatus.OFFLINE)
        );

        return new AnalyticsDtos.AnalyticsOverview(
                days,
                startDate,
                endDate,
                summary,
                trend.values().stream().map(DailyAccumulator::toResponse).toList(),
                topCollections(startDate, endDate)
        );
    }

    private Map<LocalDate, DailyAccumulator> initializeTrend(LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, DailyAccumulator> trend = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            trend.put(date, new DailyAccumulator(date));
        }
        return trend;
    }

    private void applyDailyCounts(
            Map<LocalDate, DailyAccumulator> trend,
            List<SiteVisitEventRepository.DailyEventCount> counts,
            DailyMetric metric
    ) {
        counts.forEach(item -> {
            DailyAccumulator accumulator = trend.get(item.getEventDate());
            if (accumulator != null) {
                accumulator.set(metric, item.getEventCount() == null ? 0 : item.getEventCount());
            }
        });
    }

    private List<AnalyticsDtos.PopularContent> topCollections(LocalDate startDate, LocalDate endDate) {
        return visitRepository.topTargets(
                        SiteVisitType.COLLECTION,
                        startDate,
                        endDate,
                        PageRequest.of(0, 5)
                ).stream()
                .map(item -> new AnalyticsDtos.PopularContent(
                        item.getTargetId(),
                        collectionRepository.findById(item.getTargetId())
                                .map(WorkCollection::getTitle)
                                .orElse("作品集 #" + item.getTargetId()),
                        item.getViews(),
                        item.getUniqueVisitors()
                ))
                .toList();
    }

    private Long normalizeTarget(SiteVisitType type, Long targetId) {
        if (SiteVisitType.SITE == type) {
            if (targetId != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ANALYTICS_TARGET_INVALID",
                        "Site visits must not include a target id");
            }
            return 0L;
        }
        if (targetId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANALYTICS_TARGET_REQUIRED",
                    "Content visits require a target id");
        }
        return targetId;
    }

    private boolean trackable(SiteVisitType type, Long targetId) {
        if (SiteVisitType.SITE == type) {
            return true;
        }
        return collectionRepository.findByIdAndDeletedFalseAndPublishStatus(
                        targetId, PublishStatus.PUBLISHED)
                .filter(collection -> ContentVisibility.HIDDEN != collection.getVisibility())
                .isPresent();
    }

    private String hashVisitor(String visitorId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(visitorId.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private SystemUser requireAdmin(Long adminId) {
        SystemUser admin = userRepository.findById(adminId)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .filter(user -> "ACTIVE".equals(user.getAccountStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "Account is not available"));
        if (!"ADMIN".equals(admin.getAccountType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ANALYTICS_ACCESS_DENIED",
                    "Only administrators can view analytics");
        }
        return admin;
    }

    private enum DailyMetric {
        PAGE_VIEWS,
        UNIQUE_VISITORS,
        COLLECTION_VIEWS
    }

    private static final class DailyAccumulator {

        private final LocalDate date;
        private long pageViews;
        private long uniqueVisitors;
        private long collectionViews;
        private long inquiryCount;
        private long creatorUploadCount;

        private DailyAccumulator(LocalDate date) {
            this.date = date;
        }

        private void set(DailyMetric metric, long value) {
            switch (metric) {
                case PAGE_VIEWS -> pageViews = value;
                case UNIQUE_VISITORS -> uniqueVisitors = value;
                case COLLECTION_VIEWS -> collectionViews = value;
            }
        }

        private AnalyticsDtos.DailyTrend toResponse() {
            return new AnalyticsDtos.DailyTrend(
                    date,
                    pageViews,
                    uniqueVisitors,
                    collectionViews,
                    inquiryCount,
                    creatorUploadCount
            );
        }
    }
}
