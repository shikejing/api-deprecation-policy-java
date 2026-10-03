package com.github.shikejing.apideprecationpolicyjava.versioning;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 版本协商：按请求维度解析期望版本。
 *
 * 优先级（确定的默认行为）：
 * 1. 显式请求头（默认 X-Api-Version，可配置）；
 * 2. 路径中的版本段（如 /api/v2/echo，可开关）；
 * 3. 配置的默认版本（api.versioning.default-version）。
 */
public class VersionNegotiator {

    private static final Pattern PATH_VERSION = Pattern.compile("(^|/)(v\\d+)(/|$)");

    private final VersioningProperties properties;

    public VersionNegotiator(VersioningProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析请求期望的版本号，永远不会返回 null（未声明时回落到默认版本）。
     */
    public String resolve(HttpServletRequest request) {
        String fromHeader = request.getHeader(properties.getHeaderName());
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader.trim();
        }
        if (properties.isPathNegotiationEnabled()) {
            Matcher matcher = PATH_VERSION.matcher(request.getRequestURI());
            if (matcher.find()) {
                return matcher.group(2);
            }
        }
        return properties.getDefaultVersion();
    }
}
