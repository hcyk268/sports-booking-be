package dev.booking.sports.identity.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "role_permissions")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = { "role", "permission" })
public class RolePermission {

	@EmbeddedId
	private RolePermissionId id = new RolePermissionId();

	@ManyToOne(optional = false)
	@MapsId("roleId")
	@JoinColumn(name = "role_id", nullable = false)
	private Role role;

	@ManyToOne(optional = false)
	@MapsId("permissionId")
	@JoinColumn(name = "permission_id", nullable = false)
	private Permission permission;

	@Column(name = "assigned_at", nullable = false)
	private Instant assignedAt;

	static RolePermission of(Role role, Permission permission) {
		RolePermission rolePermission = new RolePermission();
		rolePermission.role = role;
		rolePermission.permission = permission;
		rolePermission.id = new RolePermissionId(role.getId(), permission.getId());
		return rolePermission;
	}

	@PrePersist
	void onPersist() {
		if (assignedAt == null) {
			assignedAt = Instant.now();
		}
	}
}
