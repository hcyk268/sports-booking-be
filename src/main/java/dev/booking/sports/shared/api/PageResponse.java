package dev.booking.sports.shared.api;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        Pagination pagination
) {

    public static <T> PageResponse<T> of(
            List<T> items,
            int page,
            int size,
            long totalElements
    ) {
        return new PageResponse<>(
                items,
                Pagination.from(page, size, totalElements)
        );
    }
}
