package dev.booking.sports.shared.upload;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import dev.booking.sports.shared.exception.ApiException;
import static org.assertj.core.api.Assertions.*;

class ImageVerifierTest {
    private final ImageVerifier verifier = new ImageVerifier();
    private byte[] image(String format, int width, int height) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }
    @Test void acceptsRealPngAndJpeg() throws Exception {
        verifier.verify(image("png", 3, 2), "image/png");
        verifier.verify(image("jpg", 3, 2), "image/jpeg");
    }
    @Test void rejectsSpoofedTypeAndText() throws Exception {
        byte[] jpeg = image("jpg", 2, 2);
        assertThatThrownBy(() -> verifier.verify(jpeg, "image/png")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> verifier.verify("not a png".getBytes(), "image/png")).isInstanceOf(ApiException.class);
    }
    @Test void rejectsOversizedDimensionsBeforeDecode() throws Exception {
        byte[] wide = image("png", 6001, 1);
        assertThatThrownBy(() -> verifier.verify(wide, "image/png")).isInstanceOf(ApiException.class);
    }
}
