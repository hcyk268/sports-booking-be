package dev.booking.sports.shared.upload;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.upload.legacy.LegacyImageReader;
import static org.assertj.core.api.Assertions.*;

class LegacyImageReaderTest {
    @TempDir Path directory;
    @Test void readsExistingLegacyFileButRejectsTraversalAndMissingFiles() throws Exception {
        var reader = new LegacyImageReader(directory.toString());
        String key = "11111111-1111-4111-8111-111111111111.png";
        Files.write(directory.resolve(key), new byte[]{1,2,3});
        assertThat(reader.read(key)).containsExactly(1,2,3);
        assertThatThrownBy(() -> reader.read("../secret.png")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> reader.read("22222222-2222-4222-8222-222222222222.png")).isInstanceOf(ApiException.class);
    }
}
