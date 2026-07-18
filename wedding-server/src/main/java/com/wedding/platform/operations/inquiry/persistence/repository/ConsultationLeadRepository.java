package com.wedding.platform.operations.inquiry.persistence.repository;

import com.wedding.platform.operations.inquiry.persistence.entity.ConsultationLead;
import com.wedding.platform.operations.inquiry.persistence.entity.InquiryFollowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;
import java.util.List;

public interface ConsultationLeadRepository extends JpaRepository<ConsultationLead, Long> {

    Optional<ConsultationLead> findByIdAndDeletedFalse(Long id);

    boolean existsByReferenceCode(String referenceCode);

    @Query("""
            SELECT lead
            FROM ConsultationLead lead
            WHERE lead.deleted = false
              AND (:followStatus IS NULL OR lead.followStatus = :followStatus)
              AND (
                :keyword IS NULL
                OR LOWER(lead.referenceCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(lead.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(lead.contact) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(lead.region) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(lead.serviceNeeds) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY
              CASE WHEN lead.followStatus = :newStatus THEN 0
                   WHEN lead.followStatus = :followingStatus THEN 1
                   ELSE 2 END,
              lead.createdAt DESC,
              lead.id DESC
            """)
    Page<ConsultationLead> findLeads(
            @Param("followStatus") InquiryFollowStatus followStatus,
            @Param("keyword") String keyword,
            @Param("newStatus") InquiryFollowStatus newStatus,
            @Param("followingStatus") InquiryFollowStatus followingStatus,
            Pageable pageable
    );

    long countByDeletedFalseAndFollowStatus(InquiryFollowStatus followStatus);

    List<ConsultationLead> findAllByDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Instant startAt,
            Instant endAt
    );
}
