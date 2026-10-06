package dev.booking.sports.identity.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.identity.domain.model.Role;
import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;

@IntegrationTest
@Transactional
class UserRepositoryIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Test
	void existsByEmailIgnoreCase_isCaseInsensitive() {
		Role customer = roleRepository.findByCode("CUSTOMER").orElseThrow();
		User user = User.register("unique-it@example.com", "encoded-hash", "Integration User", null);
		user.assignRole(customer);
		userRepository.saveAndFlush(user);

		assertThat(userRepository.existsByEmailIgnoreCase("UNIQUE-IT@example.com")).isTrue();
		assertThat(userRepository.existsByEmailIgnoreCase("not-found@example.com")).isFalse();
	}

	@Test
	void findByIdWithRoles_loadsCustomerRole() {
		Role customer = roleRepository.findByCode("CUSTOMER").orElseThrow();
		User user = User.register("roles-it@example.com", "encoded-hash", "Role Fetch", null);
		user.assignRole(customer);
		user = userRepository.saveAndFlush(user);

		User loaded = userRepository.findByIdWithRoles(user.getId()).orElseThrow();

		assertThat(loaded.roleCodes()).containsExactly("CUSTOMER");
	}
}
