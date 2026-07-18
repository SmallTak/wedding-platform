package com.wedding.platform.operations.feedback.persistence.repository;

import com.wedding.platform.operations.feedback.persistence.entity.FeedbackReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedbackReplyRepository extends JpaRepository<FeedbackReply, Long> {

    Optional<FeedbackReply> findByFeedbackIdAndDeletedFalse(Long feedbackId);

    List<FeedbackReply> findAllByFeedbackIdInAndDeletedFalse(List<Long> feedbackIds);
}
