package com.github.shikejing.apideprecationpolicyjava.business;

import com.github.shikejing.apideprecationpolicyjava.version.VersionContext;
import com.github.shikejing.apideprecationpolicyjava.version.VersionState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sample versioned business endpoint. The resolved version is taken from the
 * request context populated by the version negotiation filter, so the response
 * always reflects the version the caller negotiated.
 */
@RestController
@RequestMapping("/api")
public class GreetingController {

    @GetMapping({"/greeting", "/{version}/greeting"})
    public Map<String, Object> greeting() {
        VersionState state = VersionContext.current();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", state.version());
        body.put("status", state.status().name());
        body.put("message", "Hello from API " + state.version());
        return body;
    }
}
