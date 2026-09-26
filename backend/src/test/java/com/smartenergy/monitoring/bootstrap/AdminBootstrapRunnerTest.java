package com.smartenergy.monitoring.bootstrap;

import com.smartenergy.monitoring.entity.User;
import com.smartenergy.monitoring.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("TEST 9: Bootstrap with ADMIN_INITIAL_PASSWORD creates a BCrypt password and activates the locked bootstrap account")
    void testBootstrap_WithInitialPassword_ActivatesAccount() {
        User lockedAdmin = new User(
                "admin",
                "admin@smartenergy.local",
                AdminBootstrapRunner.LOCKED_BOOTSTRAP_HASH,
                "ADMIN",
                false
        );
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(lockedAdmin));

        String testInitialPassword = "BootstrapSecret123!";
        AdminBootstrapRunner runner = new AdminBootstrapRunner(userRepository, passwordEncoder, testInitialPassword);

        boolean activated = runner.provisionInitialAdmin();

        assertThat(activated).isTrue();
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getIsActive()).isTrue();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(AdminBootstrapRunner.LOCKED_BOOTSTRAP_HASH);
        assertThat(savedUser.getPasswordHash()).matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
        assertThat(passwordEncoder.matches(testInitialPassword, savedUser.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("TEST 10: Bootstrap without ADMIN_INITIAL_PASSWORD does not activate the locked bootstrap account")
    void testBootstrap_WithoutInitialPassword_LeavesAccountLocked() {
        User lockedAdmin = new User(
                "admin",
                "admin@smartenergy.local",
                AdminBootstrapRunner.LOCKED_BOOTSTRAP_HASH,
                "ADMIN",
                false
        );
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(lockedAdmin));

        // When password is empty or null
        AdminBootstrapRunner runner = new AdminBootstrapRunner(userRepository, passwordEncoder, "");

        boolean activated = runner.provisionInitialAdmin();

        assertThat(activated).isFalse();
        verify(userRepository, never()).save(any(User.class));
        assertThat(lockedAdmin.getIsActive()).isFalse();
        assertThat(lockedAdmin.getPasswordHash()).isEqualTo(AdminBootstrapRunner.LOCKED_BOOTSTRAP_HASH);
    }

    @Test
    @DisplayName("TEST 11: Already-active admin is not overwritten by bootstrap")
    void testBootstrap_AlreadyActiveAdmin_NotOverwritten() {
        String existingBcryptHash = passwordEncoder.encode("ExistingActivePassword999");
        User activeAdmin = new User(
                "admin",
                "admin@smartenergy.local",
                existingBcryptHash,
                "ADMIN",
                true // already active
        );
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeAdmin));

        AdminBootstrapRunner runner = new AdminBootstrapRunner(userRepository, passwordEncoder, "NewAttemptedPassword");

        boolean activated = runner.provisionInitialAdmin();

        assertThat(activated).isFalse();
        verify(userRepository, never()).save(any(User.class));
        assertThat(activeAdmin.getPasswordHash()).isEqualTo(existingBcryptHash);
    }

    @Test
    @DisplayName("Inactive admin with a custom hash is not overwritten")
    void testBootstrap_CustomHashAdmin_NotOverwritten() {
        User inactiveCustomAdmin = new User(
                "admin",
                "admin@smartenergy.local",
                "$2a$10$customModifiedHashNotTheBootstrapPlaceholder",
                "ADMIN",
                false
        );
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(inactiveCustomAdmin));

        AdminBootstrapRunner runner = new AdminBootstrapRunner(userRepository, passwordEncoder, "NewAttemptedPassword");

        boolean activated = runner.provisionInitialAdmin();

        assertThat(activated).isFalse();
        verify(userRepository, never()).save(any(User.class));
        assertThat(inactiveCustomAdmin.getPasswordHash()).isEqualTo("$2a$10$customModifiedHashNotTheBootstrapPlaceholder");
    }
}
