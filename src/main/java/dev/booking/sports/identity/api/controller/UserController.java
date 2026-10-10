package dev.booking.sports.identity.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.booking.sports.identity.api.dto.request.CreateUserRequest;
import dev.booking.sports.identity.api.dto.request.ReplaceUserRolesRequest;
import dev.booking.sports.identity.api.dto.request.UpdateUserProfileRequest;
import dev.booking.sports.identity.api.dto.response.UserSummaryResponse;
import dev.booking.sports.identity.application.UserService;
import dev.booking.sports.identity.domain.enums.UserStatus;
import dev.booking.sports.identity.infrastructure.security.AuthPrincipal;
import dev.booking.sports.shared.api.ApiResponse;
import dev.booking.sports.shared.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "User")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping("/me")
	@PreAuthorize("hasAuthority('USER_READ_SELF')")
	@Operation(summary = "Get the authenticated user's profile")
	public ApiResponse<UserSummaryResponse> getMe(
			@AuthenticationPrincipal AuthPrincipal principal,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(userService.getMe(principal.userId()), servletRequest.getRequestURI());
	}

	@PatchMapping("/me")
	@PreAuthorize("hasAuthority('USER_UPDATE_SELF')")
	@Operation(summary = "Update the authenticated user's profile")
	public ApiResponse<UserSummaryResponse> updateMe(
			@AuthenticationPrincipal AuthPrincipal principal,
			@Valid @RequestBody UpdateUserProfileRequest request,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(
				userService.updateMe(principal.userId(), request),
				servletRequest.getRequestURI());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('USER_CREATE')")
	@Operation(summary = "Create a user with roles and email a temporary password (admin)")
	public ApiResponse<UserSummaryResponse> create(
			@Valid @RequestBody CreateUserRequest request,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(userService.create(request), servletRequest.getRequestURI());
	}

	@GetMapping
	@PreAuthorize("hasAuthority('USER_READ')")
	@Operation(summary = "List users (admin)")
	public ApiResponse<PageResponse<UserSummaryResponse>> listUsers(
			@RequestParam(required = false) UserStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(userService.listUsers(status, page, size), servletRequest.getRequestURI());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('USER_READ')")
	@Operation(summary = "Get a user by id (admin)")
	public ApiResponse<UserSummaryResponse> getById(
			@PathVariable UUID id,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(userService.getById(id), servletRequest.getRequestURI());
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAuthority('USER_UPDATE')")
	@Operation(summary = "Update a user by id (admin)")
	public ApiResponse<UserSummaryResponse> updateById(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateUserProfileRequest request,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(userService.updateById(id, request), servletRequest.getRequestURI());
	}

	@PutMapping("/{id}/roles")
	@PreAuthorize("hasAuthority('USER_MANAGE_ROLE')")
	@Operation(summary = "Replace a user's roles (admin)")
	public ApiResponse<UserSummaryResponse> replaceRoles(
			@AuthenticationPrincipal AuthPrincipal principal,
			@PathVariable UUID id,
			@Valid @RequestBody ReplaceUserRolesRequest request,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(
				userService.replaceRoles(principal.userId(), id, request),
				servletRequest.getRequestURI());
	}

	@PostMapping("/{id}/lock")
	@PreAuthorize("hasAuthority('USER_LOCK')")
	@Operation(summary = "Lock a user account (admin)")
	public ApiResponse<UserSummaryResponse> lock(
			@AuthenticationPrincipal AuthPrincipal principal,
			@PathVariable UUID id,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(
				userService.lock(principal.userId(), id),
				servletRequest.getRequestURI());
	}

	@PostMapping("/{id}/unlock")
	@PreAuthorize("hasAuthority('USER_UNLOCK')")
	@Operation(summary = "Unlock a user account (admin)")
	public ApiResponse<UserSummaryResponse> unlock(
			@PathVariable UUID id,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(userService.unlock(id), servletRequest.getRequestURI());
	}

	@PostMapping("/{id}/disable")
	@PreAuthorize("hasAuthority('USER_UPDATE')")
	@Operation(summary = "Disable a user account (admin)")
	public ApiResponse<UserSummaryResponse> disable(
			@AuthenticationPrincipal AuthPrincipal principal,
			@PathVariable UUID id,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(
				userService.disable(principal.userId(), id),
				servletRequest.getRequestURI());
	}
}
