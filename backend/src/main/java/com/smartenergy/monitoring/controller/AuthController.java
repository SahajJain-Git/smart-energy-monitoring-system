package com.smartenergy.monitoring.controller;

import com.smartenergy.monitoring.dto.AuthRequest;
import com.smartenergy.monitoring.dto.AuthResponse;
import com.smartenergy.monitoring.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for user authentication operations.
 * Base Path: /api/v1/auth
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates a user using username and password.
     * Note: In Step 7B-1, this endpoint verifies credentials without issuing a JWT.
     *
     * @param request the validated authentication request
     * @return HTTP 200 with AuthResponse on success, or HTTP 401 on authentication failure
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.authenticate(request);
        return ResponseEntity.ok(response);
    }
}
