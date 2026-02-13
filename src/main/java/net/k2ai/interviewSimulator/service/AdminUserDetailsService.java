package net.k2ai.interviewSimulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.entity.AdminUser;
import net.k2ai.interviewSimulator.repository.AdminUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class AdminUserDetailsService implements UserDetailsService {

	private final AdminUserRepository adminUserRepository;


	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		AdminUser admin = adminUserRepository.findByUsername(username)
				.orElseThrow(() -> {
					log.warn("Admin login attempt with unknown username: {}", username);
					return new UsernameNotFoundException("User not found: " + username);
				});

		log.info("Admin user '{}' authenticated", username);

		return new User(
				admin.getUsername(),
				admin.getPasswordHash(),
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
		);
	}//loadUserByUsername

}//AdminUserDetailsService
