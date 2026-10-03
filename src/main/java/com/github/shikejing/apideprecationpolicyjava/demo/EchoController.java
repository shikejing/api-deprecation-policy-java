package com.github.shikejing.apideprecationpolicyjava.demo;

import com.github.shikejing.apideprecationpolicyjava.versioning.VersionContext;
import com.github.shikejing.apideprecationpolicyjava.versioning.VersionInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示用业务接口：按协商出的版本返回不同响应。
 * 通过 {@link VersionContext} 读取本次请求锁定的版本快照，
 * 保证响应与门禁判定使用的是同一份状态。
 */
@RestController
@RequestMapping("/api")
public class EchoController {

    @GetMapping({"/echo", "/v1/echo", "/v2/echo"})
    public Map<String, String> echo() {
        VersionInfo info = VersionContext.current();
        String version = info != null ? info.getVersion() : "unknown";
        return Map.of(
                "version", version,
                "message", "v2".equals(version) ? "hello from v2" : "hello from v1");
    }
}
