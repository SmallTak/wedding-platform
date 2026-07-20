package com.wedding.platform.platform.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandedImagePathResolverTests {

    private final BrandedImagePathResolver resolver =
            new BrandedImagePathResolver("originals", "branded");

    @Test
    void originalPathsMapToStableJpegBrandedPaths() {
        assertEquals(
                "branded/2026/07/photo.jpg",
                resolver.relativePath("originals/2026/07/photo.png")
        );
        assertEquals(
                "/media/branded/2026/07/photo.jpg",
                resolver.publicUrl("originals\\2026\\07\\photo.jpeg")
        );
    }

    @Test
    void pathsOutsideOriginalsDirectoryAreRejected() {
        assertThrows(IllegalStateException.class,
                () -> resolver.relativePath("previews/2026/07/photo.jpg"));
        assertThrows(IllegalStateException.class,
                () -> resolver.relativePath("originals/../../secret.jpg"));
        assertThrows(IllegalStateException.class,
                () -> resolver.relativePath("originals"));
    }
}
