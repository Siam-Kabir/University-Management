package com.university.management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @Size(min = 8) String password) { }
