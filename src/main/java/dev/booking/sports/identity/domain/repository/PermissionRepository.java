package dev.booking.sports.identity.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.booking.sports.identity.domain.model.Permission;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

	@Query("""
			SELECT p FROM Permission p
			WHERE (:module IS NULL OR p.module = :module)
			ORDER BY p.module ASC, p.code ASC
			""")
	List<Permission> findAllOrdered(@Param("module") String module);

	Set<Permission> findByCodeIn(Collection<String> codes);
}
