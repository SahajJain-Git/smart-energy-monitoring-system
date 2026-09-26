package com.smartenergy.monitoring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.monitoring.dto.ErrorResponse;
import com.smartenergy.monitoring.security.DeviceApiKeyAuthenticationFilter;
import com.smartenergy.monitoring.security.JwtAuthenticationEntryPoint;
import com.smartenergy.monitoring.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for Phase 7 Step 7C (ESP32 Device API-Key & User JWT Authentication).
 *
 * Configures:
 * - BCrypt password encoder for user credentials
 * - Stateless session creation policy
 * - CSRF disabled for stateless REST header-based APIs
 * - CORS integration with preflight OPTIONS permitted
 * - Public access: POST /api/v1/auth/login and OPTIONS /**
 * - Device-authenticated access: POST /api/v1/measurements requires ROLE_DEVICE
 * - User-authenticated access: GET /api/v1/devices/** and GET /api/v1/measurements/** require user roles (ADMIN, OPERATOR, VIEWER)
 * - Structured JSON 401 & 403 error responses
 * - Dual-filter security pipeline: CORS -> JwtAuthenticationFilter -> DeviceApiKeyAuthenticationFilter -> Authorization
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DeviceApiKeyAuthenticationFilter deviceApiKeyAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            DeviceApiKeyAuthenticationFilter deviceApiKeyAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.deviceApiKeyAuthenticationFilter = deviceApiKeyAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ErrorResponse err = new ErrorResponse(
                            HttpStatus.FORBIDDEN.value(),
                            HttpStatus.FORBIDDEN.getReasonPhrase(),
                            "Access is denied: insufficient permissions to access this resource.",
                            request.getRequestURI()
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(err));
                })
            )
            .authorizeHttpRequests(auth -> auth
                // 1. Preflight CORS requests are public
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 2. User login endpoint is public
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                // 3. Telemetry ingestion from ESP32 requires device authentication with ROLE_DEVICE
                .requestMatchers(HttpMethod.POST, "/api/v1/measurements").hasRole("DEVICE")
                // 4. Protected User APIs requiring human user roles (ADMIN, OPERATOR, VIEWER)
                .requestMatchers(HttpMethod.GET, "/api/v1/devices/**").hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                .requestMatchers(HttpMethod.GET, "/api/v1/measurements/**").hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                .requestMatchers(HttpMethod.GET, "/api/v1/measurements").hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                // 5. Any other request requires authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(deviceApiKeyAuthenticationFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
