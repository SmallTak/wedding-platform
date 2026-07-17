package com.wedding.platform.platform.file;

import com.wedding.platform.platform.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CollectionImageStorageService {

    private static final long MAX_IMAGE_BYTES = 30L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 80_000_000L;
    private static final int MAX_IMAGE_DIMENSION = 20_000;
    private static final Set<String> JPEG_FORMATS = Set.of("jpeg", "jpg");

    private final Path storageRoot;
    private final String originalsDirectory;
    private final String previewsDirectory;
    private final String thumbnailsDirectory;
    private final int previewMaxWidth;
    private final int thumbnailMaxWidth;
    private final String watermarkText;

    public CollectionImageStorageService(
            @Value("${app.storage.root}") String storageRoot,
            @Value("${app.storage.originals:originals}") String originalsDirectory,
            @Value("${app.storage.previews:previews}") String previewsDirectory,
            @Value("${app.storage.thumbnails:thumbnails}") String thumbnailsDirectory,
            @Value("${app.storage.images.preview-max-width:1920}") int previewMaxWidth,
            @Value("${app.storage.images.thumbnail-max-width:480}") int thumbnailMaxWidth,
            @Value("${app.storage.images.watermark-text:photo.shop-hz.top}") String watermarkText
    ) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.originalsDirectory = validateDirectory(originalsDirectory);
        this.previewsDirectory = validateDirectory(previewsDirectory);
        this.thumbnailsDirectory = validateDirectory(thumbnailsDirectory);
        this.previewMaxWidth = requirePositive(previewMaxWidth, "Preview max width");
        this.thumbnailMaxWidth = requirePositive(thumbnailMaxWidth, "Thumbnail max width");
        this.watermarkText = watermarkText == null ? "" : watermarkText.trim();
    }

    public StoredImage store(MultipartFile file) {
        validateUpload(file);
        String originalName = sanitizeOriginalName(file.getOriginalFilename());
        String identifier = UUID.randomUUID().toString();
        Path temporaryDirectory = storageRoot.resolve(".tmp").normalize();
        Path uploadTemporary = temporaryDirectory.resolve(identifier + ".upload");
        Path previewTemporary = temporaryDirectory.resolve(identifier + ".preview.jpg");
        Path thumbnailTemporary = temporaryDirectory.resolve(identifier + ".thumbnail.jpg");

        Path originalTarget = null;
        Path previewTarget = null;
        Path thumbnailTarget = null;
        try {
            Files.createDirectories(temporaryDirectory);
            String checksum = copyUpload(file, uploadTemporary);
            DecodedImage decoded = decode(uploadTemporary);

            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            String datePath = DateTimeFormatter.ofPattern("yyyy/MM").format(today);
            String originalExtension = "image/png".equals(decoded.mimeType()) ? ".png" : ".jpg";
            String storageName = identifier + originalExtension;
            String originalPath = originalsDirectory + "/" + datePath + "/" + storageName;
            String previewPath = previewsDirectory + "/" + datePath + "/" + identifier + ".jpg";
            String thumbnailPath = thumbnailsDirectory + "/" + datePath + "/" + identifier + ".jpg";

            originalTarget = resolveStoragePath(originalPath);
            previewTarget = resolveStoragePath(previewPath);
            thumbnailTarget = resolveStoragePath(thumbnailPath);

            BufferedImage preview = resize(decoded.image(), previewMaxWidth);
            applyWatermark(preview);
            writeJpeg(preview, previewTemporary, 0.88f);
            BufferedImage thumbnail = resize(decoded.image(), thumbnailMaxWidth);
            writeJpeg(thumbnail, thumbnailTemporary, 0.82f);

            Files.createDirectories(originalTarget.getParent());
            Files.createDirectories(previewTarget.getParent());
            Files.createDirectories(thumbnailTarget.getParent());
            move(uploadTemporary, originalTarget);
            move(previewTemporary, previewTarget);
            move(thumbnailTemporary, thumbnailTarget);

            return new StoredImage(
                    originalName,
                    storageName,
                    decoded.mimeType(),
                    file.getSize(),
                    decoded.width(),
                    decoded.height(),
                    originalPath,
                    previewPath,
                    thumbnailPath,
                    checksum
            );
        } catch (ApiException exception) {
            cleanup(uploadTemporary, previewTemporary, thumbnailTemporary);
            cleanup(originalTarget, previewTarget, thumbnailTarget);
            throw exception;
        } catch (IOException exception) {
            cleanup(uploadTemporary, previewTemporary, thumbnailTemporary);
            cleanup(originalTarget, previewTarget, thumbnailTarget);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_PROCESS_FAILED",
                    "The image could not be stored or processed");
        }
    }

    public void cleanup(StoredImage image) {
        if (image == null) {
            return;
        }
        cleanup(
                resolveStoragePath(image.originalPath()),
                resolveStoragePath(image.previewPath()),
                resolveStoragePath(image.thumbnailPath())
        );
    }

    private String copyUpload(MultipartFile file, Path target) throws IOException {
        MessageDigest digest = sha256();
        long copied = 0;
        try (InputStream input = new DigestInputStream(file.getInputStream(), digest);
             OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                copied += read;
                if (copied > MAX_IMAGE_BYTES) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE",
                            "Each image must not exceed 30 MB");
                }
                output.write(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private DecodedImage decode(Path source) {
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) {
                throw invalidFormat();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidFormat();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String mimeType;
                if (JPEG_FORMATS.contains(format)) {
                    mimeType = "image/jpeg";
                } else if ("png".equals(format)) {
                    mimeType = "image/png";
                } else {
                    throw invalidFormat();
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalidFormat();
                }
                return new DecodedImage(toRgb(image), mimeType, width, height);
            } finally {
                reader.dispose();
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidFormat();
        }
    }

    private BufferedImage resize(BufferedImage source, int maxSize) {
        int longestEdge = Math.max(source.getWidth(), source.getHeight());
        double scale = Math.min(1d, (double) maxSize / longestEdge);
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private void applyWatermark(BufferedImage image) {
        if (watermarkText.isBlank()) {
            return;
        }
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int fontSize = Math.max(14, Math.min(48, Math.min(image.getWidth(), image.getHeight()) / 24));
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
            graphics.setFont(font);
            FontMetrics metrics = graphics.getFontMetrics();
            int margin = Math.max(12, fontSize / 2);
            int textWidth = metrics.stringWidth(watermarkText);
            int x = Math.max(margin, image.getWidth() - textWidth - margin);
            int y = Math.max(metrics.getAscent() + margin, image.getHeight() - margin);

            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            graphics.setColor(Color.BLACK);
            graphics.drawString(watermarkText, x + 2, y + 2);
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.78f));
            graphics.setColor(Color.WHITE);
            graphics.drawString(watermarkText, x, y);
        } finally {
            graphics.dispose();
        }
    }

    private void writeJpeg(BufferedImage image, Path target, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG writer is not available");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_EMPTY", "Image file is required");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE",
                    "Each image must not exceed 30 MB");
        }
    }

    private void validateDimensions(int width, int height) {
        long pixels = (long) width * height;
        if (width < 1 || height < 1
                || width > MAX_IMAGE_DIMENSION
                || height > MAX_IMAGE_DIMENSION
                || pixels > MAX_IMAGE_PIXELS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_DIMENSIONS_INVALID",
                    "Image dimensions exceed the supported limit");
        }
    }

    private String sanitizeOriginalName(String value) {
        String name = "image";
        if (value != null) {
            try {
                name = Path.of(value).getFileName().toString().trim();
            } catch (RuntimeException ignored) {
                name = "image";
            }
        }
        if (name.isBlank()) {
            name = "image";
        }
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private Path resolveStoragePath(String relativePath) {
        Path target = storageRoot.resolve(relativePath).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_PATH_INVALID",
                    "Image storage path is invalid");
        }
        return target;
    }

    private String validateDirectory(String value) {
        Path path = Path.of(value).normalize();
        if (path.isAbsolute() || path.startsWith("..") || path.toString().isBlank()) {
            throw new IllegalStateException("Image storage directory must be relative to the storage root");
        }
        return path.toString().replace('\\', '/');
    }

    private int requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalStateException(name + " must be positive");
        }
        return value;
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private ApiException invalidFormat() {
        return new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_FORMAT_INVALID",
                "Image content must be a valid JPEG or PNG file");
    }

    private void cleanup(Path... paths) {
        for (Path path : paths) {
            if (path == null) {
                continue;
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Generated files without committed database records are best-effort cleanup only.
            }
        }
    }

    private record DecodedImage(BufferedImage image, String mimeType, int width, int height) {
    }

    public record StoredImage(
            String originalName,
            String storageName,
            String mimeType,
            long fileSize,
            int width,
            int height,
            String originalPath,
            String previewPath,
            String thumbnailPath,
            String checksum
    ) {
    }
}
