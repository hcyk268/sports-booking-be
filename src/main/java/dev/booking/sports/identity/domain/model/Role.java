package dev.booking.sports.identity.domain.model;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import dev.booking.sports.shared.persistence.CreatableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Role extends CreatableEntity {

	@Column(nullable = false, unique = true, length = 50)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 255)
	private String description;

	@OneToMany(mappedBy = "role")
	private Set<UserRole> userRoles = new HashSet<>();

	@OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<RolePermission> rolePermissions = new HashSet<>();

	public void replacePermissions(Set<Permission> permissions) {
		rolePermissions.clear();
		permissions.forEach(permission -> rolePermissions.add(RolePermission.of(this, permission)));
	}

	public Set<String> permissionCodes() {
		return rolePermissions.stream()
				.map(rolePermission -> rolePermission.getPermission().getCode())
				.collect(Collectors.toUnmodifiableSet());
	}
}
