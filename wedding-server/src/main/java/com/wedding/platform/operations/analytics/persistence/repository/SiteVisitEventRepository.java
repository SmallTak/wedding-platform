package com.wedding.platform.operations.analytics.persistence.repository;

import com.wedding.platform.operations.analytics.persistence.entity.SiteVisitEvent;
import com.wedding.platform.operations.analytics.persistence.entity.SiteVisitType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SiteVisitEventRepository extends JpaRepository<SiteVisitEvent, Long> {

    boolean existsByEventTypeAndTargetIdAndVisitorHashAndSessionBucket(
            SiteVisitType eventType,
            Long targetId,
            String visitorHash,
            Long sessionBucket
    );

    long countByEventTypeAndEventDateBetween(
            SiteVisitType eventType,
            LocalDate startDate,
            LocalDate endDate
    );

    @Query("""
            SELECT COUNT(DISTINCT event.visitorHash)
            FROM SiteVisitEvent event
            WHERE event.eventType = :eventType
              AND event.eventDate BETWEEN :startDate AND :endDate
            """)
    long countUniqueVisitors(
            @Param("eventType") SiteVisitType eventType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT event.eventDate AS eventDate, COUNT(event.id) AS eventCount
            FROM SiteVisitEvent event
            WHERE event.eventType = :eventType
              AND event.eventDate BETWEEN :startDate AND :endDate
            GROUP BY event.eventDate
            ORDER BY event.eventDate
            """)
    List<DailyEventCount> dailyCounts(
            @Param("eventType") SiteVisitType eventType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT event.eventDate AS eventDate, COUNT(DISTINCT event.visitorHash) AS eventCount
            FROM SiteVisitEvent event
            WHERE event.eventType = :eventType
              AND event.eventDate BETWEEN :startDate AND :endDate
            GROUP BY event.eventDate
            ORDER BY event.eventDate
            """)
    List<DailyEventCount> dailyUniqueVisitors(
            @Param("eventType") SiteVisitType eventType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT event.targetId AS targetId,
                   COUNT(event.id) AS views,
                   COUNT(DISTINCT event.visitorHash) AS uniqueVisitors
            FROM SiteVisitEvent event
            WHERE event.eventType = :eventType
              AND event.eventDate BETWEEN :startDate AND :endDate
            GROUP BY event.targetId
            ORDER BY COUNT(event.id) DESC, event.targetId
            """)
    List<TargetTraffic> topTargets(
            @Param("eventType") SiteVisitType eventType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    interface DailyEventCount {
        LocalDate getEventDate();

        Long getEventCount();
    }

    interface TargetTraffic {
        Long getTargetId();

        Long getViews();

        Long getUniqueVisitors();
    }
}
