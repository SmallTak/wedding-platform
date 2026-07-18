package com.wedding.platform.content.collection.application;

import com.wedding.platform.content.collection.persistence.entity.ContentCategory;
import com.wedding.platform.content.collection.persistence.entity.ContentTag;
import com.wedding.platform.content.collection.persistence.repository.CollectionTagRepository;
import com.wedding.platform.content.collection.persistence.repository.ContentCategoryRepository;
import com.wedding.platform.content.collection.persistence.repository.ContentTagRepository;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.collection.web.ContentConfigDtos;
import com.wedding.platform.platform.audit.AuditLogService;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class ContentConfigService {

    private final ContentCategoryRepository categoryRepository;
    private final ContentTagRepository tagRepository;
    private final WorkCollectionRepository collectionRepository;
    private final CollectionTagRepository collectionTagRepository;
    private final SystemUserRepository userRepository;
    private final AuditLogService auditLogService;

    public ContentConfigService(
            ContentCategoryRepository categoryRepository,
            ContentTagRepository tagRepository,
            WorkCollectionRepository collectionRepository,
            CollectionTagRepository collectionTagRepository,
            SystemUserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.collectionRepository = collectionRepository;
        this.collectionTagRepository = collectionTagRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ContentConfigDtos.CategoryResponse> listCategories(Long operatorId) {
        requireAdmin(operatorId);
        return categoryRepository.findAllByDeletedFalseOrderBySortOrderAscCreatedAtAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ContentConfigDtos.CategoryResponse createCategory(
            Long operatorId,
            ContentConfigDtos.CreateCategoryRequest request,
            String ipAddress
    ) {
        SystemUser actor = requireAdmin(operatorId);
        String name = request.name().trim();
        requireCategoryNameAvailable(name, null);

        ContentCategory category = new ContentCategory();
        category.setName(name);
        category.setDescription(trimToNull(request.description()));
        category.setSortOrder(request.sortOrder());
        category.setStatus("ACTIVE");
        category.setCreatedBy(operatorId);
        category.setUpdatedBy(operatorId);
        category = categoryRepository.saveAndFlush(category);

        auditLogService.record(operatorId, actor.getAccountType(), "CONTENT_CONFIG", "CREATE_CATEGORY",
                "CONTENT_CATEGORY", category.getId(), "Content category created", ipAddress);
        return toResponse(category);
    }

    @Transactional
    public ContentConfigDtos.CategoryResponse updateCategory(
            Long operatorId,
            Long categoryId,
            ContentConfigDtos.UpdateCategoryRequest request,
            String ipAddress
    ) {
        SystemUser actor = requireAdmin(operatorId);
        ContentCategory category = categoryRepository.findByIdAndDeletedFalse(categoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND",
                        "Content category was not found"));
        requireVersion(category.getVersion(), request.version(), "CATEGORY_VERSION_CONFLICT");
        String name = request.name().trim();
        requireCategoryNameAvailable(name, categoryId);

        category.setName(name);
        category.setDescription(trimToNull(request.description()));
        category.setSortOrder(request.sortOrder());
        category.setStatus(request.status());
        category.setUpdatedBy(operatorId);
        category = categoryRepository.saveAndFlush(category);

        auditLogService.record(operatorId, actor.getAccountType(), "CONTENT_CONFIG", "UPDATE_CATEGORY",
                "CONTENT_CATEGORY", categoryId, "Content category updated", ipAddress);
        return toResponse(category);
    }

    @Transactional
    public void deleteCategory(
            Long operatorId,
            Long categoryId,
            Long version,
            String ipAddress
    ) {
        SystemUser actor = requireAdmin(operatorId);
        ContentCategory category = categoryRepository.findByIdAndDeletedFalse(categoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND",
                        "Content category was not found"));
        requireVersion(category.getVersion(), version, "CATEGORY_VERSION_CONFLICT");
        if (collectionRepository.existsByCategoryIdAndDeletedFalse(categoryId)) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_IN_USE",
                    "The category is still used by an active work collection");
        }

        category.setStatus("DISABLED");
        category.setDeleted(true);
        category.setDeletedAt(Instant.now());
        category.setUpdatedBy(operatorId);
        categoryRepository.saveAndFlush(category);
        auditLogService.record(operatorId, actor.getAccountType(), "CONTENT_CONFIG", "DELETE_CATEGORY",
                "CONTENT_CATEGORY", categoryId, "Content category logically deleted", ipAddress);
    }

    @Transactional(readOnly = true)
    public List<ContentConfigDtos.TagResponse> listTags(Long operatorId) {
        requireAdmin(operatorId);
        return tagRepository.findAllByDeletedFalseOrderBySortOrderAscCreatedAtAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ContentConfigDtos.TagResponse createTag(
            Long operatorId,
            ContentConfigDtos.CreateTagRequest request,
            String ipAddress
    ) {
        SystemUser actor = requireAdmin(operatorId);
        String name = request.name().trim();
        requireTagNameAvailable(name, null);

        ContentTag tag = new ContentTag();
        tag.setName(name);
        tag.setSortOrder(request.sortOrder());
        tag.setStatus("ACTIVE");
        tag.setCreatedBy(operatorId);
        tag.setUpdatedBy(operatorId);
        tag = tagRepository.saveAndFlush(tag);

        auditLogService.record(operatorId, actor.getAccountType(), "CONTENT_CONFIG", "CREATE_TAG",
                "CONTENT_TAG", tag.getId(), "Content tag created", ipAddress);
        return toResponse(tag);
    }

    @Transactional
    public ContentConfigDtos.TagResponse updateTag(
            Long operatorId,
            Long tagId,
            ContentConfigDtos.UpdateTagRequest request,
            String ipAddress
    ) {
        SystemUser actor = requireAdmin(operatorId);
        ContentTag tag = tagRepository.findByIdAndDeletedFalse(tagId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND",
                        "Content tag was not found"));
        requireVersion(tag.getVersion(), request.version(), "TAG_VERSION_CONFLICT");
        String name = request.name().trim();
        requireTagNameAvailable(name, tagId);

        tag.setName(name);
        tag.setSortOrder(request.sortOrder());
        tag.setStatus(request.status());
        tag.setUpdatedBy(operatorId);
        tag = tagRepository.saveAndFlush(tag);

        auditLogService.record(operatorId, actor.getAccountType(), "CONTENT_CONFIG", "UPDATE_TAG",
                "CONTENT_TAG", tagId, "Content tag updated", ipAddress);
        return toResponse(tag);
    }

    @Transactional
    public void deleteTag(
            Long operatorId,
            Long tagId,
            Long version,
            String ipAddress
    ) {
        SystemUser actor = requireAdmin(operatorId);
        ContentTag tag = tagRepository.findByIdAndDeletedFalse(tagId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND",
                        "Content tag was not found"));
        requireVersion(tag.getVersion(), version, "TAG_VERSION_CONFLICT");
        if (collectionTagRepository.countActiveCollectionReferences(tagId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "TAG_IN_USE",
                    "The tag is still used by an active work collection");
        }

        tag.setStatus("DISABLED");
        tag.setDeleted(true);
        tag.setDeletedAt(Instant.now());
        tag.setUpdatedBy(operatorId);
        tagRepository.saveAndFlush(tag);
        auditLogService.record(operatorId, actor.getAccountType(), "CONTENT_CONFIG", "DELETE_TAG",
                "CONTENT_TAG", tagId, "Content tag logically deleted", ipAddress);
    }

    private SystemUser requireAdmin(Long userId) {
        SystemUser actor = userRepository.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .filter(user -> "ACTIVE".equals(user.getAccountStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "Account is not available"));
        if (!"ADMIN".equals(actor.getAccountType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED",
                    "Only administrators can manage content configuration");
        }
        return actor;
    }

    private void requireCategoryNameAvailable(String name, Long excludedId) {
        boolean exists = excludedId == null
                ? categoryRepository.existsByNameIgnoreCase(name)
                : categoryRepository.existsByNameIgnoreCaseAndIdNot(name, excludedId);
        if (exists) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_NAME_EXISTS",
                    "A content category with this name already exists");
        }
    }

    private void requireTagNameAvailable(String name, Long excludedId) {
        boolean exists = excludedId == null
                ? tagRepository.existsByNameIgnoreCase(name)
                : tagRepository.existsByNameIgnoreCaseAndIdNot(name, excludedId);
        if (exists) {
            throw new ApiException(HttpStatus.CONFLICT, "TAG_NAME_EXISTS",
                    "A content tag with this name already exists");
        }
    }

    private void requireVersion(Long currentVersion, Long requestVersion, String code) {
        if (!currentVersion.equals(requestVersion)) {
            throw new ApiException(HttpStatus.CONFLICT, code,
                    "The content configuration was updated by another user; reload it before saving");
        }
    }

    private ContentConfigDtos.CategoryResponse toResponse(ContentCategory category) {
        return new ContentConfigDtos.CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getSortOrder(),
                category.getStatus(),
                category.getVersion(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    private ContentConfigDtos.TagResponse toResponse(ContentTag tag) {
        return new ContentConfigDtos.TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getSortOrder(),
                tag.getStatus(),
                tag.getVersion(),
                tag.getCreatedAt(),
                tag.getUpdatedAt()
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
