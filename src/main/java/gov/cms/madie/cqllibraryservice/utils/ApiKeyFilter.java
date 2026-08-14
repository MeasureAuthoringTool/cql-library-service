package gov.cms.madie.cqllibraryservice.utils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public class ApiKeyFilter extends OncePerRequestFilter {

  private final String apiKeyHeader;
  private final String apiKeyValue;
  private final Set<String> pathsToFilter;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  // Constructor now accepts a List of target paths
  public ApiKeyFilter(String apiKeyHeader, String apiKeyValue, Set<String> pathsToFilter) {
    this.apiKeyHeader = apiKeyHeader;
    this.apiKeyValue = apiKeyValue;
    this.pathsToFilter = pathsToFilter != null ? pathsToFilter : Collections.emptySet();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // In a real servlet container, getServletPath() returns the path relative to the context.
    // In MockMvc (test environment), getServletPath() may return "" while getPathInfo()
    // holds the actual path — workaround for test cases.
    String path = request.getServletPath();
    if (!StringUtils.hasLength(path)) {
      path = request.getPathInfo();
    }

    // Match current request against your list of allowed paths
    final String resolvedPath = path;
    return pathsToFilter.stream().noneMatch(pattern -> pathMatcher.match(pattern, resolvedPath));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // Okta wins: skip if already authenticated
    if (SecurityContextHolder.getContext().getAuthentication() != null
        && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
      filterChain.doFilter(request, response);
      return;
    }

    String requestKey = request.getHeader(apiKeyHeader);
    if (apiKeyValue.equals(requestKey)) {
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken("ApiKeyUser", null, Collections.emptyList());
      SecurityContextHolder.getContext().setAuthentication(auth);
    }

    filterChain.doFilter(request, response);
  }
}
