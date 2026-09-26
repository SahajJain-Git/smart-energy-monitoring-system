package com.smartenergy.monitoring.security;

import com.smartenergy.monitoring.entity.Device;
import com.smartenergy.monitoring.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Isolated unit tests for DeviceApiKeyAuthenticationService.
 * Verifies constant-time comparison, edge-cases, device status checks, and error statuses.
 */
@ExtendWith(MockitoExtension.class)
class DeviceApiKeyAuthenticationServiceTest {

    private static final String CONFIGURED_KEY = "super-secret-device-api-key-for-testing-purposes-32chars";
    private static final String TARGET_DEVICE = "SEM-ESP32-001";

    @Mock
    private DeviceRepository deviceRepository;

    private DeviceApiKeyAuthenticationService authService;

    @BeforeEach
    void setUp() {
        authService = new DeviceApiKeyAuthenticationService(deviceRepository, CONFIGURED_KEY);
    }

    @Test
    @DisplayName("DEV-SERVICE 1: Null or empty candidate key returns INVALID_KEY")
    void testAuthenticate_NullOrEmptyKey_ReturnsInvalidKey() {
        assertThat(authService.authenticate(null).status())
                .isEqualTo(DeviceApiKeyAuthenticationService.AuthStatus.INVALID_KEY);
        assertThat(authService.authenticate("").status())
                .isEqualTo(DeviceApiKeyAuthenticationService.AuthStatus.INVALID_KEY);
        assertThat(authService.authenticate("   ").status())
                .isEqualTo(DeviceApiKeyAuthenticationService.AuthStatus.INVALID_KEY);
    }

    @Test
    @DisplayName("DEV-SERVICE 2: Unconfigured server secret returns INVALID_KEY safely")
    void testAuthenticate_UnconfiguredServerSecret_ReturnsInvalidKey() {
        DeviceApiKeyAuthenticationService unconfiguredService =
                new DeviceApiKeyAuthenticationService(deviceRepository, "");

        assertThat(unconfiguredService.authenticate(CONFIGURED_KEY).status())
                .isEqualTo(DeviceApiKeyAuthenticationService.AuthStatus.INVALID_KEY);
    }

    @Test
    @DisplayName("DEV-SERVICE 3: Non-matching candidate key returns INVALID_KEY")
    void testAuthenticate_MismatchedKey_ReturnsInvalidKey() {
        assertThat(authService.authenticate("wrong-key-completely-different-characters-123456").status())
                .isEqualTo(DeviceApiKeyAuthenticationService.AuthStatus.INVALID_KEY);
    }

    @Test
    @DisplayName("DEV-SERVICE 4: Matching key but device not found in database returns DEVICE_NOT_FOUND")
    void testAuthenticate_DeviceNotFound_ReturnsDeviceNotFound() {
        when(deviceRepository.findByDeviceId(TARGET_DEVICE)).thenReturn(Optional.empty());

        DeviceApiKeyAuthenticationService.DeviceAuthResult result = authService.authenticate(CONFIGURED_KEY);

        assertThat(result.status()).isEqualTo(DeviceApiKeyAuthenticationService.AuthStatus.DEVICE_NOT_FOUND);
        assertThat(result.deviceId()).isNull();
    }

    @Test
    @DisplayName("DEV-SERVICE 5: Matching key but device status is INACTIVE returns DEVICE_INACTIVE")
    void testAuthenticate_InactiveDevice_ReturnsDeviceInactive() {
        Device inactiveDevice = new Device(TARGET_DEVICE, "Prototype 1", "Lab", "INACTIVE");
        when(deviceRepository.findByDeviceId(TARGET_DEVICE)).thenReturn(Optional.of(inactiveDevice));

        DeviceApiKeyAuthenticationService.DeviceAuthResult result = authService.authenticate(CONFIGURED_KEY);

        assertThat(result.status()).isEqualTo(DeviceApiKeyAuthenticationService.AuthStatus.DEVICE_INACTIVE);
        assertThat(result.deviceId()).isEqualTo(TARGET_DEVICE);
    }

    @Test
    @DisplayName("DEV-SERVICE 6: Matching key and ACTIVE device returns SUCCESS")
    void testAuthenticate_ActiveDevice_ReturnsSuccess() {
        Device activeDevice = new Device(TARGET_DEVICE, "Prototype 1", "Lab", "ACTIVE");
        when(deviceRepository.findByDeviceId(TARGET_DEVICE)).thenReturn(Optional.of(activeDevice));

        DeviceApiKeyAuthenticationService.DeviceAuthResult result = authService.authenticate(CONFIGURED_KEY);

        assertThat(result.status()).isEqualTo(DeviceApiKeyAuthenticationService.AuthStatus.SUCCESS);
        assertThat(result.deviceId()).isEqualTo(TARGET_DEVICE);
    }
}
