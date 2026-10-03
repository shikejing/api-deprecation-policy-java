package com.github.shikejing.apideprecationpolicyjava.config;

import com.github.shikejing.apideprecationpolicyjava.version.VersionNegotiationFilter;
import com.github.shikejing.apideprecationpolicyjava.version.VersionRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the version negotiation filter for business endpoints ({@code /api/*}).
 */
@Configuration
public class FilterRegistrationConfig {

    @Bean
    public FilterRegistrationBean<VersionNegotiationFilter> versionNegotiationFilter(
            VersionRegistry registry, VersionProperties properties) {
        FilterRegistrationBean<VersionNegotiationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new VersionNegotiationFilter(registry, properties));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
