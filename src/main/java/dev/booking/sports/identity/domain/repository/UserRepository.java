package dev.booking.sports.identity.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.booking.sports.identity.domain.enums.UserStatus;
import dev.booking.sports.identity.domain.model.User;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
	
	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	@Query("""
			SELECT DISTINCT u FROM User u
			LEFT JOIN FETCH u.userRoles ur
			LEFT JOIN FETCH ur.role r
			LEFT JOIN FETCH r.rolePermissions rp
			LEFT JOIN FETCH rp.permission
			WHERE LOWER(u.email) = LOWER(:email)
			""")
	Optional<User> findByEmailWithRoles(@Param("email") String email);

	@Query("""
			SELECT DISTINCT u FROM User u
			LEFT JOIN FETCH u.userRoles ur
			LEFT JOIN FETCH ur.role r
			LEFT JOIN FETCH r.rolePermissions rp
			LEFT JOIN FETCH rp.permission
			WHERE u.id = :id
			""")
	Optional<User> findByIdWithRolesAndPermissions(@Param("id") UUID id);

	@EntityGraph(attributePaths = {
			"userRoles",
			"userRoles.role",
			"userRoles.role.rolePermissions",
			"userRoles.role.rolePermissions.permission"
	})
	@Query("""
			SELECT u FROM User u
			WHERE (:status IS NULL OR u.status = :status)
			""")
	Page<User> findAllPaged(@Param("status") UserStatus status, Pageable pageable);

	@Query("""
			SELECT DISTINCT u FROM User u
			JOIN u.userRoles ur
			JOIN ur.role r
			WHERE r.code = :roleCode
			""")
	List<User> findAllByRoleCode(@Param("roleCode") String roleCode);
}
