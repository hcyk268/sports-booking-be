package dev.booking.sports.identity.domain.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import dev.booking.sports.identity.domain.enums.UserStatus;
import dev.booking.sports.shared.persistence.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, exclude = "passwordHash")
public class User extends BaseEntity {

	@Column(nullable = false, length = 255)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "full_name", nullable = false, length = 100)
	private String fullName;

	@Column(length = 15)
	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private UserStatus status = UserStatus.ACTIVE;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "password_changed_at")
	private Instant passwordChangedAt;

	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<UserRole> userRoles = new HashSet<>();

	public static User register(String email, String passwordHash, String fullName, String phone) {
		User user = new User();
		user.email = email;
		user.passwordHash = passwordHash;
		user.fullName = fullName;
		user.phone = phone;
		user.status = UserStatus.PENDING_VERIFICATION;
		return user;
	}

	public void assignRole(Role role) {
		userRoles.add(UserRole.of(this, role));
	}

	public Set<String> roleCodes() {
		return userRoles.stream()
				.map(userRole -> userRole.getRole().getCode())
				.collect(Collectors.toUnmodifiableSet());
	}

	public Set<String> permissionCodes() {
		return userRoles.stream()
				.flatMap(userRole -> userRole.getRole().getRolePermissions().stream())
				.map(rolePermission -> rolePermission.getPermission().getCode())
				.collect(Collectors.toUnmodifiableSet());
	}

	public void activate(Instant verifiedAt) {
		emailVerifiedAt = verifiedAt;
		status = UserStatus.ACTIVE;
	}

	public void changePassword(String newPasswordHash, Instant changedAt) {
		passwordHash = newPasswordHash;
		passwordChangedAt = changedAt;
	}
}
