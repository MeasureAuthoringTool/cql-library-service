package gov.cms.madie.cqllibraryservice.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] AUTH_WHITELIST = {"/actuator/**", "/cql-libraries/*"};

  @Bean
  protected SecurityFilterChain filterChain(HttpSecurity http, UserRoleConverter roleConverter)
      throws Exception {
    http.cors()
        .and()
        .csrf()
        .and()
        .authorizeHttpRequests()
        .requestMatchers(AUTH_WHITELIST)
        .permitAll()
        .requestMatchers("/cql-libraries/cql")
        .permitAll()
        .requestMatchers("/cql-libraries/admin/**")
        .hasRole("MADIE-ADMIN")
        .anyRequest()
        .authenticated()
        .and()
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
        .oauth2ResourceServer(
            oAuth2ResourceServerConfigurer ->
                oAuth2ResourceServerConfigurer.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(roleConverter)))
        .headers()
        .xssProtection()
        .and()
        .contentSecurityPolicy("script-src 'self'");
    return http.build();
  }
}
