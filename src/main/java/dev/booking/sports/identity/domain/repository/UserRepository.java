package dev.booking.sports.identity.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.booking.sports.identity.domain.model.User;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
	
	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	@Query("""
			select distinct u from User u
			left join fetch u.userRoles ur
			left join fetch ur.role r
			left join fetch r.rolePermissions rp
			left join fetch rp.permission
			where lower(u.email) = lower(:email)
			""")
	Optional<User> findByEmailWithRoles(@Param("email") String email);

	@Query("""
			select distinct u from User u
			left join fetch u.userRoles ur
			left join fetch ur.role r
			left join fetch r.rolePermissions rp
			left join fetch rp.permission
			where u.id = :id
			""")
	Optional<User> findByIdWithRoles(@Param("id") UUID id);
}
