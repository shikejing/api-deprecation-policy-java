package com.github.shikejing.apideprecationpolicyjava.versioning;

import com.github.shikejing.apideprecationpolicyjava.audit.AuditLog;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 版本治理组件装配。
 */
@Configuration
@EnableConfigurationProperties(VersioningProperties.class)
public class VersioningConfig {

    @Bean
    public VersionRegistry versionRegistry() {
        return new VersionRegistry();
    }

    @Bean
    public VersionNegotiator versionNegotiator(VersioningProperties properties) {
        return new VersionNegotiator(properties);
    }

    @Bean
    public AuditLog auditLog() {
        return new AuditLog();
    }

    @Bean
    public FilterRegistrationBean<VersionGateFilter> versionGateFilter(
            VersionRegistry registry, VersionNegotiator negotiator) {
        FilterRegistrationBean<VersionGateFilter> bean = new FilterRegistrationBean<>(
                new VersionGateFilter(registry, negotiator));
        bean.addUrlPatterns("/*");
        bean.setOrder(1);
        return bean;
    }
}
