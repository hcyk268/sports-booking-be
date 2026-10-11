package dev.booking.sports.identity.api.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.booking.sports.identity.api.dto.response.PermissionResponse;
import dev.booking.sports.identity.application.RbacService;
import dev.booking.sports.shared.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Tag(name = "Permission")
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

	private final RbacService rbacService;

	@GetMapping
	@PreAuthorize("hasAuthority('ROLE_READ')")
	@Operation(summary = "List permission catalog (admin)")
	public ApiResponse<List<PermissionResponse>> listPermissions(
			@RequestParam(required = false) String module,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(rbacService.listPermissions(module), servletRequest.getRequestURI());
	}
}
