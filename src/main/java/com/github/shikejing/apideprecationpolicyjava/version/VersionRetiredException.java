package com.github.shikejing.apideprecationpolicyjava.version;

/**
 * Thrown when a requested version has been retired (or is past its planned retirement time).
 */
public class VersionRetiredException extends RuntimeException {
    private final String version;

    public VersionRetiredException(String version) {
        super("API version has been retired: " + version);
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
