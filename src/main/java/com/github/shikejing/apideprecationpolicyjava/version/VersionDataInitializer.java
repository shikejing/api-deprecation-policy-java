package com.github.shikejing.apideprecationpolicyjava.version;

import com.github.shikejing.apideprecationpolicyjava.config.VersionProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the initially published API versions on startup: the default version
 * plus the two currently published versions v1 and v2.
 */
@Component
public class VersionDataInitializer implements ApplicationRunner {

    private final VersionRegistry registry;
    private final VersionProperties properties;

    public VersionDataInitializer(VersionRegistry registry, VersionProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        registerIfAbsent("v1");
        registerIfAbsent("v2");
        String defaultVersion = properties.getDefaultVersion();
        if (defaultVersion != null && !defaultVersion.isBlank()) {
            registerIfAbsent(defaultVersion.trim());
        }
    }

    private void registerIfAbsent(String version) {
        try {
            registry.register(version);
        } catch (InvalidVersionTransitionException alreadyRegistered) {
            // idempotent startup: version already present
        }
    }
}
