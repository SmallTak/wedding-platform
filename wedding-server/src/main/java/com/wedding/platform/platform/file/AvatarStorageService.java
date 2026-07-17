package com.wedding.platform.platform.file;

import com.wedding.platform.platform.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
public class AvatarStorageService {

    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final Path avatarRoot;

    public AvatarStorageService(
            @Value("${app.storage.root}") String storageRoot,
            @Value("${app.storage.avatars:avatars}") String avatarsDirectory
    ) {
        this.avatarRoot = Path.of(storageRoot).toAbsolutePath().normalize().resolve(avatarsDirectory).normalize();
    }

    public String store(Long userId, MultipartFile file) {
        String contentType = file.getContentType();
        String extension = EXTENSIONS.get(contentType);
        if (file.isEmpty() || extension == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AVATAR_TYPE_INVALID", "Avatar must be a JPG, PNG, or WebP image");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AVATAR_TOO_LARGE", "Avatar must not exceed 5 MB");
        }

        String fileName = UUID.randomUUID() + extension;
        Path userDirectory = avatarRoot.resolve(userId.toString()).normalize();
        Path target = userDirectory.resolve(fileName).normalize();
        if (!target.startsWith(userDirectory)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AVATAR_PATH_INVALID", "Avatar path is invalid");
        }
        try {
            Files.createDirectories(userDirectory);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/api/public/avatars/" + userId + "/" + fileName;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AVATAR_STORE_FAILED", "Avatar could not be stored");
        }
    }

    public Resource load(Long userId, String fileName) {
        if (!fileName.matches("^[0-9a-fA-F-]{36}\\.(jpg|png|webp)$")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AVATAR_NOT_FOUND", "Avatar was not found");
        }
        Path userDirectory = avatarRoot.resolve(userId.toString()).normalize();
        Path target = userDirectory.resolve(fileName).normalize();
        if (!target.startsWith(userDirectory)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AVATAR_NOT_FOUND", "Avatar was not found");
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "AVATAR_NOT_FOUND", "Avatar was not found");
            }
            return resource;
        } catch (java.net.MalformedURLException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AVATAR_NOT_FOUND", "Avatar was not found");
        }
    }
}
