package com.pointbank.bankplatform.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController 
public class SecurityDebugController {

     @GetMapping("/api/debug/auth")
    public Map<String, Object> inspectAuthentication() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return Map.of(
                "authenticated", authentication.isAuthenticated(),
                "principal", authentication.getName(),
                "authorities", authentication.getAuthorities().toString()
        );
    }
}
