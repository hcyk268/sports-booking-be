package dev.booking.sports.identity.api.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.booking.sports.identity.api.dto.request.ApplyRolePermissionsRequest;
import dev.booking.sports.identity.api.dto.request.ReplaceRolePermissionsRequest;
import dev.booking.sports.identity.api.dto.response.RolePermissionChangeRequestResponse;
import dev.booking.sports.identity.api.dto.response.RoleResponse;
import dev.booking.sports.identity.application.RbacService;
import dev.booking.sports.identity.application.RolePermissionChangeService;
import dev.booking.sports.identity.infrastructure.security.AuthPrincipal;
import dev.booking.sports.shared.api.ApiResponse;
import dev.booking.sports.shared.ratelimit.RateLimit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Role")
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

	private final RbacService rbacService;
	private final RolePermissionChangeService rolePermissionChangeService;

	@GetMapping
	@PreAuthorize("hasAuthority('ROLE_READ')")
	@Operation(summary = "List roles with permission codes (admin)")
	public ApiResponse<List<RoleResponse>> listRoles(HttpServletRequest servletRequest) {
		return ApiResponse.success(rbacService.listRoles(), servletRequest.getRequestURI());
	}

	@GetMapping("/{code}")
	@PreAuthorize("hasAuthority('ROLE_READ')")
	@Operation(summary = "Get a role by code with permission codes (admin)")
	public ApiResponse<RoleResponse> getRoleByCode(
			@PathVariable String code,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(rbacService.getRoleByCode(code), servletRequest.getRequestURI());
	}

	@PostMapping("/{code}/permissions/change-request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@PreAuthorize("hasAuthority('ROLE_MANAGE')")
	@RateLimit(action = "roles.permission-change-otp", maxRequests = 3, timeWindow = 3600)
	@Operation(summary = "Request OTP to replace a role's permissions")
	public ApiResponse<RolePermissionChangeRequestResponse> requestPermissionChange(
			@AuthenticationPrincipal AuthPrincipal principal,
			@PathVariable String code,
			@Valid @RequestBody ReplaceRolePermissionsRequest request,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(
				rolePermissionChangeService.requestChange(principal.userId(), code, request),
				servletRequest.getRequestURI());
	}

	@PutMapping("/{code}/permissions")
	@PreAuthorize("hasAuthority('ROLE_MANAGE')")
	@Operation(summary = "Confirm OTP and replace a role's permissions")
	public ApiResponse<RoleResponse> applyPermissionChange(
			@AuthenticationPrincipal AuthPrincipal principal,
			@PathVariable String code,
			@Valid @RequestBody ApplyRolePermissionsRequest request,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(
				rolePermissionChangeService.applyChange(principal.userId(), code, request),
				servletRequest.getRequestURI());
	}
}
