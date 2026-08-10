package gov.cms.madie.cqllibraryservice.utils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyFilterTest {

  @AfterEach
  void teardown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldNotFilterFalseWhenPathMatchesPattern() {
    // given - set up mocks
    ApiKeyFilter filter = new ApiKeyFilter("api-key", "secret", List.of("/api/**"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setServletPath("/api/libraries");

    // when - method under test
    boolean result = filter.shouldNotFilter(request);

    // then - assertions
    assertFalse(result);
  }

  @Test
  void shouldNotFilterTrueWhenPathDoesNotMatchPattern() {
    // given - set up mocks
    ApiKeyFilter filter = new ApiKeyFilter("api-key", "secret", List.of("/api/**"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setServletPath("/health");

    // when - method under test
    boolean result = filter.shouldNotFilter(request);

    // then - assertions
    assertTrue(result);
  }

  @Test
  void shouldNotFilterTrueWhenPathsToFilterIsNull() {
    // given - set up mocks
    ApiKeyFilter filter = new ApiKeyFilter("api-key", "secret", null);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setServletPath("/api/libraries");

    // when - method under test
    boolean result = filter.shouldNotFilter(request);

    // then - assertions
    assertTrue(result);
  }

  @Test
  void doFilterInternalKeepsExistingAuthenticationWhenAlreadyAuthenticated()
      throws ServletException, IOException {
    // given - set up mocks
    ApiKeyFilter filter = new ApiKeyFilter("api-key", "secret", List.of("/api/**"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);
    UsernamePasswordAuthenticationToken existingAuth =
        new UsernamePasswordAuthenticationToken("okta-user", null, List.of());
    SecurityContextHolder.getContext().setAuthentication(existingAuth);

    // when - method under test
    filter.doFilterInternal(request, response, filterChain);

    // then - assertions
    assertSame(existingAuth, SecurityContextHolder.getContext().getAuthentication());
    verify(filterChain, times(1)).doFilter(request, response);
  }

  @Test
  void doFilterInternalSetsApiKeyAuthenticationWhenHeaderMatches()
      throws ServletException, IOException {
    // given - set up mocks
    ApiKeyFilter filter = new ApiKeyFilter("api-key", "secret", List.of("/api/**"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("api-key", "secret");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);

    // when - method under test
    filter.doFilterInternal(request, response, filterChain);

    // then - assertions
    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    assertEquals(
        "ApiKeyUser", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    verify(filterChain, times(1)).doFilter(request, response);
  }

  @Test
  void doFilterInternalDoesNotSetAuthenticationWhenHeaderIsMissing()
      throws ServletException, IOException {
    // given - set up mocks
    ApiKeyFilter filter = new ApiKeyFilter("api-key", "secret", List.of("/api/**"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);

    // when - method under test
    filter.doFilterInternal(request, response, filterChain);

    // then - assertions
    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(filterChain, times(1)).doFilter(request, response);
  }

  @Test
  void doFilterInternalDoesNotSetAuthenticationWhenHeaderIsInvalid()
      throws ServletException, IOException {
    // given - set up mocks
    ApiKeyFilter filter = new ApiKeyFilter("api-key", "secret", List.of("/api/**"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("api-key", "wrong");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);

    // when - method under test
    filter.doFilterInternal(request, response, filterChain);

    // then - assertions
    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(filterChain, times(1)).doFilter(request, response);
  }
}
