package com.wedding.platform.content.review.application;

import com.wedding.platform.content.collection.persistence.entity.CollectionTag;
import com.wedding.platform.content.collection.persistence.entity.ContentCategory;
import com.wedding.platform.content.collection.persistence.entity.ContentTag;
import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import com.wedding.platform.content.collection.persistence.repository.CollectionTagRepository;
import com.wedding.platform.content.collection.persistence.repository.ContentCategoryRepository;
import com.wedding.platform.content.collection.persistence.repository.ContentTagRepository;
import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.media.persistence.entity.MediaAsset;
import com.wedding.platform.content.media.persistence.repository.CollectionPhotoRepository;
import com.wedding.platform.content.media.persistence.repository.MediaAssetRepository;
import com.wedding.platform.content.project.persistence.entity.WeddingProject;
import com.wedding.platform.content.project.persistence.repository.WeddingProjectRepository;
import com.wedding.platform.content.review.persistence.entity.ReviewActionLog;
import com.wedding.platform.content.review.persistence.entity.ReviewItem;
import com.wedding.platform.content.review.persistence.entity.ReviewItemStatus;
import com.wedding.platform.content.review.persistence.entity.ReviewItemType;
import com.wedding.platform.content.review.persistence.entity.ReviewTargetType;
import com.wedding.platform.content.review.persistence.entity.ReviewTask;
import com.wedding.platform.content.review.persistence.entity.ReviewTaskStatus;
import com.wedding.platform.content.review.persistence.repository.ReviewActionLogRepository;
import com.wedding.platform.content.review.persistence.repository.ReviewItemRepository;
import com.wedding.platform.content.review.persistence.repository.ReviewTaskRepository;
import com.wedding.platform.content.review.web.ReviewDtos;
import com.wedding.platform.content.shared.ReviewStatus;
import com.wedding.platform.platform.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewRevisionService {

    public static final List<String> PROJECT_FIELD_KEYS = List.of(
            "TITLE",
            "COUPLE_DISPLAY_NAME",
            "EVENT_DATE",
            "REGION_CODE",
            "LOCATION_TEXT",
            "DESCRIPTION"
    );

    public static final List<String> COLLECTION_FIELD_KEYS = List.of(
            "PROJECT",
            "TITLE",
            "DESCRIPTION",
            "CATEGORY",
            "TAGS",
            "COVER"
    );

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("TITLE", "标题"),
            Map.entry("COUPLE_DISPLAY_NAME", "新人展示名称"),
            Map.entry("EVENT_DATE", "婚礼日期"),
            Map.entry("REGION_CODE", "地区编码"),
            Map.entry("LOCATION_TEXT", "婚礼地点"),
            Map.entry("DESCRIPTION", "内容介绍"),
            Map.entry("PROJECT", "关联婚礼项目"),
            Map.entry("CATEGORY", "主分类"),
            Map.entry("TAGS", "内容标签"),
            Map.entry("COVER", "作品集封面")
    );

    private final ReviewTaskRepository taskRepository;
    private final ReviewItemRepository itemRepository;
    private final ReviewActionLogRepository actionRepository;
    private final WeddingProjectRepository projectRepository;
    private final ContentCategoryRepository categoryRepository;
    private final ContentTagRepository tagRepository;
    private final CollectionTagRepository collectionTagRepository;
    private final CollectionPhotoRepository photoRepository;
    private final MediaAssetRepository assetRepository;
    private final ObjectMapper objectMapper;

    public ReviewRevisionService(
            ReviewTaskRepository taskRepository,
            ReviewItemRepository itemRepository,
            ReviewActionLogRepository actionRepository,
            WeddingProjectRepository projectRepository,
            ContentCategoryRepository categoryRepository,
            ContentTagRepository tagRepository,
            CollectionTagRepository collectionTagRepository,
            CollectionPhotoRepository photoRepository,
            MediaAssetRepository assetRepository,
            ObjectMapper objectMapper
    ) {
        this.taskRepository = taskRepository;
        this.itemRepository = itemRepository;
        this.actionRepository = actionRepository;
        this.projectRepository = projectRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.collectionTagRepository = collectionTagRepository;
        this.photoRepository = photoRepository;
        this.assetRepository = assetRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReviewTask submitProject(WeddingProject project, Long operatorId) {
        ensureProjectBaseline(project);
        return createSubmission(
                ReviewTargetType.PROJECT,
                project.getId(),
                operatorId,
                projectSnapshots(project),
                List.of()
        );
    }

    @Transactional
    public ReviewTask submitCollection(
            WorkCollection collection,
            List<CollectionPhoto> photos,
            Long operatorId
    ) {
        ensureCollectionBaseline(collection, photos);
        List<PhotoSnapshot> photoSnapshots = photos.stream()
                .filter(photo -> ReviewStatus.DRAFT == photo.getReviewStatus()
                        || ReviewStatus.REJECTED == photo.getReviewStatus())
                .map(this::photoSnapshot)
                .toList();
        return createSubmission(
                ReviewTargetType.COLLECTION,
                collection.getId(),
                operatorId,
                collectionSnapshots(collection),
                photoSnapshots
        );
    }

    @Transactional
    public void ensureProjectBaseline(WeddingProject project) {
        if (taskRepository.findTopByTargetTypeAndTargetIdOrderByRevisionNoDesc(
                ReviewTargetType.PROJECT, project.getId()).isPresent()
                || ReviewStatus.DRAFT == project.getReviewStatus()) {
            return;
        }
        ReviewItemStatus fieldStatus = aggregateBaselineStatus(project.getReviewStatus());
        createBaseline(
                ReviewTargetType.PROJECT,
                project.getId(),
                project.getUpdatedBy(),
                project.getSubmittedAt() == null ? project.getUpdatedAt() : project.getSubmittedAt(),
                projectSnapshots(project),
                List.of(),
                fieldStatus,
                taskStatus(project.getReviewStatus())
        );
    }

    @Transactional
    public void ensureCollectionBaseline(WorkCollection collection, List<CollectionPhoto> photos) {
        if (taskRepository.findTopByTargetTypeAndTargetIdOrderByRevisionNoDesc(
                ReviewTargetType.COLLECTION, collection.getId()).isPresent()
                || ReviewStatus.DRAFT == collection.getReviewStatus()) {
            return;
        }
        List<PhotoSnapshot> photoSnapshots = photos.stream()
                .map(this::photoSnapshot)
                .toList();
        createBaseline(
                ReviewTargetType.COLLECTION,
                collection.getId(),
                collection.getUpdatedBy(),
                collection.getSubmittedAt() == null ? collection.getUpdatedAt() : collection.getSubmittedAt(),
                collectionSnapshots(collection),
                photoSnapshots,
                collectionFieldBaselineStatus(collection),
                taskStatus(collection.getReviewStatus())
        );
    }

    @Transactional
    public void cancelPendingSubmission(ReviewTargetType targetType, Long targetId, Long operatorId) {
        List<ReviewTask> tasks = taskRepository
                .findAllByTargetTypeAndTargetIdOrderByRevisionNoDesc(targetType, targetId);
        for (ReviewTask task : tasks) {
            if (ReviewTaskStatus.CANCELLED == task.getStatus()
                    || ReviewTaskStatus.APPROVED == task.getStatus()) {
                continue;
            }
            List<ReviewItem> pendingItems = itemRepository.findAllByTaskIdOrderByIdAsc(task.getId()).stream()
                    .filter(item -> Boolean.TRUE.equals(item.getCurrent()))
                    .filter(item -> ReviewItemStatus.PENDING == item.getStatus())
                    .toList();
            if (pendingItems.isEmpty()) {
                continue;
            }
            for (ReviewItem pending : pendingItems) {
                pending.setCurrent(false);
                restorePreviousCurrent(targetType, targetId, pending);
            }
            itemRepository.saveAll(pendingItems);
            task.setStatus(ReviewTaskStatus.CANCELLED);
            taskRepository.save(task);
            recordAction(task.getId(), null, "CANCEL_SUBMISSION", operatorId,
                    "Content changed while the submission was pending");
        }
    }

    @Transactional
    public void markPhotoRemoved(Long collectionId, Long photoId, Long operatorId) {
        itemRepository.findCurrentBusinessItem(
                        ReviewTargetType.COLLECTION,
                        collectionId,
                        ReviewItemType.PHOTO,
                        photoId
                )
                .ifPresent(item -> {
                    item.setStatus(ReviewItemStatus.REMOVED);
                    item.setRejectionReason(null);
                    item.setReviewedBy(operatorId);
                    item.setReviewedAt(Instant.now());
                    itemRepository.save(item);
                    recordAction(item.getTaskId(), item.getId(), "REMOVE_ITEM", operatorId,
                            "Collection photo logically deleted");
                    refreshTaskStatus(item.getTaskId());
                });
    }

    @Transactional
    public void reviewItems(
            ReviewTargetType targetType,
            Long targetId,
            ReviewItemType itemType,
            List<Long> itemIds,
            ReviewDtos.ReviewDecision decision,
            String reason,
            Long reviewerId
    ) {
        if (ReviewDtos.ReviewDecision.REJECT == decision && !StringUtils.hasText(reason)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REJECTION_REASON_REQUIRED",
                    "A rejection reason is required");
        }
        Set<Long> distinctIds = new LinkedHashSet<>(itemIds);
        if (distinctIds.size() != itemIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REVIEW_ITEM_SELECTION_INVALID",
                    "Review item ids must not contain duplicates");
        }
        Map<Long, ReviewItem> currentItems = itemRepository.findCurrentItemsByType(
                        targetType, targetId, itemType)
                .stream()
                .collect(Collectors.toMap(ReviewItem::getId, Function.identity()));
        List<ReviewItem> selected = itemIds.stream().map(currentItems::get).toList();
        if (selected.stream().anyMatch(item -> item == null || ReviewItemStatus.PENDING != item.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REVIEW_ITEM_SELECTION_INVALID",
                    "Every selected review item must be current and pending");
        }

        ReviewItemStatus status = ReviewDtos.ReviewDecision.APPROVE == decision
                ? ReviewItemStatus.APPROVED
                : ReviewItemStatus.REJECTED;
        String normalizedReason = status == ReviewItemStatus.REJECTED ? reason.trim() : null;
        Instant now = Instant.now();
        for (ReviewItem item : selected) {
            item.setStatus(status);
            item.setRejectionReason(normalizedReason);
            item.setReviewedBy(reviewerId);
            item.setReviewedAt(now);
        }
        itemRepository.saveAll(selected);
        selected.forEach(item -> recordAction(
                item.getTaskId(),
                item.getId(),
                status == ReviewItemStatus.APPROVED ? "APPROVE_ITEM" : "REJECT_ITEM",
                reviewerId,
                normalizedReason
        ));
        selected.stream().map(ReviewItem::getTaskId).distinct().forEach(this::refreshTaskStatus);
    }

    @Transactional
    public List<Long> reviewAllPendingFields(
            ReviewTargetType targetType,
            Long targetId,
            ReviewDtos.ReviewDecision decision,
            String reason,
            Long reviewerId
    ) {
        List<Long> pendingIds = currentItems(targetType, targetId, ReviewItemType.FIELD).stream()
                .filter(item -> ReviewItemStatus.PENDING == item.getStatus())
                .map(ReviewItem::getId)
                .toList();
        if (pendingIds.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_PENDING_FIELDS",
                    "There are no pending fields to review");
        }
        reviewItems(targetType, targetId, ReviewItemType.FIELD, pendingIds, decision, reason, reviewerId);
        return pendingIds;
    }

    @Transactional(readOnly = true)
    public List<ReviewItem> currentItems(
            ReviewTargetType targetType,
            Long targetId,
            ReviewItemType itemType
    ) {
        return itemRepository.findCurrentItemsByType(targetType, targetId, itemType);
    }

    @Transactional(readOnly = true)
    public boolean allRequiredFieldsApproved(
            ReviewTargetType targetType,
            Long targetId,
            List<String> requiredKeys
    ) {
        Map<String, ReviewItemStatus> statuses = currentItems(targetType, targetId, ReviewItemType.FIELD)
                .stream()
                .collect(Collectors.toMap(ReviewItem::getFieldKey, ReviewItem::getStatus, (left, right) -> right));
        return requiredKeys.stream().allMatch(key -> ReviewItemStatus.APPROVED == statuses.get(key));
    }

    @Transactional(readOnly = true)
    public ReviewDtos.ReviewHistoryResponse history(ReviewTargetType targetType, Long targetId) {
        List<ReviewTask> tasks = taskRepository
                .findAllByTargetTypeAndTargetIdOrderByRevisionNoDesc(targetType, targetId);
        Map<Long, List<ReviewItem>> itemsByTask = tasks.isEmpty()
                ? Map.of()
                : itemRepository.findAllByTaskIdInOrderByTaskIdDescIdAsc(
                                tasks.stream().map(ReviewTask::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(ReviewItem::getTaskId));
        List<ReviewDtos.ReviewRevisionResponse> revisions = tasks.stream()
                .map(task -> new ReviewDtos.ReviewRevisionResponse(
                        task.getId(),
                        task.getTargetType(),
                        task.getTargetId(),
                        task.getRevisionNo(),
                        task.getSubmittedBy(),
                        task.getSubmittedAt(),
                        task.getStatus(),
                        itemsByTask.getOrDefault(task.getId(), List.of()).stream()
                                .map(this::toResponse)
                                .toList()
                ))
                .toList();
        return new ReviewDtos.ReviewHistoryResponse(
                itemRepository.findCurrentItems(targetType, targetId).stream()
                        .map(this::toResponse)
                        .toList(),
                revisions
        );
    }

    private ReviewTask createSubmission(
            ReviewTargetType targetType,
            Long targetId,
            Long operatorId,
            Map<String, Snapshot> fieldSnapshots,
            List<PhotoSnapshot> photoSnapshots
    ) {
        int revisionNo = taskRepository.findMaxRevisionNo(targetType, targetId) + 1;
        List<PendingItem> pendingItems = new ArrayList<>();
        for (Map.Entry<String, Snapshot> entry : fieldSnapshots.entrySet()) {
            String snapshotJson = json(entry.getValue().payload());
            ReviewItem current = itemRepository.findCurrentField(
                    targetType, targetId, ReviewItemType.FIELD, entry.getKey()).orElse(null);
            if (current != null && current.getSnapshotJson().equals(snapshotJson)) {
                continue;
            }
            pendingItems.add(new PendingItem(
                    ReviewItemType.FIELD,
                    null,
                    entry.getKey(),
                    snapshotJson
            ));
            supersede(current);
        }
        for (PhotoSnapshot photo : photoSnapshots) {
            ReviewItem current = itemRepository.findCurrentBusinessItem(
                    targetType, targetId, ReviewItemType.PHOTO, photo.businessId()).orElse(null);
            pendingItems.add(new PendingItem(
                    ReviewItemType.PHOTO,
                    photo.businessId(),
                    null,
                    json(photo.snapshot().payload())
            ));
            supersede(current);
        }
        if (pendingItems.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_REVIEW_CHANGES",
                    "No changed or rejected content is available for review");
        }

        Instant now = Instant.now();
        ReviewTask task = new ReviewTask();
        task.setTargetType(targetType);
        task.setTargetId(targetId);
        task.setRevisionNo(revisionNo);
        task.setSubmittedBy(operatorId);
        task.setSubmittedAt(now);
        task.setStatus(ReviewTaskStatus.PENDING);
        task = taskRepository.saveAndFlush(task);

        List<ReviewItem> items = new ArrayList<>();
        for (PendingItem pending : pendingItems) {
            ReviewItem item = new ReviewItem();
            item.setTaskId(task.getId());
            item.setItemType(pending.itemType());
            item.setBusinessId(pending.businessId());
            item.setFieldKey(pending.fieldKey());
            item.setSnapshotJson(pending.snapshotJson());
            item.setRevisionNo(revisionNo);
            item.setStatus(ReviewItemStatus.PENDING);
            item.setCurrent(true);
            items.add(item);
        }
        itemRepository.saveAll(items);
        itemRepository.flush();
        recordAction(task.getId(), null, "SUBMIT_REVISION", operatorId,
                "Submitted revision " + revisionNo);
        return task;
    }

    private void createBaseline(
            ReviewTargetType targetType,
            Long targetId,
            Long operatorId,
            Instant submittedAt,
            Map<String, Snapshot> fieldSnapshots,
            List<PhotoSnapshot> photoSnapshots,
            ReviewItemStatus fieldStatus,
            ReviewTaskStatus taskStatus
    ) {
        ReviewTask task = new ReviewTask();
        task.setTargetType(targetType);
        task.setTargetId(targetId);
        task.setRevisionNo(1);
        task.setSubmittedBy(operatorId);
        task.setSubmittedAt(submittedAt == null ? Instant.now() : submittedAt);
        task.setStatus(taskStatus);
        task = taskRepository.saveAndFlush(task);

        List<ReviewItem> items = new ArrayList<>();
        for (Map.Entry<String, Snapshot> entry : fieldSnapshots.entrySet()) {
            items.add(baselineItem(task, ReviewItemType.FIELD, null, entry.getKey(),
                    entry.getValue(), fieldStatus));
        }
        for (PhotoSnapshot photo : photoSnapshots) {
            CollectionPhoto entity = photoRepository.findById(photo.businessId()).orElse(null);
            ReviewItemStatus status = entity == null
                    ? ReviewItemStatus.REMOVED
                    : photoStatus(entity.getReviewStatus());
            ReviewItem item = baselineItem(task, ReviewItemType.PHOTO, photo.businessId(), null,
                    photo.snapshot(), status);
            if (entity != null) {
                item.setRejectionReason(entity.getRejectionReason());
                item.setReviewedBy(entity.getReviewedBy());
                item.setReviewedAt(entity.getReviewedAt());
            }
            items.add(item);
        }
        itemRepository.saveAll(items);
        recordAction(task.getId(), null, "CREATE_BASELINE", operatorId,
                "Created review baseline for pre-V7 content");
    }

    private ReviewItem baselineItem(
            ReviewTask task,
            ReviewItemType itemType,
            Long businessId,
            String fieldKey,
            Snapshot snapshot,
            ReviewItemStatus status
    ) {
        ReviewItem item = new ReviewItem();
        item.setTaskId(task.getId());
        item.setItemType(itemType);
        item.setBusinessId(businessId);
        item.setFieldKey(fieldKey);
        item.setSnapshotJson(json(snapshot.payload()));
        item.setRevisionNo(task.getRevisionNo());
        item.setStatus(status);
        item.setCurrent(true);
        return item;
    }

    private void supersede(ReviewItem current) {
        if (current == null) {
            return;
        }
        current.setCurrent(false);
        itemRepository.save(current);
    }

    private void restorePreviousCurrent(
            ReviewTargetType targetType,
            Long targetId,
            ReviewItem pending
    ) {
        ReviewItem previous = itemRepository.findPreviousItems(
                        targetType,
                        targetId,
                        pending.getItemType(),
                        pending.getFieldKey(),
                        pending.getBusinessId(),
                        pending.getId()
                ).stream()
                .filter(item -> ReviewItemStatus.APPROVED == item.getStatus()
                        || ReviewItemStatus.REJECTED == item.getStatus())
                .findFirst()
                .orElse(null);
        if (previous != null) {
            previous.setCurrent(true);
            itemRepository.save(previous);
        }
        if (ReviewItemType.PHOTO == pending.getItemType() && pending.getBusinessId() != null) {
            photoRepository.findById(pending.getBusinessId()).ifPresent(photo -> {
                if (previous == null) {
                    photo.setReviewStatus(ReviewStatus.DRAFT);
                    photo.setRejectionReason(null);
                    photo.setReviewedBy(null);
                    photo.setReviewedAt(null);
                } else {
                    photo.setReviewStatus(previous.getStatus() == ReviewItemStatus.APPROVED
                            ? ReviewStatus.APPROVED
                            : ReviewStatus.REJECTED);
                    photo.setRejectionReason(previous.getRejectionReason());
                    photo.setReviewedBy(previous.getReviewedBy());
                    photo.setReviewedAt(previous.getReviewedAt());
                }
                photo.setSubmittedAt(null);
                photoRepository.save(photo);
            });
        }
    }

    private void refreshTaskStatus(Long taskId) {
        ReviewTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || ReviewTaskStatus.CANCELLED == task.getStatus()) {
            return;
        }
        List<ReviewItem> items = itemRepository.findAllByTaskIdOrderByIdAsc(taskId);
        boolean rejected = items.stream().anyMatch(item -> ReviewItemStatus.REJECTED == item.getStatus());
        boolean pending = items.stream().anyMatch(item -> ReviewItemStatus.PENDING == item.getStatus());
        task.setStatus(rejected
                ? ReviewTaskStatus.PARTIALLY_REJECTED
                : pending ? ReviewTaskStatus.PENDING : ReviewTaskStatus.APPROVED);
        taskRepository.save(task);
    }

    private Map<String, Snapshot> projectSnapshots(WeddingProject project) {
        Map<String, Snapshot> values = new LinkedHashMap<>();
        values.put("TITLE", snapshot(project.getTitle(), project.getTitle()));
        values.put("COUPLE_DISPLAY_NAME", snapshot(
                project.getCoupleDisplayName(), display(project.getCoupleDisplayName())));
        values.put("EVENT_DATE", snapshot(project.getEventDate(), project.getEventDate().toString()));
        values.put("REGION_CODE", snapshot(project.getRegionCode(), project.getRegionCode()));
        values.put("LOCATION_TEXT", snapshot(project.getLocationText(), project.getLocationText()));
        values.put("DESCRIPTION", snapshot(project.getDescription(), display(project.getDescription())));
        return values;
    }

    private Map<String, Snapshot> collectionSnapshots(WorkCollection collection) {
        Map<String, Snapshot> values = new LinkedHashMap<>();
        WeddingProject project = collection.getProjectId() == null
                ? null
                : projectRepository.findById(collection.getProjectId()).orElse(null);
        values.put("PROJECT", snapshot(
                project == null ? null : Map.of(
                        "id", project.getId(),
                        "projectCode", project.getProjectCode(),
                        "title", project.getTitle()
                ),
                project == null ? "独立作品集" : project.getProjectCode() + " · " + project.getTitle()
        ));
        values.put("TITLE", snapshot(collection.getTitle(), collection.getTitle()));
        values.put("DESCRIPTION", snapshot(
                collection.getDescription(), display(collection.getDescription())));

        ContentCategory category = categoryRepository.findById(collection.getCategoryId()).orElse(null);
        values.put("CATEGORY", snapshot(
                category == null ? collection.getCategoryId() : Map.of(
                        "id", category.getId(),
                        "name", category.getName()
                ),
                category == null ? "分类 #" + collection.getCategoryId() : category.getName()
        ));

        List<CollectionTag> relations = collectionTagRepository.findAllByCollectionId(collection.getId());
        Map<Long, ContentTag> tags = tagRepository.findAllById(relations.stream()
                        .map(relation -> relation.getId().getTagId())
                        .toList())
                .stream()
                .collect(Collectors.toMap(ContentTag::getId, Function.identity()));
        List<Map<String, Object>> tagValues = relations.stream()
                .map(relation -> tags.get(relation.getId().getTagId()))
                .filter(tag -> tag != null)
                .sorted(Comparator.comparing(ContentTag::getSortOrder).thenComparing(ContentTag::getId))
                .map(tag -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", tag.getId());
                    value.put("name", tag.getName());
                    return value;
                })
                .toList();
        String tagDisplay = tagValues.isEmpty()
                ? "未设置标签"
                : tagValues.stream().map(tag -> String.valueOf(tag.get("name")))
                .collect(Collectors.joining("、"));
        values.put("TAGS", snapshot(tagValues, tagDisplay));

        CollectionPhoto cover = collection.getCoverPhotoId() == null
                ? null
                : photoRepository.findByIdAndCollectionIdAndDeletedFalse(
                        collection.getCoverPhotoId(), collection.getId()).orElse(null);
        MediaAsset coverAsset = cover == null ? null : assetRepository.findById(cover.getAssetId()).orElse(null);
        Map<String, Object> coverValue = null;
        if (cover != null) {
            coverValue = new LinkedHashMap<>();
            coverValue.put("photoId", cover.getId());
            coverValue.put("assetId", cover.getAssetId());
            coverValue.put("originalName", coverAsset == null ? null : coverAsset.getOriginalName());
            coverValue.put("checksum", coverAsset == null ? null : coverAsset.getChecksum());
        }
        values.put("COVER", snapshot(
                coverValue,
                coverAsset == null ? "未设置封面" : coverAsset.getOriginalName()
        ));
        return values;
    }

    private PhotoSnapshot photoSnapshot(CollectionPhoto photo) {
        MediaAsset asset = assetRepository.findById(photo.getAssetId()).orElse(null);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("photoId", photo.getId());
        value.put("assetId", photo.getAssetId());
        value.put("sortOrder", photo.getSortOrder());
        value.put("originalName", asset == null ? null : asset.getOriginalName());
        value.put("mimeType", asset == null ? null : asset.getMimeType());
        value.put("width", asset == null ? null : asset.getWidth());
        value.put("height", asset == null ? null : asset.getHeight());
        value.put("checksum", asset == null ? null : asset.getChecksum());
        String display = asset == null
                ? "图片 #" + photo.getId()
                : asset.getOriginalName() + " · " + asset.getWidth() + " × " + asset.getHeight();
        return new PhotoSnapshot(photo.getId(), snapshot(value, display));
    }

    private Snapshot snapshot(Object value, String displayValue) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("value", value);
        payload.put("display", displayValue);
        return new Snapshot(payload);
    }

    private ReviewDtos.ReviewItemResponse toResponse(ReviewItem item) {
        return new ReviewDtos.ReviewItemResponse(
                item.getId(),
                item.getItemType(),
                item.getBusinessId(),
                item.getFieldKey(),
                item.getFieldKey() == null ? "作品图片" : FIELD_LABELS.getOrDefault(
                        item.getFieldKey(), item.getFieldKey()),
                item.getSnapshotJson(),
                displayValue(item.getSnapshotJson()),
                item.getRevisionNo(),
                item.getStatus(),
                item.getRejectionReason(),
                item.getReviewedBy(),
                item.getReviewedAt(),
                item.getCurrent()
        );
    }

    private ReviewItemStatus aggregateBaselineStatus(ReviewStatus status) {
        return switch (status) {
            case APPROVED -> ReviewItemStatus.APPROVED;
            case PARTIALLY_REJECTED, REJECTED -> ReviewItemStatus.REJECTED;
            default -> ReviewItemStatus.PENDING;
        };
    }

    private ReviewItemStatus collectionFieldBaselineStatus(WorkCollection collection) {
        if (ReviewStatus.PARTIALLY_REJECTED == collection.getReviewStatus()
                && !StringUtils.hasText(collection.getRejectionReason())) {
            return ReviewItemStatus.APPROVED;
        }
        return aggregateBaselineStatus(collection.getReviewStatus());
    }

    private ReviewTaskStatus taskStatus(ReviewStatus status) {
        return switch (status) {
            case APPROVED -> ReviewTaskStatus.APPROVED;
            case PARTIALLY_REJECTED, REJECTED -> ReviewTaskStatus.PARTIALLY_REJECTED;
            default -> ReviewTaskStatus.PENDING;
        };
    }

    private ReviewItemStatus photoStatus(ReviewStatus status) {
        return switch (status) {
            case APPROVED -> ReviewItemStatus.APPROVED;
            case REJECTED, PARTIALLY_REJECTED -> ReviewItemStatus.REJECTED;
            case DRAFT -> ReviewItemStatus.REMOVED;
            default -> ReviewItemStatus.PENDING;
        };
    }

    private String display(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未填写";
    }

    private String displayValue(String snapshotJson) {
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            JsonNode display = root.get("display");
            return display == null || display.isNull() ? "未填写" : display.asText();
        } catch (Exception exception) {
            return snapshotJson;
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize review snapshot", exception);
        }
    }

    private void recordAction(
            Long taskId,
            Long itemId,
            String action,
            Long operatorId,
            String reason
    ) {
        ReviewActionLog log = new ReviewActionLog();
        log.setTaskId(taskId);
        log.setReviewItemId(itemId);
        log.setAction(action);
        log.setOperatorId(operatorId);
        log.setReason(reason);
        actionRepository.save(log);
    }

    private record Snapshot(Map<String, Object> payload) {
    }

    private record PhotoSnapshot(Long businessId, Snapshot snapshot) {
    }

    private record PendingItem(
            ReviewItemType itemType,
            Long businessId,
            String fieldKey,
            String snapshotJson
    ) {
    }
}
