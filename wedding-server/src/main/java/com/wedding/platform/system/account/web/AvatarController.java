package com.wedding.platform.system.account.web;

import com.wedding.platform.platform.file.AvatarStorageService;
import com.wedding.platform.system.account.application.AccountService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class AvatarController {

    private final AvatarStorageService storageService;
    private final AccountService accountService;

    public AvatarController(AvatarStorageService storageService, AccountService accountService) {
        this.storageService = storageService;
        this.accountService = accountService;
    }

    @PostMapping(path = "/account/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AvatarResponse upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file
    ) {
        Long userId = AuthController.userId(jwt);
        accountService.getUser(userId);
        return new AvatarResponse(storageService.store(userId, file));
    }

    @GetMapping("/public/avatars/{userId}/{fileName:.+}")
    public ResponseEntity<Resource> avatar(
            @PathVariable Long userId,
            @PathVariable String fileName
    ) {
        Resource resource = storageService.load(userId, fileName);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .contentType(contentType(fileName))
                .body(resource);
    }

    private MediaType contentType(String fileName) {
        if (fileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (fileName.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }

    public record AvatarResponse(String path) {
    }
}
