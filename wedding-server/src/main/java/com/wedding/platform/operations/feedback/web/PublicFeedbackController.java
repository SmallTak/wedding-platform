package com.wedding.platform.operations.feedback.web;

import com.wedding.platform.operations.feedback.application.PublicFeedbackService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/feedback")
public class PublicFeedbackController {

    private final PublicFeedbackService feedbackService;

    public PublicFeedbackController(PublicFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public PublicFeedbackDtos.FeedbackPage feedback(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return feedbackService.list(page, size);
    }
}
