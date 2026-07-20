package com.wedding.platform.platform.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class BrandedImagePathResolver {

    private final String originalsDirectory;
    private final String brandedDirectory;

    public BrandedImagePathResolver(
            @Value("${app.storage.originals:originals}") String originalsDirectory,
            @Value("${app.storage.branded:branded}") String brandedDirectory
    ) {
        this.originalsDirectory = validateDirectory(originalsDirectory);
        this.brandedDirectory = validateDirectory(brandedDirectory);
    }

    public String relativePath(String originalPath) {
        Path normalized = Path.of(normalizePath(originalPath)).normalize();
        Path originalsRoot = Path.of(originalsDirectory);
        if (!normalized.startsWith(originalsRoot)
                || normalized.getNameCount() <= originalsRoot.getNameCount()) {
            throw new IllegalStateException("Original image path is outside the configured originals directory");
        }
        String relative = originalsRoot.relativize(normalized).toString().replace('\\', '/');
        int slashIndex = relative.lastIndexOf('/');
        int extensionIndex = relative.lastIndexOf('.');
        if (extensionIndex > slashIndex) {
            relative = relative.substring(0, extensionIndex);
        }
        return brandedDirectory + "/" + relative + ".jpg";
    }

    public String publicUrl(String originalPath) {
        return "/media/" + relativePath(originalPath);
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Original image path is required");
        }
        String normalized = value.trim().replace('\\', '/');
        Path path = Path.of(normalized).normalize();
        if (path.isAbsolute() || path.startsWith("..") || path.toString().isBlank()) {
            throw new IllegalStateException("Original image path must be relative to the storage root");
        }
        return path.toString().replace('\\', '/');
    }

    private String validateDirectory(String value) {
        Path path = Path.of(value).normalize();
        if (path.isAbsolute() || path.startsWith("..") || path.toString().isBlank()) {
            throw new IllegalStateException("Image storage directory must be relative to the storage root");
        }
        return path.toString().replace('\\', '/');
    }
}
