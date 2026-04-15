package com.unios.dto;

import lombok.Data;

@Data
public class FacultyCreationRequest {
    private String email;
    private String password;
    private String name;
}
