package dev.booking.sports.notification.api.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.booking.sports.notification.api.dto.response.NotificationResponse;
import dev.booking.sports.notification.api.dto.response.UnreadCountResponse;
import dev.booking.sports.notification.application.NotificationService;
import dev.booking.sports.shared.api.ApiResponse;
import dev.booking.sports.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    @Operation(summary = "List notifications for the current user, newest first")
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @AuthenticationPrincipal(expression = "userId()") UUID recipientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ApiResponse.success(service.list(recipientId, page, size), request.getRequestURI());
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count unread notifications for the current user")
    public ApiResponse<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal(expression = "userId()") UUID recipientId,
            HttpServletRequest request) {
        return ApiResponse.success(service.unreadCount(recipientId), request.getRequestURI());
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification belonging to the current user as read")
    public ApiResponse<NotificationResponse> markRead(
            @AuthenticationPrincipal(expression = "userId()") UUID recipientId,
            @PathVariable("id") UUID notificationId,
            HttpServletRequest request) {
        return ApiResponse.success(service.markRead(recipientId, notificationId), request.getRequestURI());
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all unread notifications for the current user as read")
    public ApiResponse<Void> markAllRead(
            @AuthenticationPrincipal(expression = "userId()") UUID recipientId,
            HttpServletRequest request) {
        service.markAllRead(recipientId);
        return ApiResponse.success("All notifications marked as read", request.getRequestURI());
    }
}
