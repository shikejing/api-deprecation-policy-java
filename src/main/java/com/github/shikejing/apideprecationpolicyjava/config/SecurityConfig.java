package com.github.shikejing.apideprecationpolicyjava.config;

import com.github.shikejing.apideprecationpolicyjava.admin.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration: management endpoints ({@code /admin/**}) require the
 * ADMIN role; business endpoints are open to any caller. Both unauthenticated
 * (401) and unauthorized (403) attempts are audited for traceability.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuditService auditService,
                                                   VersionProperties properties) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").hasRole(properties.getAdminRole())
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            auditService.record("anonymous", "AUTHENTICATE", null, false,
                                    "unauthenticated request to " + request.getMethod() + " "
                                            + request.getRequestURI());
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                                    "Authentication required");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            String actor = request.getUserPrincipal() != null
                                    ? request.getUserPrincipal().getName() : "anonymous";
                            auditService.record(actor, "AUTHORIZE", null, false,
                                    "denied access to " + request.getMethod() + " "
                                            + request.getRequestURI());
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                        }));
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(VersionProperties properties) {
        UserDetails admin = User.withUsername(properties.getAdminUsername())
                .password("{noop}" + properties.getAdminPassword())
                .roles(properties.getAdminRole())
                .build();
        UserDetails operator = User.withUsername(properties.getOperatorUsername())
                .password("{noop}" + properties.getOperatorPassword())
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(admin, operator);
    }
}
