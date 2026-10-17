package dev.booking.sports.court.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.shared.exception.ApiException;

class LocalCourtImageStorageTest {

    @TempDir Path directory;

    private LocalCourtImageStorage storage() {
        return new LocalCourtImageStorage(directory.toString());
    }

    private byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB),
                "png", bytes);
        return bytes.toByteArray();
    }

    @Test
    void storesValidatedImageWithGeneratedNameAndDeletesIt() throws Exception {
        LocalCourtImageStorage storage = storage();
        var saved = storage.save(new MockMultipartFile(
                "file", "../../wrong.jpg", "text/plain", png(8, 8)));
        assertEquals("image/png", saved.contentType());
        assertTrue(saved.storageKey().matches("[0-9a-f-]{36}\\.png"));
        byte[] read = storage.read(saved.storageKey());
        assertEquals(saved.sizeBytes(), read.length);
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(read)));
        storage.deleteQuietly(saved.storageKey());
        assertFalse(Files.exists(directory.resolve(saved.storageKey())));
        assertEquals(CourtErrorCode.COURT_IMAGE_NOT_FOUND,
                assertThrows(ApiException.class,
                        () -> storage.read(saved.storageKey())).getErrorCode());
    }

    @Test
    void storesValidJpeg() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB),
                "jpg", bytes);
        var saved = storage().save(new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", bytes.toByteArray()));
        assertEquals("image/jpeg", saved.contentType());
        assertTrue(saved.storageKey().endsWith(".jpg"));
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(
                storage().read(saved.storageKey()))));
    }

    @Test
    void rejectsTextDisguisedAsPngWithoutWritingFiles() throws Exception {
        var exception = assertThrows(ApiException.class,
                () -> storage().save(new MockMultipartFile(
                        "file", "fake.png", "image/png", "<svg>fake</svg>".getBytes())));
        assertEquals(CourtErrorCode.INVALID_COURT_IMAGE, exception.getErrorCode());
        try (var entries = Files.list(directory)) {
            assertEquals(0, entries.count());
        }
    }

    @Test
    void rejectsOversizedFile() {
        var exception = assertThrows(ApiException.class,
                () -> storage().save(new MockMultipartFile(
                        "file", "large.png", "image/png",
                        new byte[5 * 1024 * 1024 + 1])));
        assertEquals(CourtErrorCode.COURT_IMAGE_TOO_LARGE, exception.getErrorCode());
    }

    @Test
    void rejectsImageDimensionsBeforeDecodingPixels() throws Exception {
        byte[] oversized = png(6001, 1);
        var exception = assertThrows(ApiException.class,
                () -> storage().save(new MockMultipartFile(
                        "file", "wide.png", "image/png", oversized)));
        assertEquals(CourtErrorCode.INVALID_COURT_IMAGE, exception.getErrorCode());
    }

    @Test
    void rejectsPathTraversal() {
        var exception = assertThrows(ApiException.class,
                () -> storage().read("../outside.png"));
        assertEquals(CourtErrorCode.COURT_IMAGE_NOT_FOUND, exception.getErrorCode());
    }
}
