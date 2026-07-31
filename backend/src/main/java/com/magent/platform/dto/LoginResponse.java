package com.magent.platform.dto;

public record LoginResponse(
        String token,
        String adminId,
        String username,
        String role
) {
}