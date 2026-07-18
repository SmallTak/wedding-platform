package com.wedding.platform.operations.site.web;

import com.wedding.platform.operations.site.application.HomepageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/home")
public class PublicHomepageController {

    private final HomepageService homepageService;

    public PublicHomepageController(HomepageService homepageService) {
        this.homepageService = homepageService;
    }

    @GetMapping
    public HomepageDtos.PublicHomepage homepage() {
        return homepageService.publicHomepage();
    }
}
