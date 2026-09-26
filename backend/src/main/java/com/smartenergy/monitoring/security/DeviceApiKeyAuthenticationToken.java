package com.smartenergy.monitoring.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Authentication token representing an authenticated IoT edge device identified via its API key.
 * Contains the authoritative device identifier (e.g. SEM-ESP32-001) as principal and DEVICE role authority.
 * Never retains the raw API key secret in memory.
 */
public class DeviceApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final String deviceId;

    public DeviceApiKeyAuthenticationToken(String deviceId, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.deviceId = deviceId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null; // The secret key is never retained in the SecurityContext
    }

    @Override
    public Object getPrincipal() {
        return this.deviceId;
    }
}
