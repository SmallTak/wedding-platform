package com.wedding.platform.content.publication.application;

import com.wedding.platform.content.collection.persistence.entity.CollectionCreator;
import com.wedding.platform.content.collection.persistence.entity.CollectionTag;
import com.wedding.platform.content.collection.persistence.entity.ContentCategory;
import com.wedding.platform.content.collection.persistence.entity.ContentTag;
import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import com.wedding.platform.content.collection.persistence.repository.CollectionCreatorRepository;
import com.wedding.platform.content.collection.persistence.repository.CollectionTagRepository;
import com.wedding.platform.content.collection.persistence.repository.ContentCategoryRepository;
import com.wedding.platform.content.collection.persistence.repository.ContentTagRepository;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.media.persistence.entity.MediaAsset;
import com.wedding.platform.content.media.persistence.repository.CollectionPhotoRepository;
import com.wedding.platform.content.media.persistence.repository.MediaAssetRepository;
import com.wedding.platform.content.project.persistence.entity.WeddingProject;
import com.wedding.platform.content.project.persistence.repository.WeddingProjectRepository;
import com.wedding.platform.content.publication.web.PublicCollectionDtos;
import com.wedding.platform.content.publication.web.PublicAccessDtos;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.ProfessionalRole;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PublicCollectionService {

    private static final int MAX_PAGE_SIZE = 60;

    private final WorkCollectionRepository collectionRepository;
    private final ContentCategoryRepository categoryRepository;
    private final ContentTagRepository tagRepository;
    private final CollectionTagRepository collectionTagRepository;
    private final CollectionCreatorRepository collectionCreatorRepository;
    private final CollectionPhotoRepository photoRepository;
    private final MediaAssetRepository assetRepository;
    private final WeddingProjectRepository projectRepository;
    private final SystemUserRepository userRepository;
    private final PublicContentAccessService contentAccessService;

    public PublicCollectionService(
            WorkCollectionRepository collectionRepository,
            ContentCategoryRepository categoryRepository,
            ContentTagRepository tagRepository,
            CollectionTagRepository collectionTagRepository,
            CollectionCreatorRepository collectionCreatorRepository,
            CollectionPhotoRepository photoRepository,
            MediaAssetRepository assetRepository,
            WeddingProjectRepository projectRepository,
            SystemUserRepository userRepository,
            PublicContentAccessService contentAccessService
    ) {
        this.collectionRepository = collectionRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.collectionTagRepository = collectionTagRepository;
        this.collectionCreatorRepository = collectionCreatorRepository;
        this.photoRepository = photoRepository;
        this.assetRepository = assetRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.contentAccessService = contentAccessService;
    }

    @Transactional(readOnly = true)
    public List<PublicCollectionDtos.CategorySummary> categories() {
        return categoryRepository.findAllByStatusAndDeletedFalseOrderBySortOrderAscCreatedAtAsc("ACTIVE").stream()
                .map(category -> new PublicCollectionDtos.CategorySummary(category.getId(), category.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicCollectionDtos.CollectionPage collections(
            int page,
            int size,
            String keyword,
            Long categoryId
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_INVALID",
                    "Page must be at least 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Page<WorkCollection> result = collectionRepository.findPublicCollections(
                PublishStatus.PUBLISHED,
                ContentVisibility.PUBLIC,
                categoryId,
                trimToNull(keyword),
                PageRequest.of(page, size)
        );
        return new PublicCollectionDtos.CollectionPage(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<PublicCollectionDtos.CollectionSummary> latestCollections(int size) {
        int limitedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return collectionRepository.findLatestPublicCollections(
                        PublishStatus.PUBLISHED,
                        ContentVisibility.PUBLIC,
                        PageRequest.of(0, limitedSize))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicCollectionDtos.CollectionSummary> collectionsByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, WorkCollection> byId = collectionRepository.findAllById(ids).stream()
                .filter(collection -> !Boolean.TRUE.equals(collection.getDeleted()))
                .filter(collection -> PublishStatus.PUBLISHED == collection.getPublishStatus())
                .filter(collection -> ContentVisibility.PUBLIC == collection.getVisibility())
                .collect(Collectors.toMap(WorkCollection::getId, Function.identity()));
        return ids.stream()
                .map(byId::get)
                .filter(collection -> collection != null)
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isPublicCollection(Long collectionId) {
        return collectionRepository.findByIdAndDeletedFalseAndPublishStatusAndVisibility(
                        collectionId,
                        PublishStatus.PUBLISHED,
                        ContentVisibility.PUBLIC)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public PublicCollectionDtos.CollectionDetail collection(Long collectionId, String accessToken) {
        WorkCollection collection = collectionRepository
                .findByIdAndDeletedFalseAndPublishStatus(collectionId, PublishStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PUBLIC_COLLECTION_NOT_FOUND",
                        "Published collection was not found"));
        requireAccess(collection, accessToken);
        List<CollectionPhoto> photos = photoRepository
                .findAllByCollectionIdAndDeletedFalseAndReviewStatusOrderBySortOrderAscIdAsc(
                        collectionId,
                        ReviewStatus.APPROVED
                );
        Map<Long, MediaAsset> assets = assetRepository.findAllById(
                        photos.stream().map(CollectionPhoto::getAssetId).toList())
                .stream()
                .collect(Collectors.toMap(MediaAsset::getId, Function.identity()));
        List<PublicCollectionDtos.PublicPhoto> publicPhotos = photos.stream()
                .map(photo -> {
                    MediaAsset asset = assets.get(photo.getAssetId());
                    if (asset == null) {
                        return null;
                    }
                    return new PublicCollectionDtos.PublicPhoto(
                            photo.getId(),
                            asset.getWidth(),
                            asset.getHeight(),
                            publicUrl(asset.getOriginalPath()),
                            publicUrl(asset.getPreviewPath()),
                            publicUrl(asset.getThumbnailPath()),
                            photo.getSortOrder()
                    );
                })
                .filter(photo -> photo != null)
                .toList();
        return new PublicCollectionDtos.CollectionDetail(toSummary(collection), publicPhotos);
    }

    @Transactional
    public PublicContentAccessService.IssuedSession unlock(
            Long collectionId,
            PublicAccessDtos.AccessRequest request,
            String clientAddress
    ) {
        WorkCollection collection = collectionRepository
                .findByIdAndDeletedFalseAndPublishStatus(collectionId, PublishStatus.PUBLISHED)
                .filter(item -> ContentVisibility.PASSWORD == item.getVisibility())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "CONTENT_ACCESS_INVALID",
                        "The access password is incorrect"));
        return contentAccessService.unlock(
                PublicContentAccessService.ContentType.COLLECTION,
                collectionId,
                collection.getVersion(),
                collection.getAccessPasswordHash(),
                request.password(),
                clientAddress
        );
    }

    @Transactional(readOnly = true)
    public List<PublicCollectionDtos.CollectionSummary> projectCollections(Long projectId) {
        return collectionRepository.findPublishedCollectionsByProject(
                        projectId,
                        PublishStatus.PUBLISHED,
                        ContentVisibility.PUBLIC)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicCollectionDtos.CollectionSummary firstProjectCollection(Long projectId) {
        return collectionRepository.findPublishedCollectionsByProject(
                        projectId,
                        PublishStatus.PUBLISHED,
                        ContentVisibility.PUBLIC)
                .stream()
                .findFirst()
                .map(this::toSummary)
                .orElse(null);
    }

    private PublicCollectionDtos.CollectionSummary toSummary(WorkCollection collection) {
        ContentCategory category = categoryRepository.findById(collection.getCategoryId()).orElse(null);
        PublicCollectionDtos.CategorySummary categorySummary = category == null
                ? null
                : new PublicCollectionDtos.CategorySummary(category.getId(), category.getName());

        List<CollectionTag> tagRelations = collectionTagRepository.findAllByCollectionId(collection.getId());
        Map<Long, ContentTag> tagsById = tagRepository.findAllById(tagRelations.stream()
                        .map(relation -> relation.getId().getTagId())
                        .toList())
                .stream()
                .collect(Collectors.toMap(ContentTag::getId, Function.identity()));
        List<PublicCollectionDtos.TagSummary> tags = tagRelations.stream()
                .map(relation -> tagsById.get(relation.getId().getTagId()))
                .filter(tag -> tag != null && !Boolean.TRUE.equals(tag.getDeleted()))
                .sorted(Comparator.comparing(ContentTag::getSortOrder).thenComparing(ContentTag::getId))
                .map(tag -> new PublicCollectionDtos.TagSummary(tag.getId(), tag.getName()))
                .toList();

        List<CollectionCreator> creatorRelations = collectionCreatorRepository.findAllByCollectionId(
                collection.getId());
        Map<Long, SystemUser> users = new HashMap<>();
        userRepository.findAllById(creatorRelations.stream()
                        .map(relation -> relation.getId().getCreatorUserId())
                        .toList())
                .forEach(user -> users.put(user.getId(), user));
        List<PublicCollectionDtos.CreatorSummary> creators = creatorRelations.stream()
                .map(relation -> users.get(relation.getId().getCreatorUserId()))
                .filter(user -> user != null
                        && !Boolean.TRUE.equals(user.getDeleted())
                        && "ACTIVE".equals(user.getAccountStatus()))
                .map(user -> new PublicCollectionDtos.CreatorSummary(
                        user.getId(),
                        user.getDisplayName(),
                        user.getProfessionalRoles().stream()
                                .filter(role -> !Boolean.TRUE.equals(role.getDeleted()))
                                .sorted(Comparator.comparing(ProfessionalRole::getSortOrder))
                                .map(ProfessionalRole::getName)
                                .toList()
                ))
                .toList();

        String coverOriginalUrl = null;
        String coverPreviewUrl = null;
        String coverThumbnailUrl = null;
        if (collection.getCoverPhotoId() != null) {
            CollectionPhoto cover = photoRepository
                    .findByIdAndCollectionIdAndDeletedFalse(collection.getCoverPhotoId(), collection.getId())
                    .filter(photo -> ReviewStatus.APPROVED == photo.getReviewStatus())
                    .orElse(null);
            if (cover != null) {
                MediaAsset asset = assetRepository.findById(cover.getAssetId()).orElse(null);
                if (asset != null) {
                    coverOriginalUrl = publicUrl(asset.getOriginalPath());
                    coverPreviewUrl = publicUrl(asset.getPreviewPath());
                    coverThumbnailUrl = publicUrl(asset.getThumbnailPath());
                }
            }
        }

        return new PublicCollectionDtos.CollectionSummary(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                categorySummary,
                tags,
                coverOriginalUrl,
                coverPreviewUrl,
                coverThumbnailUrl,
                collection.getPublishedAt(),
                collection.getFeatured(),
                collection.getPinned(),
                creators,
                publicProject(collection.getProjectId())
        );
    }

    private PublicCollectionDtos.ProjectSummary publicProject(Long projectId) {
        if (projectId == null) {
            return null;
        }
        WeddingProject project = projectRepository.findByIdAndDeletedFalse(projectId).orElse(null);
        if (project == null
                || PublishStatus.PUBLISHED != project.getPublishStatus()
                || ContentVisibility.PUBLIC != project.getVisibility()) {
            return null;
        }
        return new PublicCollectionDtos.ProjectSummary(
                project.getId(),
                project.getTitle(),
                project.getEventDate(),
                project.getLocationText()
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String publicUrl(String relativePath) {
        return "/media/" + relativePath.replace('\\', '/');
    }

    private void requireAccess(WorkCollection collection, String accessToken) {
        if (ContentVisibility.PUBLIC == collection.getVisibility()) {
            return;
        }
        if (ContentVisibility.PASSWORD == collection.getVisibility()) {
            if (contentAccessService.isValid(
                    accessToken,
                    PublicContentAccessService.ContentType.COLLECTION,
                    collection.getId(),
                    collection.getVersion())) {
                return;
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CONTENT_ACCESS_REQUIRED",
                    "An access password is required");
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "PUBLIC_COLLECTION_NOT_FOUND",
                "Published collection was not found");
    }
}
