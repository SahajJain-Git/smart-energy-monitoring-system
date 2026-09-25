package com.smartenergy.monitoring.repository;

import com.smartenergy.monitoring.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the Device entity.
 * Provides data access operations for devices table.
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    /**
     * Finds a device by its unique business hardware identifier (e.g., 'SEM-ESP32-001').
     *
     * @param deviceId the unique hardware identifier string
     * @return an Optional containing the Device if found, or empty
     */
    Optional<Device> findByDeviceId(String deviceId);

    /**
     * Checks if a device exists with the given hardware identifier.
     *
     * @param deviceId the unique hardware identifier string
     * @return true if device exists, false otherwise
     */
    boolean existsByDeviceId(String deviceId);
}
