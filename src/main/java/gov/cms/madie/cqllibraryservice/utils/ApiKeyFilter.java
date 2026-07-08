package gov.cms.madie.cqllibraryservice.utils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ApiKeyFilter extends OncePerRequestFilter {

  private final String apiKeyHeader;
  private final String apiKeyValue;
  private final List<String> pathsToFilter;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  // Constructor now accepts a List of target paths
  public ApiKeyFilter(String apiKeyHeader, String apiKeyValue, List<String> pathsToFilter) {
    this.apiKeyHeader = apiKeyHeader;
    this.apiKeyValue = apiKeyValue;
    this.pathsToFilter = pathsToFilter != null ? pathsToFilter : Collections.emptyList();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();

    // Match current request against your list of allowed paths
    return pathsToFilter.stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
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
