package com.smartenergy.monitoring.service;

import com.smartenergy.monitoring.dto.AuthRequest;
import com.smartenergy.monitoring.dto.AuthResponse;
import com.smartenergy.monitoring.entity.User;
import com.smartenergy.monitoring.repository.UserRepository;
import com.smartenergy.monitoring.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider("test-secret-key-that-is-at-least-32-bytes-long-for-testing", 3600000);

    private AuthService authService;
    private CustomUserDetailsService userDetailsService;

    private User activeUser;
    private final String rawPassword = "TestSecretPassword123!";
    private String bcryptHash;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider);
        userDetailsService = new CustomUserDetailsService(userRepository);
        bcryptHash = passwordEncoder.encode(rawPassword);

        activeUser = new User("operator1", "operator1@smartenergy.local", bcryptHash, "OPERATOR", true);
        activeUser.setId(10L);
    }

    @Test
    @DisplayName("TEST 1: Valid username and valid password -> authentication succeeds")
    void testAuthenticate_Success() {
        when(userRepository.findByUsername("operator1")).thenReturn(Optional.of(activeUser));

        AuthRequest request = new AuthRequest("operator1", rawPassword);
        AuthResponse response = authService.authenticate(request);

        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("operator1");
        assertThat(response.getRole()).isEqualTo("OPERATOR");
        assertThat(response.getToken()).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(response.getToken())).isTrue();
        assertThat(response.getMessage()).isEqualTo("Authentication successful");
    }

    @Test
    @DisplayName("TEST 2: Valid username and wrong password -> BadCredentialsException thrown")
    void testAuthenticate_WrongPassword() {
        when(userRepository.findByUsername("operator1")).thenReturn(Optional.of(activeUser));

        AuthRequest request = new AuthRequest("operator1", "IncorrectPassword!");

        assertThatThrownBy(() -> authService.authenticate(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    @DisplayName("TEST 3: Unknown username -> BadCredentialsException thrown")
    void testAuthenticate_UnknownUser() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        AuthRequest request = new AuthRequest("nonexistent", rawPassword);

        assertThatThrownBy(() -> authService.authenticate(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    @DisplayName("TEST 4: Inactive user -> DisabledException thrown")
    void testAuthenticate_InactiveUser() {
        User inactiveUser = new User("disabled_user", "disabled@smartenergy.local", bcryptHash, "VIEWER", false);
        when(userRepository.findByUsername("disabled_user")).thenReturn(Optional.of(inactiveUser));

        AuthRequest request = new AuthRequest("disabled_user", rawPassword);

        assertThatThrownBy(() -> authService.authenticate(request))
                .isInstanceOf(DisabledException.class)
                .hasMessage("User account is inactive");
    }

    @Test
    @DisplayName("TEST 7 & 8: AuthResponse does NOT expose password or passwordHash")
    void testAuthenticate_DoesNotExposeSensitiveInformation() {
        when(userRepository.findByUsername("operator1")).thenReturn(Optional.of(activeUser));

        AuthRequest request = new AuthRequest("operator1", rawPassword);
        AuthResponse response = authService.authenticate(request);

        assertThat(response.getUsername()).isEqualTo("operator1");
        assertThat(response.getRole()).isEqualTo("OPERATOR");

        // AuthResponse class strictly contains username, role, and message
        assertThat(response.getClass().getDeclaredFields())
                .extracting("name")
                .doesNotContain("password", "passwordHash", "credentials", "secret");
    }

    @Test
    @DisplayName("STEP 13: PasswordEncoder creates valid BCrypt hash ($2a$, $2b$, or $2y$) and matches raw password")
    void testPasswordEncoder_BCryptCharacteristics() {
        String testPassword = "AdminSecurePassword456$";
        String encoded = passwordEncoder.encode(testPassword);

        assertThat(encoded).isNotEqualTo(testPassword);
        assertThat(encoded).matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
        assertThat(passwordEncoder.matches(testPassword, encoded)).isTrue();
        assertThat(passwordEncoder.matches("WrongPassword", encoded)).isFalse();
    }

    @Test
    @DisplayName("CustomUserDetailsService loads UserDetails with authorities and disabled state")
    void testCustomUserDetailsService_LoadByUsername() {
        when(userRepository.findByUsername("operator1")).thenReturn(Optional.of(activeUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("operator1");

        assertThat(userDetails.getUsername()).isEqualTo("operator1");
        assertThat(userDetails.getPassword()).isEqualTo(bcryptHash);
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_OPERATOR");
    }

    @Test
    @DisplayName("CustomUserDetailsService throws UsernameNotFoundException when user absent")
    void testCustomUserDetailsService_UserNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }
}
