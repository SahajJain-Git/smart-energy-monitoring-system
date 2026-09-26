package com.smartenergy.monitoring.service;

import com.smartenergy.monitoring.dto.MeasurementRequest;
import com.smartenergy.monitoring.dto.MeasurementResponse;
import com.smartenergy.monitoring.entity.Device;
import com.smartenergy.monitoring.entity.Measurement;
import com.smartenergy.monitoring.exception.ResourceNotFoundException;
import com.smartenergy.monitoring.repository.DeviceRepository;
import com.smartenergy.monitoring.repository.MeasurementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Isolated unit tests for MeasurementService.
 *
 * Verifies apparent energy calculations, rounding, defaults, client-supplied value handling,
 * and repository interactions without starting Spring Boot or requiring a live database.
 */
@ExtendWith(MockitoExtension.class)
class MeasurementServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private MeasurementRepository measurementRepository;

    @InjectMocks
    private MeasurementService measurementService;

    @Captor
    private ArgumentCaptor<Measurement> measurementCaptor;

    @Captor
    private ArgumentCaptor<Device> deviceCaptor;

    private Device existingDevice;

    @BeforeEach
    void setUp() {
        existingDevice = new Device("SEM-ESP32-001", "Smart Energy Monitor Prototype 1", "Lab Test Bench A", "ACTIVE");
        existingDevice.setId(1L);
    }

    @Test
    @DisplayName("recordMeasurement: Should compute Apparent Power (S = Vrms * Irms) when omitted by client")
    void recordMeasurement_shouldComputeApparentPower_whenOmitted() {
        // Arrange
        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("5.000"));
        request.setSamplingDurationSeconds(new BigDecimal("1.00"));
        request.setApparentPower(null); // Explicitly omitted

        when(deviceRepository.findByDeviceId("SEM-ESP32-001")).thenReturn(Optional.of(existingDevice));
        when(measurementRepository.save(any(Measurement.class))).thenAnswer(invocation -> {
            Measurement m = invocation.getArgument(0);
            m.setId(101L);
            return m;
        });

        // Act
        MeasurementResponse response = measurementService.recordMeasurement(request);

        // Assert
        verify(measurementRepository).save(measurementCaptor.capture());
        Measurement captured = measurementCaptor.getValue();

        // S = 230.00 * 5.000 = 1150.00 VA
        BigDecimal expectedPower = new BigDecimal("1150.00");
        assertThat(captured.getApparentPower()).isEqualByComparingTo(expectedPower);
        assertThat(captured.getApparentPower().scale()).isEqualTo(2);

        assertThat(response).isNotNull();
        assertThat(response.getApparentPower()).isEqualByComparingTo(expectedPower);
    }

    @Test
    @DisplayName("recordMeasurement: Should compute Incremental Apparent Energy (delta_VAh = S * dt / 3600) when omitted")
    void recordMeasurement_shouldComputeIncrementalEnergy_whenOmitted() {
        // Arrange
        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("5.000"));
        request.setSamplingDurationSeconds(new BigDecimal("1.00"));
        request.setIncrementalApparentEnergy(null); // Explicitly omitted

        when(deviceRepository.findByDeviceId("SEM-ESP32-001")).thenReturn(Optional.of(existingDevice));
        when(measurementRepository.save(any(Measurement.class))).thenAnswer(invocation -> {
            Measurement m = invocation.getArgument(0);
            m.setId(102L);
            return m;
        });

        // Act
        MeasurementResponse response = measurementService.recordMeasurement(request);

        // Assert
        verify(measurementRepository).save(measurementCaptor.capture());
        Measurement captured = measurementCaptor.getValue();

        // S = 1150.00 VA, dt = 1.00 s
        // delta_VAh = 1150.00 * 1.00 / 3600 = 0.319444... -> rounded to 4 decimals = 0.3194 VAh
        BigDecimal expectedEnergy = new BigDecimal("0.3194");
        assertThat(captured.getIncrementalApparentEnergy()).isEqualByComparingTo(expectedEnergy);
        assertThat(captured.getIncrementalApparentEnergy().scale()).isEqualTo(4);

        assertThat(response).isNotNull();
        assertThat(response.getIncrementalApparentEnergy()).isEqualByComparingTo(expectedEnergy);
    }

    @Test
    @DisplayName("recordMeasurement: Should default samplingDurationSeconds to 1.00 when omitted")
    void recordMeasurement_shouldDefaultSamplingDuration_whenOmitted() {
        // Arrange
        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("5.000"));
        request.setSamplingDurationSeconds(null); // Omitted duration

        when(deviceRepository.findByDeviceId("SEM-ESP32-001")).thenReturn(Optional.of(existingDevice));
        when(measurementRepository.save(any(Measurement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        measurementService.recordMeasurement(request);

        // Assert
        verify(measurementRepository).save(measurementCaptor.capture());
        Measurement captured = measurementCaptor.getValue();

        BigDecimal expectedDuration = new BigDecimal("1.00");
        assertThat(captured.getSamplingDurationSeconds()).isEqualByComparingTo(expectedDuration);
    }

    @Test
    @DisplayName("recordMeasurement: Should supply non-null timestamp when recordedAt is omitted")
    void recordMeasurement_shouldSupplyTimestamp_whenOmitted() {
        // Arrange
        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("5.000"));
        request.setRecordedAt(null); // Omitted timestamp

        LocalDateTime testStartTime = LocalDateTime.now().minusSeconds(1);

        when(deviceRepository.findByDeviceId("SEM-ESP32-001")).thenReturn(Optional.of(existingDevice));
        when(measurementRepository.save(any(Measurement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        measurementService.recordMeasurement(request);
        LocalDateTime testEndTime = LocalDateTime.now().plusSeconds(1);

        // Assert
        verify(measurementRepository).save(measurementCaptor.capture());
        Measurement captured = measurementCaptor.getValue();

        assertThat(captured.getRecordedAt()).isNotNull();
        assertThat(captured.getRecordedAt()).isAfterOrEqualTo(testStartTime);
        assertThat(captured.getRecordedAt()).isBeforeOrEqualTo(testEndTime);
    }

    @Test
    @DisplayName("recordMeasurement: Should preserve client-supplied apparentPower and incrementalEnergy without recalculation")
    void recordMeasurement_shouldPreserveClientSuppliedCalculations() {
        // Arrange: Provide deliberately distinct values from raw Vrms * Irms
        // Raw V * I = 230.00 * 5.000 = 1150.00 VA, but client sends 1250.00 VA and 0.5000 VAh
        BigDecimal clientSuppliedPower = new BigDecimal("1250.00");
        BigDecimal clientSuppliedEnergy = new BigDecimal("0.5000");

        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("5.000"));
        request.setSamplingDurationSeconds(new BigDecimal("1.00"));
        request.setApparentPower(clientSuppliedPower);
        request.setIncrementalApparentEnergy(clientSuppliedEnergy);

        when(deviceRepository.findByDeviceId("SEM-ESP32-001")).thenReturn(Optional.of(existingDevice));
        when(measurementRepository.save(any(Measurement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MeasurementResponse response = measurementService.recordMeasurement(request);

        // Assert
        verify(measurementRepository).save(measurementCaptor.capture());
        Measurement captured = measurementCaptor.getValue();

        // Production behavior check: Values were preserved, NOT overwritten with 1150.00 VA or 0.3194 VAh
        assertThat(captured.getApparentPower()).isEqualByComparingTo(clientSuppliedPower);
        assertThat(captured.getIncrementalApparentEnergy()).isEqualByComparingTo(clientSuppliedEnergy);

        assertThat(response.getApparentPower()).isEqualByComparingTo(clientSuppliedPower);
        assertThat(response.getIncrementalApparentEnergy()).isEqualByComparingTo(clientSuppliedEnergy);
    }

    @Test
    @DisplayName("recordMeasurement: Should auto-register unknown device when not found in repository")
    void recordMeasurement_shouldAutoRegisterUnknownDevice() {
        // Arrange
        String unknownDeviceId = "SEM-ESP32-999";
        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId(unknownDeviceId);
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("2.500"));

        when(deviceRepository.findByDeviceId(unknownDeviceId)).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device newDev = invocation.getArgument(0);
            newDev.setId(99L);
            return newDev;
        });
        when(measurementRepository.save(any(Measurement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MeasurementResponse response = measurementService.recordMeasurement(request);

        // Assert
        verify(deviceRepository).save(deviceCaptor.capture());
        Device autoRegisteredDevice = deviceCaptor.getValue();

        assertThat(autoRegisteredDevice.getDeviceId()).isEqualTo(unknownDeviceId);
        assertThat(autoRegisteredDevice.getName()).isEqualTo("Edge Sensor " + unknownDeviceId);
        assertThat(autoRegisteredDevice.getLocation()).isEqualTo("Lab / Unspecified");
        assertThat(autoRegisteredDevice.getStatus()).isEqualTo("ACTIVE");

        verify(measurementRepository).save(measurementCaptor.capture());
        assertThat(measurementCaptor.getValue().getDevice().getDeviceId()).isEqualTo(unknownDeviceId);
        assertThat(response.getDeviceId()).isEqualTo(unknownDeviceId);
    }

    @Test
    @DisplayName("getLatestMeasurement: Should throw ResourceNotFoundException when device does not exist")
    void getLatestMeasurement_shouldThrowException_whenDeviceNotFound() {
        // Arrange
        String nonExistentId = "UNKNOWN-DEVICE";
        when(deviceRepository.existsByDeviceId(nonExistentId)).thenReturn(false);

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> measurementService.getLatestMeasurement(nonExistentId)
        );

        assertThat(ex.getMessage()).contains("Device not found with ID: " + nonExistentId);
        verify(measurementRepository, never()).findTopByDevice_DeviceIdOrderByRecordedAtDesc(anyString());
    }

    @Test
    @DisplayName("getLatestMeasurement: Should throw ResourceNotFoundException when device exists but has no telemetry")
    void getLatestMeasurement_shouldThrowException_whenNoTelemetryExists() {
        // Arrange
        when(deviceRepository.existsByDeviceId("SEM-ESP32-001")).thenReturn(true);
        when(measurementRepository.findTopByDevice_DeviceIdOrderByRecordedAtDesc("SEM-ESP32-001"))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> measurementService.getLatestMeasurement("SEM-ESP32-001")
        );

        assertThat(ex.getMessage()).contains("No telemetry measurements found for device: SEM-ESP32-001");
    }

    @Test
    @DisplayName("getLatestMeasurement: Should return mapped MeasurementResponse when telemetry exists")
    void getLatestMeasurement_shouldReturnResponse_whenTelemetryExists() {
        // Arrange
        Measurement measurement = new Measurement(
                existingDevice,
                new BigDecimal("230.15"),
                new BigDecimal("4.850"),
                new BigDecimal("1116.23"),
                new BigDecimal("0.3101"),
                new BigDecimal("1.00"),
                LocalDateTime.now()
        );
        measurement.setId(201L);

        when(deviceRepository.existsByDeviceId("SEM-ESP32-001")).thenReturn(true);
        when(measurementRepository.findTopByDevice_DeviceIdOrderByRecordedAtDesc("SEM-ESP32-001"))
                .thenReturn(Optional.of(measurement));

        // Act
        MeasurementResponse response = measurementService.getLatestMeasurement("SEM-ESP32-001");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(201L);
        assertThat(response.getDeviceId()).isEqualTo("SEM-ESP32-001");
        assertThat(response.getVoltageRms()).isEqualByComparingTo(new BigDecimal("230.15"));
        assertThat(response.getCurrentRms()).isEqualByComparingTo(new BigDecimal("4.850"));
        assertThat(response.getApparentPower()).isEqualByComparingTo(new BigDecimal("1116.23"));
        assertThat(response.getIncrementalApparentEnergy()).isEqualByComparingTo(new BigDecimal("0.3101"));
    }

    @Test
    @DisplayName("getHistoricalMeasurements: Should throw ResourceNotFoundException when device does not exist")
    void getHistoricalMeasurements_shouldThrowException_whenDeviceNotFound() {
        // Arrange
        String nonExistentId = "UNKNOWN-DEVICE";
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now();

        when(deviceRepository.existsByDeviceId(nonExistentId)).thenReturn(false);

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> measurementService.getHistoricalMeasurements(nonExistentId, start, end)
        );

        assertThat(ex.getMessage()).contains("Device not found with ID: " + nonExistentId);
        verify(measurementRepository, never()).findByDevice_DeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(anyString(), any(), any());
    }

    @Test
    @DisplayName("getHistoricalMeasurements: Should return list of MeasurementResponse objects when measurements exist")
    void getHistoricalMeasurements_shouldReturnMappedList_whenDataExists() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(1);
        LocalDateTime end = now;

        Measurement m1 = new Measurement(existingDevice, new BigDecimal("230.00"), new BigDecimal("1.000"),
                new BigDecimal("230.00"), new BigDecimal("0.0639"), new BigDecimal("1.00"), now.minusMinutes(10));
        m1.setId(301L);

        Measurement m2 = new Measurement(existingDevice, new BigDecimal("231.00"), new BigDecimal("2.000"),
                new BigDecimal("462.00"), new BigDecimal("0.1283"), new BigDecimal("1.00"), now.minusMinutes(5));
        m2.setId(302L);

        when(deviceRepository.existsByDeviceId("SEM-ESP32-001")).thenReturn(true);
        when(measurementRepository.findByDevice_DeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc("SEM-ESP32-001", start, end))
                .thenReturn(List.of(m1, m2));

        // Act
        List<MeasurementResponse> results = measurementService.getHistoricalMeasurements("SEM-ESP32-001", start, end);

        // Assert
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo(301L);
        assertThat(results.get(0).getApparentPower()).isEqualByComparingTo(new BigDecimal("230.00"));
        assertThat(results.get(1).getId()).isEqualTo(302L);
        assertThat(results.get(1).getApparentPower()).isEqualByComparingTo(new BigDecimal("462.00"));
    }

    @Test
    @DisplayName("getHistoricalMeasurements: Should return empty list when no measurements match time interval")
    void getHistoricalMeasurements_shouldReturnEmptyList_whenNoDataMatches() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().minusHours(2);
        LocalDateTime end = LocalDateTime.now().minusHours(1);

        when(deviceRepository.existsByDeviceId("SEM-ESP32-001")).thenReturn(true);
        when(measurementRepository.findByDevice_DeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc("SEM-ESP32-001", start, end))
                .thenReturn(Collections.emptyList());

        // Act
        List<MeasurementResponse> results = measurementService.getHistoricalMeasurements("SEM-ESP32-001", start, end);

        // Assert
        assertThat(results).isNotNull().isEmpty();
    }
}
