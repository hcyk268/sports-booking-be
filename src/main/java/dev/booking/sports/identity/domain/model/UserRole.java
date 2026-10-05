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
@Table(name = "user_roles")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = { "user", "role" })
public class UserRole {

	@EmbeddedId
	private UserRoleId id = new UserRoleId();

	@ManyToOne(optional = false)
	@MapsId("userId")
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(optional = false)
	@MapsId("roleId")
	@JoinColumn(name = "role_id", nullable = false)
	private Role role;

	@Column(name = "assigned_at", nullable = false)
	private Instant assignedAt;

	static UserRole of(User user, Role role) {
		UserRole userRole = new UserRole();
		userRole.user = user;
		userRole.role = role;
		return userRole;
	}

	@PrePersist
	void onPersist() {
		if (assignedAt == null) {
			assignedAt = Instant.now();
		}
	}
}
