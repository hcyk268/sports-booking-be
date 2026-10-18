package dev.booking.sports.shared.upload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;
import dev.booking.sports.shared.exception.ApiException;

@Component
public class ImageVerifier {
    public void verify(byte[] bytes, String declaredType) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                String actualType = format.equalsIgnoreCase("PNG") ? "image/png"
                        : (format.equalsIgnoreCase("JPEG") || format.equalsIgnoreCase("JPG")) ? "image/jpeg" : "";
                if (!actualType.equals(declaredType)) throw invalid();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 6000 || height > 6000
                        || (long) width * height > 20_000_000) throw invalid();
                if (reader.read(0) == null) throw invalid();
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException ex) { throw invalid(); }
    }
    private ApiException invalid() { return new ApiException(UploadErrorCode.UPLOAD_INVALID); }
}
