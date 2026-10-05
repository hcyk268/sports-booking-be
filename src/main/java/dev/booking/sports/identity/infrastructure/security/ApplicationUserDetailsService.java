package dev.booking.sports.identity.infrastructure.security;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.identity.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApplicationUserDetailsService implements UserDetailsService {

	public static final String USER_DETAILS_CACHE = "identity:user-details";

	private final UserRepository userRepository;

	@Override
	@Transactional(readOnly = true)
	@Cacheable(cacheNames = USER_DETAILS_CACHE, key = "#email.toLowerCase()")
	public ApplicationUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return userRepository.findByEmailWithRoles(email)
				.map(ApplicationUserDetails::from)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}

	@CacheEvict(cacheNames = USER_DETAILS_CACHE, key = "#email.toLowerCase()")
	public void evict(String email) {
		// Cache eviction.
	}
}
