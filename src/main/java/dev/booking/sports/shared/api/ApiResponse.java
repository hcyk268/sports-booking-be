package dev.booking.sports.shared.api;

import java.time.Instant;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        Instant timestamp,
        String path
) {

    public static <T> ApiResponse<T> success(
            T data,
            String path
    ) {
        return new ApiResponse<>(
                "SUCCESS",
                "Request processed successfully",
                data,
                Instant.now(),
                path
        );
    }

    public static <T> ApiResponse<T> success(
            String code,
            String message,
            T data,
            String path
    ) {
        return new ApiResponse<>(
                code,
                message,
                data,
                Instant.now(),
                path
        );
    }

    public static ApiResponse<Void> success(
            String message,
            String path
    ) {
        return success("SUCCESS", message, null, path);
    }
}