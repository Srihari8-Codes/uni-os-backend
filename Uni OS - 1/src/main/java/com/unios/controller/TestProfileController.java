package com.unios.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/testprofile")
public class TestProfileController {
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_FACULTY')")
    public String testProfile() {
        return "SUCCESS";
    }
}
