package gov.cms.madie.cqllibraryservice.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] AUTH_WHITELIST = {"/actuator/**"};

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
        .headers(
            headers ->
                headers
                    .xssProtection(Customizer.withDefaults())
                    .contentSecurityPolicy(csp -> csp.policyDirectives("script-src 'self'")));
    return http.build();
  }
}
