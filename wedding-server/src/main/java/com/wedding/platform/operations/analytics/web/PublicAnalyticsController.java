package com.wedding.platform.operations.analytics.web;

import com.wedding.platform.operations.analytics.application.AnalyticsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/analytics")
public class PublicAnalyticsController {

    private final AnalyticsService analyticsService;

    public PublicAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping("/visits")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordVisit(@Valid @RequestBody AnalyticsDtos.RecordVisitRequest request) {
        analyticsService.recordVisit(request);
    }
}
