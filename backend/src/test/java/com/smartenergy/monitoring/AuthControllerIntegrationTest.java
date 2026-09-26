package com.smartenergy.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.monitoring.dto.AuthRequest;
import com.smartenergy.monitoring.entity.User;
import com.smartenergy.monitoring.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the User Authentication REST endpoint (/api/v1/auth/login).
 * Verifies credentials verification, error formatting, input validation, and data sanitization.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String TEST_USERNAME = "test_operator";
    private static final String TEST_PASSWORD = "OperatorSecure123!";
    private static final String INACTIVE_USERNAME = "test_inactive";

    @BeforeEach
    void setUp() {
        cleanTestUsers();

        // Create active test user
        User activeUser = new User(
                TEST_USERNAME,
                "test_operator@smartenergy.local",
                passwordEncoder.encode(TEST_PASSWORD),
                "OPERATOR",
                true
        );
        userRepository.save(activeUser);

        // Create inactive test user
        User inactiveUser = new User(
                INACTIVE_USERNAME,
                "test_inactive@smartenergy.local",
                passwordEncoder.encode(TEST_PASSWORD),
                "VIEWER",
                false
        );
        userRepository.save(inactiveUser);
    }

    @AfterEach
    void tearDown() {
        cleanTestUsers();
    }

    private void cleanTestUsers() {
        userRepository.findByUsername(TEST_USERNAME).ifPresent(userRepository::delete);
        userRepository.findByUsername(INACTIVE_USERNAME).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("AUTH TEST 1: Valid username and valid password -> HTTP 200 OK")
    void testLogin_Success() throws Exception {
        AuthRequest request = new AuthRequest(TEST_USERNAME, TEST_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is(TEST_USERNAME)))
                .andExpect(jsonPath("$.role", is("OPERATOR")))
                .andExpect(jsonPath("$.message", containsString("successful")))
                // TEST 7 & 8: Verify password and passwordHash are NOT exposed
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())));
    }

    @Test
    @DisplayName("AUTH TEST 2: Valid username and wrong password -> HTTP 401 Unauthorized")
    void testLogin_WrongPassword() throws Exception {
        AuthRequest request = new AuthRequest(TEST_USERNAME, "WrongSecretPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid username or password")));
    }

    @Test
    @DisplayName("AUTH TEST 3: Unknown username -> HTTP 401 Unauthorized")
    void testLogin_UnknownUser() throws Exception {
        AuthRequest request = new AuthRequest("completely_unknown_user", TEST_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid username or password")));
    }

    @Test
    @DisplayName("AUTH TEST 4: Inactive user -> HTTP 401 Unauthorized")
    void testLogin_InactiveUser() throws Exception {
        AuthRequest request = new AuthRequest(INACTIVE_USERNAME, TEST_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("User account is inactive")));
    }

    @Test
    @DisplayName("AUTH TEST 5: Blank username -> HTTP 400 Bad Request")
    void testLogin_BlankUsername() throws Exception {
        AuthRequest request = new AuthRequest("", TEST_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.username", is("Username cannot be blank")));
    }

    @Test
    @DisplayName("AUTH TEST 6: Blank password -> HTTP 400 Bad Request")
    void testLogin_BlankPassword() throws Exception {
        AuthRequest request = new AuthRequest(TEST_USERNAME, "   ");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.password", is("Password cannot be blank")));
    }

    @Test
    @DisplayName("AUTH TEST: Malformed JSON body -> HTTP 400 Bad Request")
    void testLogin_MalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"test\", \"password\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }
}
