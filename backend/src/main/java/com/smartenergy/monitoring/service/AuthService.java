package com.smartenergy.monitoring.service;

import com.smartenergy.monitoring.dto.AuthRequest;
import com.smartenergy.monitoring.dto.AuthResponse;
import com.smartenergy.monitoring.entity.User;
import com.smartenergy.monitoring.repository.UserRepository;
import com.smartenergy.monitoring.security.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication service handling credential verification and JWT generation for application users.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Authenticates a user with username and plaintext password against stored BCrypt hash,
     * and returns an AuthResponse containing a signed JWT token and user role.
     *
     * @param request the authentication request containing username and password
     * @return AuthResponse with username, assigned role, and JWT token
     * @throws BadCredentialsException if username does not exist or password does not match
     * @throws DisabledException if user account is marked inactive
     */
    @Transactional(readOnly = true)
    public AuthResponse authenticate(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new DisabledException("User account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());

        return new AuthResponse(
                user.getUsername(),
                user.getRole(),
                token,
                "Authentication successful"
        );
    }
}
