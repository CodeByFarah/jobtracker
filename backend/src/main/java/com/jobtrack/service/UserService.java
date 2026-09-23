package com.jobtrack.service;

import com.jobtrack.dto.user.UpdateProfileRequest;
import com.jobtrack.dto.user.UserResponse;
import com.jobtrack.entity.User;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.mapper.TextUtils;
import com.jobtrack.mapper.UserMapper;
import com.jobtrack.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return UserMapper.toResponse(findUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.setFullName(TextUtils.clean(request.fullName()));
        user.setHeadline(TextUtils.clean(request.headline()));
        user.setLocation(TextUtils.clean(request.location()));
        // flush so the response carries the new updated_at timestamp
        return UserMapper.toResponse(userRepository.saveAndFlush(user));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
