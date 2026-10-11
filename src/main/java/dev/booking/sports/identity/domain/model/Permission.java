package dev.booking.sports.identity.domain.model;

import java.util.HashSet;
import java.util.Set;

import dev.booking.sports.shared.persistence.CreatableEntity;

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
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Permission extends CreatableEntity {

	@Column(nullable = false, unique = true, length = 100)
	private String code;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(length = 255)
	private String description;

	@Column(nullable = false, length = 50)
	private String module;

	@OneToMany(mappedBy = "permission")
	private Set<RolePermission> rolePermissions = new HashSet<>();
}
