package gov.cms.madie.cqllibraryservice.config.security;

import gov.cms.madie.cqllibraryservice.utils.ApiKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] AUTH_WHITELIST = {"/actuator/**"};

  private final String apiKeyHeader;
  private final String apiKeyValue;

  public SecurityConfig(
      @Value("${madie.security.api-key-header}") String apiKeyHeader,
      @Value("${madie.security.system-api-key}") String apiKeyValue) {
    this.apiKeyHeader = apiKeyHeader;
    this.apiKeyValue = apiKeyValue;
  }

  @Bean
  protected SecurityFilterChain filterChain(HttpSecurity http, UserRoleConverter roleConverter)
      throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(Customizer.withDefaults())
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(AUTH_WHITELIST)
                    .permitAll()
                    .requestMatchers("/cql-libraries/admin/**")
                    .hasRole("MADIE-ADMIN")
                    .anyRequest()
                    .authenticated())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2ResourceServer(
            oAuth2ResourceServerConfigurer ->
                oAuth2ResourceServerConfigurer.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(roleConverter)))
        // It must run after OAuth2 processing handles the Okta token
        .addFilterAfter(
            new ApiKeyFilter(apiKeyHeader, apiKeyValue, List.of("/cql-libraries/cql")),
            org.springframework.security.oauth2.server.resource.web.authentication
                .BearerTokenAuthenticationFilter.class)
        .headers(
            headers ->
                headers
                    .xssProtection(Customizer.withDefaults())
                    .contentSecurityPolicy(csp -> csp.policyDirectives("script-src 'self'")));
    return http.build();
  }
}
