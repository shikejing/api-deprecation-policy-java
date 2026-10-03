package com.github.shikejing.apideprecationpolicyjava.version;

/**
 * Thrown when a requested version is not registered.
 */
public class VersionNotFoundException extends RuntimeException {
    private final String version;

    public VersionNotFoundException(String version) {
        super("Unknown API version: " + version);
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
