package dev.booking.sports.shared.upload.legacy;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.upload.UploadErrorCode;

/** Read-only compatibility for V15 local data; all NEW uploads use R2. */
@Component
public class LegacyImageReader {
    private final Path root;
    public LegacyImageReader(@Value("${app.storage.local.directory:${user.home}/.sports-booking/uploads/courts}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }
    public byte[] read(String key) {
        if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)"))
            throw new ApiException(UploadErrorCode.UPLOAD_NOT_FOUND);
        Path path = root.resolve(key).normalize();
        try {
            if (!path.getParent().equals(root) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(path) > 5242880) throw new ApiException(UploadErrorCode.UPLOAD_NOT_FOUND);
            return Files.readAllBytes(path);
        } catch (IOException ex) { throw new ApiException(UploadErrorCode.UPLOAD_UNAVAILABLE); }
    }
}
