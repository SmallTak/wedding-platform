package com.wedding.platform.content.publication.application;

import com.wedding.platform.content.media.persistence.entity.MediaAsset;
import com.wedding.platform.content.media.persistence.repository.MediaAssetRepository;
import com.wedding.platform.content.project.persistence.entity.ProjectCreator;
import com.wedding.platform.content.project.persistence.entity.WeddingProject;
import com.wedding.platform.content.project.persistence.repository.ProjectCreatorRepository;
import com.wedding.platform.content.project.persistence.repository.WeddingProjectRepository;
import com.wedding.platform.content.publication.web.PublicAccessDtos;
import com.wedding.platform.content.publication.web.PublicCollectionDtos;
import com.wedding.platform.content.publication.web.PublicProjectDtos;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.operations.feedback.application.PublicFeedbackService;
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
import java.util.function.Function;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PublicProjectService {

    private static final int MAX_PAGE_SIZE = 60;

    private final WeddingProjectRepository projectRepository;
    private final ProjectCreatorRepository projectCreatorRepository;
    private final MediaAssetRepository assetRepository;
    private final SystemUserRepository userRepository;
    private final PublicCollectionService collectionService;
    private final PublicContentAccessService contentAccessService;
    private final PublicFeedbackService feedbackService;

    public PublicProjectService(
            WeddingProjectRepository projectRepository,
            ProjectCreatorRepository projectCreatorRepository,
            MediaAssetRepository assetRepository,
            SystemUserRepository userRepository,
            PublicCollectionService collectionService,
            PublicContentAccessService contentAccessService,
            PublicFeedbackService feedbackService
    ) {
        this.projectRepository = projectRepository;
        this.projectCreatorRepository = projectCreatorRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.collectionService = collectionService;
        this.contentAccessService = contentAccessService;
        this.feedbackService = feedbackService;
    }

    @Transactional(readOnly = true)
    public PublicProjectDtos.ProjectPage projects(
            int page,
            int size,
            String keyword,
            String regionCode
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_INVALID",
                    "Page must be at least 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Page<WeddingProject> result = projectRepository.findPublicProjects(
                PublishStatus.PUBLISHED,
                ContentVisibility.PUBLIC,
                trimToNull(regionCode),
                trimToNull(keyword),
                PageRequest.of(page, size)
        );
        return new PublicProjectDtos.ProjectPage(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<PublicProjectDtos.ProjectSummary> latestProjects(int size) {
        int limitedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return projectRepository.findLatestPublicProjects(
                        PublishStatus.PUBLISHED,
                        ContentVisibility.PUBLIC,
                        PageRequest.of(0, limitedSize))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicProjectDtos.ProjectSummary> projectsByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, WeddingProject> byId = projectRepository.findAllById(ids).stream()
                .filter(project -> !Boolean.TRUE.equals(project.getDeleted()))
                .filter(project -> PublishStatus.PUBLISHED == project.getPublishStatus())
                .filter(project -> ContentVisibility.PUBLIC == project.getVisibility())
                .collect(Collectors.toMap(WeddingProject::getId, Function.identity()));
        return ids.stream()
                .map(byId::get)
                .filter(project -> project != null)
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isPublicProject(Long projectId) {
        return projectRepository.findByIdAndDeletedFalseAndPublishStatus(projectId, PublishStatus.PUBLISHED)
                .filter(project -> ContentVisibility.PUBLIC == project.getVisibility())
                .isPresent();
    }

    @Transactional(readOnly = true)
    public PublicProjectDtos.ProjectDetail project(Long projectId, String accessToken) {
        WeddingProject project = publishedProject(projectId);
        requireAccess(project, accessToken);
        return new PublicProjectDtos.ProjectDetail(
                toSummary(project),
                creators(projectId),
                collectionService.projectCollections(projectId),
                feedbackService.projectFeedback(projectId)
        );
    }

    @Transactional
    public PublicContentAccessService.IssuedSession unlock(
            Long projectId,
            PublicAccessDtos.AccessRequest request,
            String clientAddress
    ) {
        WeddingProject project = projectRepository
                .findByIdAndDeletedFalseAndPublishStatus(projectId, PublishStatus.PUBLISHED)
                .filter(item -> ContentVisibility.PASSWORD == item.getVisibility())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "CONTENT_ACCESS_INVALID",
                        "The access password is incorrect"));
        return contentAccessService.unlock(
                PublicContentAccessService.ContentType.PROJECT,
                projectId,
                project.getVersion(),
                project.getAccessPasswordHash(),
                request.password(),
                clientAddress
        );
    }

    private WeddingProject publishedProject(Long projectId) {
        return projectRepository.findByIdAndDeletedFalseAndPublishStatus(projectId, PublishStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PUBLIC_PROJECT_NOT_FOUND",
                        "Published wedding project was not found"));
    }

    private void requireAccess(WeddingProject project, String accessToken) {
        if (ContentVisibility.PUBLIC == project.getVisibility()) {
            return;
        }
        if (ContentVisibility.PASSWORD == project.getVisibility()) {
            if (contentAccessService.isValid(
                    accessToken,
                    PublicContentAccessService.ContentType.PROJECT,
                    project.getId(),
                    project.getVersion())) {
                return;
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CONTENT_ACCESS_REQUIRED",
                    "An access password is required");
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "PUBLIC_PROJECT_NOT_FOUND",
                "Published wedding project was not found");
    }

    private PublicProjectDtos.ProjectSummary toSummary(WeddingProject project) {
        String coverPreviewUrl = null;
        String coverThumbnailUrl = null;
        if (project.getCoverAssetId() != null) {
            MediaAsset asset = assetRepository.findById(project.getCoverAssetId())
                    .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                    .filter(item -> "SUCCESS".equals(item.getProcessStatus()))
                    .orElse(null);
            if (asset != null) {
                coverPreviewUrl = publicUrl(asset.getPreviewPath());
                coverThumbnailUrl = publicUrl(asset.getThumbnailPath());
            }
        }
        if (coverPreviewUrl == null) {
            PublicCollectionDtos.CollectionSummary firstCollection =
                    collectionService.firstProjectCollection(project.getId());
            if (firstCollection != null) {
                coverPreviewUrl = firstCollection.coverPreviewUrl();
                coverThumbnailUrl = firstCollection.coverThumbnailUrl();
            }
        }
        return new PublicProjectDtos.ProjectSummary(
                project.getId(),
                project.getTitle(),
                project.getCoupleDisplayName(),
                project.getEventDate(),
                project.getRegionCode(),
                project.getLocationText(),
                project.getDescription(),
                coverPreviewUrl,
                coverThumbnailUrl,
                project.getPublishedAt()
        );
    }

    private List<PublicProjectDtos.CreatorSummary> creators(Long projectId) {
        List<ProjectCreator> relations = projectCreatorRepository.findAllByProjectId(projectId);
        Map<Long, SystemUser> users = new HashMap<>();
        userRepository.findAllById(relations.stream()
                        .map(relation -> relation.getId().getCreatorUserId())
                        .toList())
                .forEach(user -> users.put(user.getId(), user));
        return relations.stream()
                .map(relation -> users.get(relation.getId().getCreatorUserId()))
                .filter(user -> user != null
                        && !Boolean.TRUE.equals(user.getDeleted())
                        && "ACTIVE".equals(user.getAccountStatus()))
                .map(user -> new PublicProjectDtos.CreatorSummary(
                        user.getId(),
                        user.getDisplayName(),
                        user.getProfessionalRoles().stream()
                                .filter(role -> !Boolean.TRUE.equals(role.getDeleted()))
                                .sorted(Comparator.comparing(ProfessionalRole::getSortOrder))
                                .map(ProfessionalRole::getName)
                                .toList()
                ))
                .toList();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String publicUrl(String relativePath) {
        return "/media/" + relativePath.replace('\\', '/');
    }
}
