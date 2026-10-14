package com.helphub.identity.user.dto;

import java.util.List;

public record UserPageResponse(
        List<UserListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort) {
}