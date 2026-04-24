package com.ai.interview.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private String displayName;
    private String targetDomain;
    private String experienceLevel;
}
