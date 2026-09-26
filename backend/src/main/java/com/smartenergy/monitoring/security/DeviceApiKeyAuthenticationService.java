package com.smartenergy.monitoring.security;

import com.smartenergy.monitoring.entity.Device;
import com.smartenergy.monitoring.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Service responsible for validating device API keys and identifying IoT edge devices.
 *
 * Current Implementation:
 * Resolves SEM-ESP32-001 from external environment configuration (DEVICE_API_KEY_SEM_ESP32_001).
 * Validates with constant-time equality check to prevent timing analysis attacks.
 * Verifies that the resolved device exists and is in ACTIVE status.
 *
 * Future-Ready Architecture:
 * Can be transitioned to database-backed hashed API keys (e.g. BCrypt/Argon2)
 * without modifying controllers, services, or filter interfaces.
 */
@Service
public class DeviceApiKeyAuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(DeviceApiKeyAuthenticationService.class);

    public static final String TARGET_DEVICE_ID = "SEM-ESP32-001";

    private final DeviceRepository deviceRepository;
    private final String configuredKeySemEsp32_001;

    public DeviceApiKeyAuthenticationService(
            DeviceRepository deviceRepository,
            @Value("${app.device.api-key.sem-esp32-001:}") String configuredKeySemEsp32_001) {
        this.deviceRepository = deviceRepository;
        this.configuredKeySemEsp32_001 = (configuredKeySemEsp32_001 != null) ? configuredKeySemEsp32_001.trim() : "";
    }

    public enum AuthStatus {
        SUCCESS,
        INVALID_KEY,
        DEVICE_INACTIVE,
        DEVICE_NOT_FOUND
    }

    public record DeviceAuthResult(AuthStatus status, String deviceId) {
        public static DeviceAuthResult success(String deviceId) {
            return new DeviceAuthResult(AuthStatus.SUCCESS, deviceId);
        }

        public static DeviceAuthResult invalidKey() {
            return new DeviceAuthResult(AuthStatus.INVALID_KEY, null);
        }

        public static DeviceAuthResult deviceInactive(String deviceId) {
            return new DeviceAuthResult(AuthStatus.DEVICE_INACTIVE, deviceId);
        }

        public static DeviceAuthResult deviceNotFound() {
            return new DeviceAuthResult(AuthStatus.DEVICE_NOT_FOUND, null);
        }
    }

    /**
     * Authenticates a candidate API key and resolves the corresponding active Device identity.
     * Uses constant-time comparison to prevent side-channel timing attacks.
     *
     * @param providedKey the candidate API key from the X-API-Key HTTP header
     * @return DeviceAuthResult detailing status and resolved device identifier
     */
    public DeviceAuthResult authenticate(String providedKey) {
        if (providedKey == null || providedKey.trim().isEmpty() || configuredKeySemEsp32_001.isEmpty()) {
            return DeviceAuthResult.invalidKey();
        }

        // Constant-time comparison prevents timing analysis attacks
        byte[] expected = configuredKeySemEsp32_001.getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedKey.trim().getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expected, actual)) {
            return DeviceAuthResult.invalidKey();
        }

        // Key matches SEM-ESP32-001 mapping; now verify device existence and active status in DB
        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(TARGET_DEVICE_ID);
        if (deviceOpt.isEmpty()) {
            log.warn("Device authentication rejected: mapped device '{}' not found in database.", TARGET_DEVICE_ID);
            return DeviceAuthResult.deviceNotFound();
        }

        Device device = deviceOpt.get();
        if (!"ACTIVE".equalsIgnoreCase(device.getStatus())) {
            log.warn("Device authentication rejected: mapped device '{}' is inactive (status: {}).",
                    TARGET_DEVICE_ID, device.getStatus());
            return DeviceAuthResult.deviceInactive(TARGET_DEVICE_ID);
        }

        return DeviceAuthResult.success(TARGET_DEVICE_ID);
    }
}
