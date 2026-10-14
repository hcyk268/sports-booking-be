package dev.booking.sports.court.infrastructure.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.shared.exception.ApiException;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class LocalCourtImageStorage {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final Path root;

    public LocalCourtImageStorage(
            @Value("${app.storage.local.directory:${user.home}/.sports-booking/uploads/courts}")
            String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    public StoredImage save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(CourtErrorCode.INVALID_COURT_IMAGE);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(CourtErrorCode.COURT_IMAGE_TOO_LARGE);
        }

        NormalizedImage image = normalize(file);
        if (image.bytes().length > MAX_BYTES) {
            throw new ApiException(CourtErrorCode.COURT_IMAGE_TOO_LARGE);
        }

        String key = UUID.randomUUID() + "." + image.extension();
        Path target = safePath(key);
        try {
            Files.createDirectories(root);
            boolean created = false;
            try (var output = Files.newOutputStream(target,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                output.write(image.bytes());
            } catch (IOException exception) {
                if (created) {
                    Files.deleteIfExists(target);
                }
                throw exception;
            }
            return new StoredImage(key, image.contentType(), image.bytes().length);
        } catch (IOException exception) {
            log.error("Could not store court image", exception);
            throw new ApiException(CourtErrorCode.COURT_IMAGE_STORAGE_ERROR);
        }
    }

    public byte[] read(String key) {
        Path target = safePath(key);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(CourtErrorCode.COURT_IMAGE_NOT_FOUND);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            log.error("Could not read court image", exception);
            throw new ApiException(CourtErrorCode.COURT_IMAGE_STORAGE_ERROR);
        }
    }

    public void deleteQuietly(String key) {
        try {
            Files.deleteIfExists(safePath(key));
        } catch (IOException exception) {
            log.error("Could not remove court image {}", key, exception);
        }
    }

    private Path safePath(String key) {
        if (key == null || !key.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)")) {
            throw new ApiException(CourtErrorCode.COURT_IMAGE_NOT_FOUND);
        }
        Path result = root.resolve(key).normalize();
        if (!result.getParent().equals(root)) {
            throw new ApiException(CourtErrorCode.COURT_IMAGE_NOT_FOUND);
        }
        return result;
    }

    private NormalizedImage normalize(MultipartFile file) {
        try {
            byte[] inputBytes = file.getBytes();
            if (inputBytes.length > MAX_BYTES) {
                throw new ApiException(CourtErrorCode.COURT_IMAGE_TOO_LARGE);
            }
            try (ImageInputStream input = ImageIO.createImageInputStream(
                    new ByteArrayInputStream(inputBytes))) {
                if (input == null) {
                    throw new ApiException(CourtErrorCode.INVALID_COURT_IMAGE);
                }
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw new ApiException(CourtErrorCode.INVALID_COURT_IMAGE);
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName();
                    boolean png = format.equalsIgnoreCase("png");
                    boolean jpeg = format.equalsIgnoreCase("jpeg")
                            || format.equalsIgnoreCase("jpg");
                    if (!png && !jpeg) {
                        throw new ApiException(CourtErrorCode.INVALID_COURT_IMAGE);
                    }
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 6000 || height > 6000
                            || (long) width * height > 20_000_000) {
                        throw new ApiException(CourtErrorCode.INVALID_COURT_IMAGE);
                    }
                    BufferedImage decoded = reader.read(0);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    String extension = png ? "png" : "jpg";
                    if (!ImageIO.write(decoded, extension, output)) {
                        throw new ApiException(CourtErrorCode.INVALID_COURT_IMAGE);
                    }
                    return new NormalizedImage(
                            output.toByteArray(),
                            extension,
                            png ? "image/png" : "image/jpeg");
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException exception) {
            throw new ApiException(CourtErrorCode.INVALID_COURT_IMAGE);
        }
    }

    public record StoredImage(
            String storageKey,
            String contentType,
            long sizeBytes) {
    }

    private record NormalizedImage(
            byte[] bytes,
            String extension,
            String contentType) {
    }
}
