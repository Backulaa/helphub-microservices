package com.helphub.identity.auth.dto;

public record CsrfTokenResponse(
        String headerName,
        String token) {
}
