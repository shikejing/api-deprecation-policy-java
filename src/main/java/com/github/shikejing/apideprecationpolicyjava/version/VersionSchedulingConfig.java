package com.github.shikejing.apideprecationpolicyjava.version;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduled enforcement of planned retirement times.
 */
@Configuration
@EnableScheduling
public class VersionSchedulingConfig {
}
