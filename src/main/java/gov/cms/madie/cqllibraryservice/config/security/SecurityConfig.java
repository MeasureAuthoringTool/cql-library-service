package gov.cms.madie.cqllibraryservice.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;

@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {

  @Value("${read-api-key}")
  private String madieReadApiKey;

  protected void configure(HttpSecurity http) throws Exception {
    http.cors()
        .and()
        .authorizeRequests()
        .antMatchers("/actuator/**")
        .permitAll()
        .and()
        .authorizeRequests()
        .anyRequest()
        .authenticated()
        .and()
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
        .addFilterBefore(
            new ApiTokenSecurityFilter(
                madieReadApiKey, "/cql-libraries/versioned", "/cql-libraries/cql"),
            BearerTokenAuthenticationFilter.class)
        .oauth2ResourceServer()
        .jwt()
        .and()
        .and()
        .headers()
        .xssProtection()
        .and()
        .contentSecurityPolicy("script-src 'self'");
  }
}
