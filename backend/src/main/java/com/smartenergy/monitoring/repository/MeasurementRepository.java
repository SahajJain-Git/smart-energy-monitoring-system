package com.smartenergy.monitoring.repository;

import com.smartenergy.monitoring.entity.Measurement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the Measurement entity.
 * Provides data access operations for measurements table (electrical telemetry).
 */
@Repository
public interface MeasurementRepository extends JpaRepository<Measurement, Long> {

    /**
     * Retrieves the latest electrical measurement for a specific hardware device identifier.
     * Useful for real-time live telemetry display (e.g. SEM-ESP32-001).
     *
     * @param deviceId the unique hardware identifier string (e.g. SEM-ESP32-001)
     * @return the most recent telemetry measurement, or empty if none recorded
     */
    Optional<Measurement> findTopByDevice_DeviceIdOrderByRecordedAtDesc(String deviceId);

    /**
     * Retrieves all measurements for a specific device within a designated time window.
     * Useful for time-series charts, energy calculations, and historical analysis.
     *
     * @param deviceId  the unique hardware identifier string
     * @param startTime beginning of the time interval (inclusive)
     * @param endTime   end of the time interval (inclusive)
     * @return list of measurements ordered chronologically ascending
     */
    List<Measurement> findByDevice_DeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            String deviceId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * Retrieves a paginated list of measurements for a device ordered newest first.
     *
     * @param deviceId the unique hardware identifier string
     * @param pageable pagination parameters (page, size, sort)
     * @return paginated measurements
     */
    Page<Measurement> findByDevice_DeviceIdOrderByRecordedAtDesc(String deviceId, Pageable pageable);
}
