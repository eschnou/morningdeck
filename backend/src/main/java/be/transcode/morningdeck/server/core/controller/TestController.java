package be.transcode.morningdeck.server.core.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth/test")
public class TestController {

    @GetMapping
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Auth endpoint accessible");
    }

    @GetMapping("/patterns")
    public ResponseEntity<Map<String, String>> patterns(HttpServletRequest request) {
        Map<String, String> paths = new HashMap<>();
        paths.put("requestURI", request.getRequestURI());
        paths.put("contextPath", request.getContextPath());
        paths.put("servletPath", request.getServletPath());
        paths.put("pathInfo", request.getPathInfo());
        return ResponseEntity.ok(paths);
    }
}
