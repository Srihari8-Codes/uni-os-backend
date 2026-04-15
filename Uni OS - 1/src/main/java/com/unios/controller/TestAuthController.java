package com.unios.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/testauth")
public class TestAuthController {
    @GetMapping
    public String testAuth(Authentication authentication) {
        if(authentication == null) return "No auth";
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));
    }
}
