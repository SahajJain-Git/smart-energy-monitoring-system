package com.smartenergy.monitoring.dto;

import com.smartenergy.monitoring.entity.Device;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for edge device metadata.
 */
public class DeviceResponse {

    private Long id;
    private String deviceId;
    private String name;
    private String location;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DeviceResponse() {
    }

    public DeviceResponse(Long id, String deviceId, String name, String location, String status,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.deviceId = deviceId;
        this.name = name;
        this.location = location;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static DeviceResponse fromEntity(Device device) {
        if (device == null) {
            return null;
        }
        return new DeviceResponse(
                device.getId(),
                device.getDeviceId(),
                device.getName(),
                device.getLocation(),
                device.getStatus(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
