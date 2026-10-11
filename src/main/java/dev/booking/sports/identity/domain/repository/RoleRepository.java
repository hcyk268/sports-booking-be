package dev.booking.sports.identity.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.booking.sports.identity.domain.model.Role;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

	Optional<Role> findByCode(String code);

	@Query("""
			SELECT DISTINCT r FROM Role r
			LEFT JOIN FETCH r.rolePermissions rp
			LEFT JOIN FETCH rp.permission
			ORDER BY r.code ASC
			""")
	List<Role> findAllWithPermissions();

	@Query("""
			SELECT DISTINCT r FROM Role r
			LEFT JOIN FETCH r.rolePermissions rp
			LEFT JOIN FETCH rp.permission
			WHERE r.code = :code
			""")
	Optional<Role> findByCodeWithPermissions(@Param("code") String code);
}
