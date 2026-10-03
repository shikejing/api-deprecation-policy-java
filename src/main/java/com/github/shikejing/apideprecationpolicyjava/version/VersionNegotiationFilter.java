package com.github.shikejing.apideprecationpolicyjava.version;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.shikejing.apideprecationpolicyjava.config.VersionProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves the API version for every business request and enforces lifecycle policy.
 *
 * <p>Resolution order: path segment ({@code /api/v1/...}) &gt; request header
 * ({@code X-API-Version}, configurable) &gt; configured default version.
 *
 * <p>The resolved state snapshot is bound to {@link VersionContext} for the whole
 * request, so a state switch never produces mixed responses for in-flight requests.
 */
public class VersionNegotiationFilter extends OncePerRequestFilter {

    private static final Pattern PATH_VERSION = Pattern.compile("^v[\\w-]*$");
    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));

    private final VersionRegistry registry;
    private final VersionProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VersionNegotiationFilter(VersionRegistry registry, VersionProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String version = resolveVersion(request);

        VersionState state = registry.find(version).orElse(null);
        if (state == null) {
            writeError(response, HttpStatus.NOT_FOUND.value(), "VERSION_NOT_FOUND",
                    "Unknown API version: " + version, version);
            return;
        }

        if (properties.isEnforceRetirement()) {
            Optional<VersionState> due = registry.markRetiredIfDue(version);
            if (due.isPresent() && due.get().status() == VersionStatus.RETIRED
                    && state.status() != VersionStatus.RETIRED) {
                state = due.get();
            }
        }

        if (state.status() == VersionStatus.RETIRED) {
            writeError(response, HttpStatus.GONE.value(), "VERSION_RETIRED",
                    "API version has been retired: " + version, version);
            return;
        }

        VersionContext.set(state);
        try {
            applyResponseHeaders(request, response, state);
            filterChain.doFilter(request, response);
        } finally {
            VersionContext.clear();
        }
    }

    /**
     * Path first ({@code /api/v1/...}), then header, then configured default.
     */
    private String resolveVersion(HttpServletRequest request) {
        String path = request.getRequestURI();
        String prefix = properties.getPathPrefix();
        if (path.startsWith(prefix)) {
            String rest = path.substring(prefix.length());
            int slash = rest.indexOf('/');
            String candidate = slash > 0 ? rest.substring(0, slash) : rest;
            if (PATH_VERSION.matcher(candidate).matches() || registry.find(candidate).isPresent()) {
                return candidate;
            }
        }
        String header = request.getHeader(properties.getHeaderName());
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        return properties.getDefaultVersion();
    }

    /**
     * Emits machine-parseable lifecycle headers:
     * <ul>
     *   <li>{@code X-API-Version}: the resolved version</li>
     *   <li>{@code Deprecation}: deprecation time as {@code @<epoch-seconds>} (RFC draft)</li>
     *   <li>{@code Sunset}: planned retirement time as an HTTP-date (RFC 8594)</li>
     * </ul>
     * Deprecation headers are only emitted for callers in the gray bucket.
     */
    private void applyResponseHeaders(HttpServletRequest request, HttpServletResponse response,
                                      VersionState state) {
        response.setHeader("X-API-Version", state.version());
        if (state.status() != VersionStatus.DEPRECATED || isGraySuppressed(request, state)) {
            return;
        }
        if (state.deprecationTime() != null) {
            response.setHeader("Deprecation", "@" + state.deprecationTime().getEpochSecond());
        }
        if (state.plannedRetirementTime() != null) {
            response.setHeader("Sunset", HTTP_DATE.format(state.plannedRetirementTime()));
        }
    }

    /**
     * Deterministic gray bucketing by caller identity: the same caller always
     * lands in the same bucket during a deprecation rollout.
     */
    private boolean isGraySuppressed(HttpServletRequest request, VersionState state) {
        if (state.grayPercent() >= 100) {
            return false;
        }
        String caller = request.getHeader(properties.getCallerHeaderName());
        if (caller == null || caller.isBlank()) {
            caller = request.getRemoteAddr();
        }
        int bucket = Math.floorMod(Objects.hash(caller, state.version()), 100);
        return bucket >= state.grayPercent();
    }

    private void writeError(HttpServletResponse response, int status, String error,
                            String message, String version) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        body.put("version", version);
        body.put("timestamp", Instant.now().toString());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
