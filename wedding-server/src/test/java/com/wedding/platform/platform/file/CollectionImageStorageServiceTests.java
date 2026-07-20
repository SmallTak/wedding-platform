package com.wedding.platform.platform.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionImageStorageServiceTests {

    private static final Color RED = new Color(220, 20, 20);
    private static final Color GREEN = new Color(20, 180, 20);
    private static final Color BLUE = new Color(20, 20, 220);
    private static final Color YELLOW = new Color(220, 220, 20);

    @TempDir
    Path storageRoot;

    @Test
    void jpegExifOrientationIsAppliedToGeneratedImagesAndStoredDimensions() throws Exception {
        CollectionImageStorageService service = new CollectionImageStorageService(
                storageRoot.toString(),
                "originals",
                "previews",
                "thumbnails",
                1920,
                480,
                2560,
                "",
                new BrandedImagePathResolver("originals", "branded")
        );
        Color[][] expectedCorners = {
                null,
                {RED, GREEN, BLUE, YELLOW},
                {GREEN, RED, YELLOW, BLUE},
                {YELLOW, BLUE, GREEN, RED},
                {BLUE, YELLOW, RED, GREEN},
                {RED, BLUE, GREEN, YELLOW},
                {BLUE, RED, YELLOW, GREEN},
                {YELLOW, GREEN, BLUE, RED},
                {GREEN, YELLOW, RED, BLUE}
        };

        for (int orientation = 1; orientation <= 8; orientation++) {
            MockMultipartFile upload = new MockMultipartFile(
                    "files",
                    "orientation-" + orientation + ".jpg",
                    "image/jpeg",
                    jpegWithExifOrientation(orientation)
            );

            CollectionImageStorageService.StoredImage stored = service.store(upload);
            BufferedImage thumbnail = ImageIO.read(storageRoot.resolve(stored.thumbnailPath()).toFile());

            int expectedWidth = orientation >= 5 ? 40 : 80;
            int expectedHeight = orientation >= 5 ? 80 : 40;
            assertEquals(expectedWidth, stored.width(), "stored width for orientation " + orientation);
            assertEquals(expectedHeight, stored.height(), "stored height for orientation " + orientation);
            assertEquals(expectedWidth, thumbnail.getWidth(), "thumbnail width for orientation " + orientation);
            assertEquals(expectedHeight, thumbnail.getHeight(), "thumbnail height for orientation " + orientation);
            assertCornerColors(thumbnail, expectedCorners[orientation], orientation);
        }
    }

    @Test
    void brandWatermarkIsAppliedToWebSizedAndPreviewImagesButNotRawOriginalOrThumbnail() throws Exception {
        CollectionImageStorageService service = new CollectionImageStorageService(
                storageRoot.toString(),
                "originals",
                "previews",
                "thumbnails",
                1200,
                480,
                1400,
                "classpath:/brand/watermark.png",
                new BrandedImagePathResolver("originals", "branded")
        );
        Color background = new Color(150, 150, 150);
        byte[] source = solidPng(1800, 1200, background);
        MockMultipartFile upload = new MockMultipartFile(
                "files",
                "watermark-source.png",
                "image/png",
                source
        );

        CollectionImageStorageService.StoredImage stored = service.store(upload);
        BufferedImage branded = ImageIO.read(storageRoot.resolve(stored.brandedPath()).toFile());
        BufferedImage preview = ImageIO.read(storageRoot.resolve(stored.previewPath()).toFile());
        BufferedImage thumbnail = ImageIO.read(storageRoot.resolve(stored.thumbnailPath()).toFile());

        assertEquals(1400, branded.getWidth());
        assertEquals(933, branded.getHeight());
        assertEquals(1200, preview.getWidth());
        assertEquals(800, preview.getHeight());
        assertTrue(countChangedPixels(branded, background, 20) > 500,
                "web-sized branded image should contain the watermark");
        assertTrue(countChangedPixels(preview, background, 20) > 500,
                "preview should contain the branded watermark");
        assertTrue(countChangedPixels(thumbnail, background, 20) < 20,
                "thumbnail should remain free of the watermark");
        assertTrue(Arrays.equals(source, Files.readAllBytes(storageRoot.resolve(stored.originalPath()))),
                "raw original should remain byte-for-byte unchanged");
    }

    @Test
    void existingOriginalsCanBeBackfilledWithoutOverwritingGeneratedFiles() throws Exception {
        BrandedImagePathResolver resolver = new BrandedImagePathResolver("originals", "branded");
        CollectionImageStorageService service = new CollectionImageStorageService(
                storageRoot.toString(),
                "originals",
                "previews",
                "thumbnails",
                1200,
                480,
                800,
                "classpath:/brand/watermark.png",
                resolver
        );
        Path original = storageRoot.resolve("originals/2026/07/existing.png");
        Files.createDirectories(original.getParent());
        Files.write(original, solidPng(1000, 700, new Color(130, 130, 130)));

        CollectionImageStorageService.BrandedBackfillResult first = service.backfillBrandedImages(false);
        Path branded = storageRoot.resolve(resolver.relativePath("originals/2026/07/existing.png"));
        long firstModified = Files.getLastModifiedTime(branded).toMillis();
        CollectionImageStorageService.BrandedBackfillResult second = service.backfillBrandedImages(false);

        assertEquals(1, first.generated());
        assertEquals(0, first.skipped());
        assertEquals(0, second.generated());
        assertEquals(1, second.skipped());
        assertEquals(firstModified, Files.getLastModifiedTime(branded).toMillis());
        BufferedImage generated = ImageIO.read(branded.toFile());
        assertEquals(800, generated.getWidth());
        assertEquals(560, generated.getHeight());
        assertTrue(countChangedPixels(generated, new Color(130, 130, 130), 20) > 500);
    }

    private byte[] jpegWithExifOrientation(int orientation) throws Exception {
        BufferedImage image = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(RED);
            graphics.fillRect(0, 0, 40, 20);
            graphics.setColor(GREEN);
            graphics.fillRect(40, 0, 40, 20);
            graphics.setColor(BLUE);
            graphics.fillRect(0, 20, 40, 20);
            graphics.setColor(YELLOW);
            graphics.fillRect(40, 20, 40, 20);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", encoded);
        byte[] jpeg = encoded.toByteArray();
        byte[] exif = exifOrientationSegment(orientation);
        ByteArrayOutputStream result = new ByteArrayOutputStream(jpeg.length + exif.length);
        result.write(jpeg, 0, 2);
        result.write(exif);
        result.write(jpeg, 2, jpeg.length - 2);
        return result.toByteArray();
    }

    private byte[] solidPng(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(image, "png", encoded);
        return encoded.toByteArray();
    }

    private long countChangedPixels(BufferedImage image, Color expected, int tolerance) {
        long changed = 0;
        for (int y = image.getHeight() / 2; y < image.getHeight(); y++) {
            for (int x = image.getWidth() / 2; x < image.getWidth(); x++) {
                Color actual = new Color(image.getRGB(x, y));
                if (Math.abs(actual.getRed() - expected.getRed()) > tolerance
                        || Math.abs(actual.getGreen() - expected.getGreen()) > tolerance
                        || Math.abs(actual.getBlue() - expected.getBlue()) > tolerance) {
                    changed++;
                }
            }
        }
        return changed;
    }

    private byte[] exifOrientationSegment(int orientation) {
        ByteBuffer buffer = ByteBuffer.allocate(36).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0xff).put((byte) 0xe1);
        buffer.putShort((short) 34);
        buffer.put(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        buffer.put((byte) 'M').put((byte) 'M');
        buffer.putShort((short) 42);
        buffer.putInt(8);
        buffer.putShort((short) 1);
        buffer.putShort((short) 0x0112);
        buffer.putShort((short) 3);
        buffer.putInt(1);
        buffer.putShort((short) orientation);
        buffer.putShort((short) 0);
        buffer.putInt(0);
        return buffer.array();
    }

    private void assertCornerColors(BufferedImage image, Color[] expected, int orientation) {
        int inset = 8;
        Color[] actual = {
                new Color(image.getRGB(inset, inset)),
                new Color(image.getRGB(image.getWidth() - inset - 1, inset)),
                new Color(image.getRGB(inset, image.getHeight() - inset - 1)),
                new Color(image.getRGB(image.getWidth() - inset - 1, image.getHeight() - inset - 1))
        };
        for (int index = 0; index < expected.length; index++) {
            assertColorClose(expected[index], actual[index],
                    "corner " + index + " for orientation " + orientation);
        }
    }

    private void assertColorClose(Color expected, Color actual, String message) {
        int tolerance = 30;
        assertEquals(expected.getRed(), actual.getRed(), tolerance, message + " red");
        assertEquals(expected.getGreen(), actual.getGreen(), tolerance, message + " green");
        assertEquals(expected.getBlue(), actual.getBlue(), tolerance, message + " blue");
    }
}
