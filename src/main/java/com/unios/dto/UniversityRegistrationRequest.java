package com.unios.dto;

import lombok.Data;

@Data
public class UniversityRegistrationRequest {
    // University Info
    private String universityName;
    private String subdomain;
    private String description;
    
    // Admin User Info
    private String adminEmail;
    private String adminPassword;
}
