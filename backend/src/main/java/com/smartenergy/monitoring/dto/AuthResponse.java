package com.smartenergy.monitoring.dto;

/**
 * Response payload for successful user authentication containing username, role, and JWT token.
 * Strictly avoids exposing passwords, password hashes, or internal database identifiers.
 */
public class AuthResponse {

    private String username;
    private String role;
    private String token;
    private String message;

    public AuthResponse() {
    }

    public AuthResponse(String username, String role, String token, String message) {
        this.username = username;
        this.role = role;
        this.token = token;
        this.message = message;
    }

    public AuthResponse(String username, String role, String message) {
        this(username, role, null, message);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
