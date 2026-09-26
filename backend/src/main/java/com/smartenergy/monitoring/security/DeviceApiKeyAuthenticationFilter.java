package com.smartenergy.monitoring.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.monitoring.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Filter that intercepts incoming HTTP requests to enforce device API-key authentication
 * specifically for edge telemetry ingestion (POST /api/v1/measurements).
 *
 * Operational Rules:
 * 1. Requests outside POST /api/v1/measurements bypass device authentication completely.
 * 2. POST /api/v1/measurements requires the X-API-Key header.
 * 3. Human user JWT tokens cannot satisfy telemetry ingestion.
 * 4. Missing or invalid keys produce HTTP 401 Unauthorized.
 * 5. Valid keys for inactive or unrecognized devices produce HTTP 403 Forbidden.
 * 6. Valid keys for active devices establish an authenticated DeviceApiKeyAuthenticationToken.
 */
@Component
public class DeviceApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final DeviceApiKeyAuthenticationService authService;
    private final ObjectMapper objectMapper;

    public DeviceApiKeyAuthenticationFilter(
            DeviceApiKeyAuthenticationService authService,
            ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // 1. If not POST /api/v1/measurements, bypass device authentication
        if (!isTelemetryIngestionEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Clear any user authentication context so JWT tokens cannot satisfy device telemetry ingestion
        SecurityContextHolder.clearContext();

        // 3. Inspect X-API-Key header
        String apiKey = request.getHeader("X-API-Key");
        if (!StringUtils.hasText(apiKey)) {
            sendErrorResponse(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Device authentication required. Missing or empty 'X-API-Key' header.",
                    request.getRequestURI()
            );
            return;
        }

        // 4. Authenticate candidate API key
        DeviceApiKeyAuthenticationService.DeviceAuthResult result = authService.authenticate(apiKey);

        switch (result.status()) {
            case SUCCESS -> {
                DeviceApiKeyAuthenticationToken authenticationToken = new DeviceApiKeyAuthenticationToken(
                        result.deviceId(),
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_DEVICE"))
                );
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                filterChain.doFilter(request, response);
            }
            case DEVICE_INACTIVE -> {
                sendErrorResponse(
                        response,
                        HttpStatus.FORBIDDEN,
                        "Device is inactive and not authorized to submit telemetry.",
                        request.getRequestURI()
                );
            }
            case DEVICE_NOT_FOUND -> {
                sendErrorResponse(
                        response,
                        HttpStatus.FORBIDDEN,
                        "Device identity is not authorized to submit telemetry.",
                        request.getRequestURI()
                );
            }
            case INVALID_KEY -> {
                sendErrorResponse(
                        response,
                        HttpStatus.UNAUTHORIZED,
                        "Invalid device API key.",
                        request.getRequestURI()
                );
            }
        }
    }

    /**
     * Determines whether the given HTTP request targets the telemetry ingestion endpoint.
     */
    private boolean isTelemetryIngestionEndpoint(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/v1/measurements".equals(path) || "/api/v1/measurements/".equals(path);
    }

    /**
     * Sends uniform structured JSON error response matching ErrorResponse schema.
     */
    private void sendErrorResponse(
            HttpServletResponse response,
            HttpStatus status,
            String message,
            String path) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                path
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
