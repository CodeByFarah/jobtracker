package com.jobtrack.mapper;

import com.jobtrack.dto.user.UserResponse;
import com.jobtrack.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getHeadline(),
                user.getLocation(), user.getCreatedAt());
    }
}
