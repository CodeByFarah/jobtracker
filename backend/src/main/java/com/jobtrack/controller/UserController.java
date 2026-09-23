package com.jobtrack.controller;

import com.jobtrack.dto.user.UpdateProfileRequest;
import com.jobtrack.dto.user.UserResponse;
import com.jobtrack.security.CurrentUser;
import com.jobtrack.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Profile", description = "The authenticated user's own profile")
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class UserController {

    private final UserService userService;
    private final CurrentUser currentUser;

    public UserController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Get my profile")
    public UserResponse getProfile() {
        return userService.getProfile(currentUser.id());
    }

    @PutMapping
    @Operation(summary = "Update my profile", description = "Updates name, headline and location. Email cannot be changed.")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public UserResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(currentUser.id(), request);
    }
}
