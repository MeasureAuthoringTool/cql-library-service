package gov.cms.madie.cqllibraryservice.config.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ApiTokenSecurityFilter implements Filter {

  private final String madieReadApiKey;
  private final List<AntPathRequestMatcher> matchers;

  public ApiTokenSecurityFilter(String madieReadApiKey, String... patterns) {
    this.madieReadApiKey = madieReadApiKey;
    matchers = new ArrayList<>();
    for (String pattern : patterns) {
      matchers.add(new AntPathRequestMatcher(pattern));
    }
  }

  @Override
  public void doFilter(
      ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
      throws IOException, ServletException {
    log.info("doFilter on path: {}", ((HttpServletRequest) servletRequest).getRequestURI());
    final String requestApiKey = ((HttpServletRequest) servletRequest).getHeader("api-key");
    log.info("comparing api-key [{}] to madie api key [{}]", requestApiKey, madieReadApiKey);

    if (shouldSecure((HttpServletRequest) servletRequest)
        && madieReadApiKey.equals(requestApiKey)) {
      PreAuthenticatedAuthenticationToken token =
          new PreAuthenticatedAuthenticationToken(
              "madie-read-api-key", null, AuthorityUtils.NO_AUTHORITIES);
      SecurityContextHolder.getContext().setAuthentication(token);
    }
    filterChain.doFilter(servletRequest, servletResponse);
  }

  private boolean shouldSecure(final HttpServletRequest request) {
    return matchers.stream().anyMatch(matcher -> matcher.matches(request));
  }
}
