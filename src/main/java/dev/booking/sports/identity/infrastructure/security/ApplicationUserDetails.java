package dev.booking.sports.identity.infrastructure.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import dev.booking.sports.identity.domain.enums.UserStatus;
import dev.booking.sports.identity.domain.model.User;

public record ApplicationUserDetails(
		UUID userId,
		String email,
		String passwordHash,
		UserStatus status,
		Set<String> roles,
		Set<String> permissions) implements UserDetails {

	public ApplicationUserDetails {
		roles = roles == null ? Set.of() : Set.copyOf(roles);
		permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
	}

	public static ApplicationUserDetails from(User user) {
		return new ApplicationUserDetails(
				user.getId(),
				user.getEmail(),
				user.getPasswordHash(),
				user.getStatus(),
				user.roleCodes(),
				user.permissionCodes());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return Stream.concat(
				roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)),
				permissions.stream().map(SimpleGrantedAuthority::new))
				.toList();
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return status != UserStatus.LOCKED;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return status != UserStatus.DISABLED;
	}
}
