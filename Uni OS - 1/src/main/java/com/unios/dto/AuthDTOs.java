package com.unios.dto;

import com.unios.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class AuthDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignupRequest {
        private String universityName;
        private String email;
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicantSignupRequest {
        private String email;
        private String password;
        private Long universityId; // links them to the batch's university
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String token;
        private UserDTO user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDTO {
        private Long id;
        private String email;
        private Role role;
        private Long universityId;
    }
}
