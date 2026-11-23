package dev.booking.sports.payment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import tools.jackson.databind.json.JsonMapper;

final class PaymentRequestHash {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private PaymentRequestHash() {}
    static String of(Object... fields) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JSON.writeValueAsString(Arrays.asList(fields)).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
