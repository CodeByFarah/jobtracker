package com.jobtrack.security;

import com.jobtrack.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the id of the authenticated user from the verified JWT. Controllers pass this id to
 * services; ids supplied in request bodies or query strings are never used for ownership.
 */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        Long id = Long.valueOf(jwt.getSubject());
        // A token can outlive its account (e.g. after a database reset). Answer 401 so the client
        // logs out, instead of failing later with a foreign-key error.
        if (!userRepository.existsById(id)) {
            throw new AuthenticationCredentialsNotFoundException("The account for this token no longer exists");
        }
        return id;
    }
}
