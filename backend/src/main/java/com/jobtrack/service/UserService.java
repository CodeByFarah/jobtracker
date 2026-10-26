package com.jobtrack.service;

import com.jobtrack.dto.user.UpdateProfileRequest;
import com.jobtrack.dto.user.UserResponse;
import com.jobtrack.entity.User;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.gmail.GmailConnectionService;
import com.jobtrack.mapper.TextUtils;
import com.jobtrack.mapper.UserMapper;
import com.jobtrack.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final GmailConnectionService gmailConnectionService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, GmailConnectionService gmailConnectionService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.gmailConnectionService = gmailConnectionService;
        this.passwordEncoder = passwordEncoder;
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

    /**
     * Deletes the account and everything in it: jobs, companies, emails read, and the Gmail
     * connection (whose access is revoked at Google first).
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("password", "That password isn't right.");
        }
        gmailConnectionService.disconnect(userId);
        userRepository.deleteApplicationsOf(userId);
        userRepository.deleteUserRow(userId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
