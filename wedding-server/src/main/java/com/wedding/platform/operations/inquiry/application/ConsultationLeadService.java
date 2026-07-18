package com.wedding.platform.operations.inquiry.application;

import com.wedding.platform.operations.inquiry.persistence.entity.ConsultationLead;
import com.wedding.platform.operations.inquiry.persistence.entity.InquiryFollowStatus;
import com.wedding.platform.operations.inquiry.persistence.repository.ConsultationLeadRepository;
import com.wedding.platform.operations.notification.application.UserNotificationService;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationRelatedType;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationType;
import com.wedding.platform.operations.inquiry.web.InquiryDtos;
import com.wedding.platform.platform.audit.AuditLogService;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ConsultationLeadService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SUBMISSIONS = 5;
    private static final long SUBMISSION_WINDOW_SECONDS = 3600;
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final ConsultationLeadRepository leadRepository;
    private final SystemUserRepository userRepository;
    private final UserNotificationService notificationService;
    private final AuditLogService auditLogService;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Deque<Instant>> submissionBuckets = new ConcurrentHashMap<>();
    private final AtomicLong submissionCount = new AtomicLong();

    public ConsultationLeadService(
            ConsultationLeadRepository leadRepository,
            SystemUserRepository userRepository,
            AuditLogService auditLogService,
            UserNotificationService notificationService
    ) {
        this.leadRepository = leadRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    @Transactional
    public InquiryDtos.InquiryReceipt create(
            InquiryDtos.CreateInquiryRequest request,
            String clientIp
    ) {
        if (StringUtils.hasText(request.website())) {
            return new InquiryDtos.InquiryReceipt("RECEIVED", Instant.now());
        }
        enforceRateLimit(clientIp);
        ConsultationLead lead = new ConsultationLead();
        lead.setReferenceCode(generateReferenceCode());
        lead.setName(request.name().trim());
        lead.setContact(request.contact().trim());
        lead.setWeddingDate(request.weddingDate());
        lead.setRegion(trimToNull(request.region()));
        lead.setServiceNeeds(request.serviceNeeds().trim());
        lead.setRemark(trimToNull(request.remark()));
        lead.setFollowStatus(InquiryFollowStatus.NEW);
        lead.setSource("WEBSITE");
        lead = leadRepository.save(lead);
        notificationService.notifyAdmins(
                null,
                UserNotificationType.CONSULTATION_NEW,
                "新的咨询线索",
                "收到新的官网咨询线索，编号为 " + lead.getReferenceCode() + "，请及时跟进。",
                UserNotificationRelatedType.INQUIRY,
                lead.getId()
        );
        auditLogService.record(
                null,
                null,
                "INQUIRY",
                "CREATE_INQUIRY",
                "CONSULTATION_LEAD",
                lead.getId(),
                "Website consultation submitted",
                clientIp
        );
        return new InquiryDtos.InquiryReceipt(lead.getReferenceCode(), lead.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public InquiryDtos.InquiryPage list(
            Long adminId,
            int page,
            int size,
            String keyword,
            InquiryFollowStatus followStatus
    ) {
        requireAdmin(adminId);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_INVALID",
                    "Page must be at least 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String normalizedKeyword = trimToNull(keyword);
        Page<ConsultationLead> result = leadRepository.findLeads(
                followStatus,
                normalizedKeyword,
                InquiryFollowStatus.NEW,
                InquiryFollowStatus.FOLLOWING,
                PageRequest.of(page, size)
        );
        return new InquiryDtos.InquiryPage(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    public InquiryDtos.InquiryResponse update(
            Long adminId,
            Long leadId,
            InquiryDtos.UpdateInquiryRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        ConsultationLead lead = leadRepository.findByIdAndDeletedFalse(leadId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INQUIRY_NOT_FOUND",
                        "Consultation lead was not found"));
        if (!lead.getVersion().equals(request.version())) {
            throw new ApiException(HttpStatus.CONFLICT, "INQUIRY_VERSION_CONFLICT",
                    "The lead was updated by another user; reload it before saving");
        }
        lead.setFollowStatus(request.followStatus());
        lead.setFollowNote(trimToNull(request.followNote()));
        lead.setAssignedAdminId(adminId);
        lead = leadRepository.save(lead);
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "INQUIRY",
                "UPDATE_INQUIRY",
                "CONSULTATION_LEAD",
                leadId,
                request.followStatus().name(),
                ipAddress
        );
        return toResponse(lead);
    }

    private InquiryDtos.InquiryResponse toResponse(ConsultationLead lead) {
        return new InquiryDtos.InquiryResponse(
                lead.getId(),
                lead.getReferenceCode(),
                lead.getName(),
                lead.getContact(),
                lead.getWeddingDate(),
                lead.getRegion(),
                lead.getServiceNeeds(),
                lead.getRemark(),
                lead.getFollowStatus(),
                lead.getFollowNote(),
                lead.getAssignedAdminId(),
                lead.getSource(),
                lead.getCreatedAt(),
                lead.getUpdatedAt(),
                lead.getVersion()
        );
    }

    private void enforceRateLimit(String clientIp) {
        String bucketKey = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        Instant now = Instant.now();
        if ((submissionCount.incrementAndGet() & 255L) == 0) {
            cleanupExpiredBuckets(now);
        }
        submissionBuckets.compute(bucketKey, (ignored, current) -> {
            Deque<Instant> bucket = current == null ? new ArrayDeque<>() : current;
            pruneExpiredSubmissions(bucket, now);
            if (bucket.size() >= MAX_SUBMISSIONS) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "INQUIRY_RATE_LIMITED",
                        "Too many consultation requests; please try again later");
            }
            bucket.addLast(now);
            return bucket;
        });
    }

    private void cleanupExpiredBuckets(Instant now) {
        submissionBuckets.forEach((key, ignored) ->
                submissionBuckets.computeIfPresent(key, (bucketKey, bucket) -> {
                    pruneExpiredSubmissions(bucket, now);
                    return bucket.isEmpty() ? null : bucket;
                }));
    }

    private void pruneExpiredSubmissions(Deque<Instant> bucket, Instant now) {
        while (!bucket.isEmpty()
                && bucket.peekFirst().plusSeconds(SUBMISSION_WINDOW_SECONDS).isBefore(now)) {
            bucket.removeFirst();
        }
    }

    private String generateReferenceCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = "INQ" + CODE_DATE.format(LocalDate.now(java.time.ZoneOffset.UTC))
                    + String.format("%06d", random.nextInt(1_000_000));
            if (!leadRepository.existsByReferenceCode(code)) {
                return code;
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INQUIRY_CODE_UNAVAILABLE",
                "Could not allocate a consultation reference");
    }

    private SystemUser requireAdmin(Long adminId) {
        SystemUser admin = userRepository.findById(adminId)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .filter(user -> "ACTIVE".equals(user.getAccountStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "Account is not available"));
        if (!"ADMIN".equals(admin.getAccountType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INQUIRY_ACCESS_DENIED",
                    "Only administrators can manage consultation leads");
        }
        return admin;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
