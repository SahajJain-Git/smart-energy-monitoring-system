package com.smartenergy.monitoring.service;

import com.smartenergy.monitoring.dto.DeviceResponse;
import com.smartenergy.monitoring.entity.Device;
import com.smartenergy.monitoring.exception.ResourceNotFoundException;
import com.smartenergy.monitoring.repository.DeviceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Isolated unit tests for DeviceService.
 *
 * Verifies device listing, identifier lookup, DTO mapping, and exception throwing
 * without starting Spring Boot or requiring a live database.
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    @DisplayName("getAllDevices: Should return mapped DeviceResponse list when devices exist")
    void getAllDevices_shouldReturnMappedList_whenDevicesExist() {
        // Arrange
        Device d1 = new Device("SEM-ESP32-001", "Smart Energy Monitor Prototype 1", "Lab Test Bench A", "ACTIVE");
        d1.setId(1L);
        d1.setCreatedAt(LocalDateTime.now().minusDays(1));
        d1.setUpdatedAt(LocalDateTime.now().minusHours(2));

        Device d2 = new Device("SEM-ESP32-002", "Smart Energy Monitor Node 2", "Server Rack B", "ACTIVE");
        d2.setId(2L);
        d2.setCreatedAt(LocalDateTime.now().minusDays(1));
        d2.setUpdatedAt(LocalDateTime.now().minusHours(1));

        when(deviceRepository.findAll()).thenReturn(List.of(d1, d2));

        // Act
        List<DeviceResponse> responses = deviceService.getAllDevices();

        // Assert
        assertThat(responses).hasSize(2);

        DeviceResponse r1 = responses.get(0);
        assertThat(r1.getId()).isEqualTo(1L);
        assertThat(r1.getDeviceId()).isEqualTo("SEM-ESP32-001");
        assertThat(r1.getName()).isEqualTo("Smart Energy Monitor Prototype 1");
        assertThat(r1.getLocation()).isEqualTo("Lab Test Bench A");
        assertThat(r1.getStatus()).isEqualTo("ACTIVE");

        DeviceResponse r2 = responses.get(1);
        assertThat(r2.getId()).isEqualTo(2L);
        assertThat(r2.getDeviceId()).isEqualTo("SEM-ESP32-002");
        assertThat(r2.getName()).isEqualTo("Smart Energy Monitor Node 2");
        assertThat(r2.getLocation()).isEqualTo("Server Rack B");
        assertThat(r2.getStatus()).isEqualTo("ACTIVE");

        verify(deviceRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getAllDevices: Should return empty list when no devices exist")
    void getAllDevices_shouldReturnEmptyList_whenNoDevicesExist() {
        // Arrange
        when(deviceRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<DeviceResponse> responses = deviceService.getAllDevices();

        // Assert
        assertThat(responses).isNotNull().isEmpty();
        verify(deviceRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getDeviceByIdentifier: Should return mapped DeviceResponse when device exists")
    void getDeviceByIdentifier_shouldReturnResponse_whenDeviceExists() {
        // Arrange
        Device device = new Device("SEM-ESP32-001", "Smart Energy Monitor Prototype 1", "Lab Test Bench A", "ACTIVE");
        device.setId(1L);
        LocalDateTime created = LocalDateTime.now().minusDays(2);
        LocalDateTime updated = LocalDateTime.now().minusHours(1);
        device.setCreatedAt(created);
        device.setUpdatedAt(updated);

        when(deviceRepository.findByDeviceId("SEM-ESP32-001")).thenReturn(Optional.of(device));

        // Act
        DeviceResponse response = deviceService.getDeviceByIdentifier("SEM-ESP32-001");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getDeviceId()).isEqualTo("SEM-ESP32-001");
        assertThat(response.getName()).isEqualTo("Smart Energy Monitor Prototype 1");
        assertThat(response.getLocation()).isEqualTo("Lab Test Bench A");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getCreatedAt()).isEqualTo(created);
        assertThat(response.getUpdatedAt()).isEqualTo(updated);

        verify(deviceRepository, times(1)).findByDeviceId("SEM-ESP32-001");
    }

    @Test
    @DisplayName("getDeviceByIdentifier: Should throw ResourceNotFoundException when device does not exist")
    void getDeviceByIdentifier_shouldThrowException_whenDeviceNotFound() {
        // Arrange
        String nonExistentId = "SEM-NON-EXISTENT";
        when(deviceRepository.findByDeviceId(nonExistentId)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> deviceService.getDeviceByIdentifier(nonExistentId)
        );

        assertThat(ex.getMessage()).isEqualTo("Device not found with ID: " + nonExistentId);
        verify(deviceRepository, times(1)).findByDeviceId(nonExistentId);
    }
}
