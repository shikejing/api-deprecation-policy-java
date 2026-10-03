package com.github.shikejing.apideprecationpolicyjava.versioning;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * 版本门禁过滤器：
 * <ul>
 *   <li>按请求协商版本并锁定快照（请求内状态一致）；</li>
 *   <li>版本不存在 → 404，错误码 VERSION_UNKNOWN；</li>
 *   <li>版本已下线或超过计划下线时间 → 410，错误码 VERSION_SUNSET（与不存在明确区分）；</li>
 *   <li>版本已弃用（且命中灰度）→ 写入机器可解析的 Deprecation / Sunset 响应头。</li>
 * </ul>
 */
public class VersionGateFilter extends OncePerRequestFilter {

    /** RFC 8594：计划下线时间（HTTP-date） */
    public static final String SUNSET_HEADER = "Sunset";
    /** RFC 9745：弃用时间（unix 时间戳，@ 前缀） */
    public static final String DEPRECATION_HEADER = "Deprecation";
    /** 当前请求实际命中的版本 */
    public static final String API_VERSION_HEADER = "X-Api-Version-Served";

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                    .withZone(ZoneOffset.UTC);

    private final VersionRegistry registry;
    private final VersionNegotiator negotiator;

    public VersionGateFilter(VersionRegistry registry, VersionNegotiator negotiator) {
        this.registry = registry;
        this.negotiator = negotiator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 管理端点不参与业务版本门禁
        return request.getRequestURI().startsWith("/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String version = negotiator.resolve(request);
        Optional<VersionInfo> found = registry.find(version);
        if (found.isEmpty()) {
            reject(response, HttpStatus.NOT_FOUND, "VERSION_UNKNOWN",
                    "API version '" + version + "' does not exist");
            return;
        }

        // 锁定本次请求的状态快照：进行中的请求不受并发切换影响
        VersionInfo info = found.get();
        VersionContext.set(info);
        try {
            boolean sunset = info.getState() == VersionState.SUNSET
                    || (info.getSunsetAt() != null && !Instant.now().isBefore(info.getSunsetAt()));
            if (sunset) {
                reject(response, HttpStatus.GONE, "VERSION_SUNSET",
                        "API version '" + version + "' has been sunset"
                                + (info.getSunsetAt() != null ? " since " + info.getSunsetAt() : ""));
                return;
            }

            response.setHeader(API_VERSION_HEADER, version);
            if (info.getState() == VersionState.DEPRECATED && inRollout(request, info)) {
                // 机器可解析的弃用时间与计划下线时间
                if (info.getDeprecatedAt() != null) {
                    response.setHeader(DEPRECATION_HEADER,
                            "@" + info.getDeprecatedAt().getEpochSecond());
                }
                if (info.getSunsetAt() != null) {
                    response.setHeader(SUNSET_HEADER, HTTP_DATE.format(info.getSunsetAt()));
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            VersionContext.clear();
        }
    }

    /**
     * 灰度判定：按调用方身份哈希取模，保证同一调用方在过渡窗口内
     * 观察到的弃用语义稳定（不会同一调用方一会儿弃用一会儿可用）。
     */
    private boolean inRollout(HttpServletRequest request, VersionInfo info) {
        if (info.getRolloutPercent() >= 100) {
            return true;
        }
        if (info.getRolloutPercent() <= 0) {
            return false;
        }
        String caller = request.getHeader("X-Caller-Id");
        if (caller == null || caller.isBlank()) {
            caller = request.getRemoteAddr();
        }
        int bucket = Math.floorMod(caller.hashCode(), 100);
        return bucket < info.getRolloutPercent();
    }

    private void reject(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\""
                + message.replace("\"", "\\\"") + "\"}");
    }
}
