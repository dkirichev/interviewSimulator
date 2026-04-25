package net.k2ai.interviewSimulator.service;

import jakarta.servlet.http.HttpServletRequest;
import net.k2ai.interviewSimulator.testutil.ReplaceCamelCase;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(ReplaceCamelCase.class)
class ClientIpResolverTest {


	@Test
	void testResolve_TrustDisabled_UsesRemoteAddr() {
		ClientIpResolver resolver = new ClientIpResolver(false);
		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getRemoteAddr()).thenReturn("10.0.0.1");
		// Even if proxy headers are present, they must be ignored when trust is off.
		when(req.getHeader("CF-Connecting-IP")).thenReturn("203.0.113.7");
		when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.99");

		assertThat(resolver.resolve(req)).isEqualTo("10.0.0.1");
	}//testResolve_TrustDisabled_UsesRemoteAddr


	@Test
	void testResolve_TrustEnabled_PrefersCfConnectingIp() {
		ClientIpResolver resolver = new ClientIpResolver(true);
		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getHeader("CF-Connecting-IP")).thenReturn("198.51.100.5");
		when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.99, 198.51.100.5");
		when(req.getRemoteAddr()).thenReturn("10.0.0.1");

		assertThat(resolver.resolve(req)).isEqualTo("198.51.100.5");
	}//testResolve_TrustEnabled_PrefersCfConnectingIp


	@Test
	void testResolve_TrustEnabled_NoCfHeader_UsesXffLeftmost() {
		ClientIpResolver resolver = new ClientIpResolver(true);
		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getHeader("CF-Connecting-IP")).thenReturn(null);
		when(req.getHeader("X-Forwarded-For")).thenReturn("198.51.100.5, 10.0.0.1");
		when(req.getRemoteAddr()).thenReturn("10.0.0.1");

		assertThat(resolver.resolve(req)).isEqualTo("198.51.100.5");
	}//testResolve_TrustEnabled_NoCfHeader_UsesXffLeftmost


	@Test
	void testResolve_TrustEnabled_NoHeaders_FallsBackToRemoteAddr() {
		ClientIpResolver resolver = new ClientIpResolver(true);
		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getHeader("CF-Connecting-IP")).thenReturn(null);
		when(req.getHeader("X-Forwarded-For")).thenReturn(null);
		when(req.getRemoteAddr()).thenReturn("10.0.0.1");

		assertThat(resolver.resolve(req)).isEqualTo("10.0.0.1");
	}//testResolve_TrustEnabled_NoHeaders_FallsBackToRemoteAddr


	@Test
	void testResolve_TrustEnabled_BlankCfHeader_FallsThrough() {
		ClientIpResolver resolver = new ClientIpResolver(true);
		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getHeader("CF-Connecting-IP")).thenReturn("   ");
		when(req.getHeader("X-Forwarded-For")).thenReturn("198.51.100.5");
		when(req.getRemoteAddr()).thenReturn("10.0.0.1");

		assertThat(resolver.resolve(req)).isEqualTo("198.51.100.5");
	}//testResolve_TrustEnabled_BlankCfHeader_FallsThrough

}//ClientIpResolverTest
