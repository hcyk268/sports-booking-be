package dev.booking.sports.shared.api;

public record Pagination(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {

    public static Pagination from(
            int page,
            int size,
            long totalElements
    ) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        return new Pagination(
                page,
                size,
                totalElements,
                totalPages,
                page < totalPages - 1,
                page > 0
        );
    }
}
