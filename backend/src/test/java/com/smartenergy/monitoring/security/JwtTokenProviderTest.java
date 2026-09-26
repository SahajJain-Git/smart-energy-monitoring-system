package com.smartenergy.monitoring.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String TEST_SECRET = "smart-energy-monitoring-test-secret-key-32-chars-minimum";
    private static final long EXPIRATION_MS = 60000; // 60 seconds

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("JWT TEST A1: Token generation produces non-empty compact JWT string")
    void testGenerateToken_NonEmpty() {
        String token = tokenProvider.generateToken("operator1", "OPERATOR");

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // Header.Payload.Signature
    }

    @Test
    @DisplayName("JWT TEST A2: Token contains subject matching username and role claim")
    void testGenerateToken_SubjectAndRole() {
        String token = tokenProvider.generateToken("admin", "ADMIN");

        String username = tokenProvider.getUsernameFromToken(token);
        String role = tokenProvider.getRoleFromToken(token);

        assertThat(username).isEqualTo("admin");
        assertThat(role).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("JWT TEST A3: Token does NOT contain sensitive passwords or database internals")
    void testGenerateToken_NoSensitiveCredentials() {
        String token = tokenProvider.generateToken("operator1", "OPERATOR");

        // Base64-decode the payload
        String[] parts = token.split("\\.");
        String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

        assertThat(payloadJson).doesNotContain("password");
        assertThat(payloadJson).doesNotContain("passwordHash");
        assertThat(payloadJson).doesNotContain("secret");
    }

    @Test
    @DisplayName("JWT TEST B1: Valid token is accepted")
    void testValidateToken_ValidToken() {
        String token = tokenProvider.generateToken("viewer1", "VIEWER");

        boolean isValid = tokenProvider.validateToken(token);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("JWT TEST B2: Expired token is rejected")
    void testValidateToken_ExpiredToken() {
        // Token provider with 1 ms expiration
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(TEST_SECRET, 1);
        String token = shortLivedProvider.generateToken("operator1", "OPERATOR");

        // Wait 15 ms to ensure expiration
        try {
            Thread.sleep(15);
        } catch (InterruptedException ignored) {}

        boolean isValid = tokenProvider.validateToken(token);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("JWT TEST B3: Malformed token is rejected")
    void testValidateToken_MalformedToken() {
        assertThat(tokenProvider.validateToken("not.a.valid.jwt")).isFalse();
        assertThat(tokenProvider.validateToken("random-string")).isFalse();
        assertThat(tokenProvider.validateToken("")).isFalse();
        assertThat(tokenProvider.validateToken(null)).isFalse();
    }

    @Test
    @DisplayName("JWT TEST B4: Tampered/modified payload token is rejected")
    void testValidateToken_TamperedToken() {
        String token = tokenProvider.generateToken("viewer1", "VIEWER");
        String[] parts = token.split("\\.");

        // Tamper with middle payload part
        String tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin\",\"role\":\"ADMIN\"}".getBytes());
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        boolean isValid = tokenProvider.validateToken(tamperedToken);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("JWT TEST B5: Token signed with different secret is rejected")
    void testValidateToken_DifferentSecret() {
        JwtTokenProvider otherSecretProvider = new JwtTokenProvider(
                "different-secret-key-that-does-not-match-original-32-chars",
                EXPIRATION_MS
        );
        String tokenFromOther = otherSecretProvider.generateToken("admin", "ADMIN");

        boolean isValid = tokenProvider.validateToken(tokenFromOther);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("JWT TEST C1: Constructor fails fast with IllegalStateException when secret is null")
    void testConstructor_NullSecret_ThrowsIllegalStateException() {
        assertThatThrownBy(() -> new JwtTokenProvider(null, EXPIRATION_MS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT signing secret is not configured or is invalid");
    }

    @Test
    @DisplayName("JWT TEST C2: Constructor fails fast with IllegalStateException when secret is empty or whitespace")
    void testConstructor_EmptySecret_ThrowsIllegalStateException() {
        assertThatThrownBy(() -> new JwtTokenProvider("", EXPIRATION_MS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT signing secret is not configured or is invalid");

        assertThatThrownBy(() -> new JwtTokenProvider("   ", EXPIRATION_MS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT signing secret is not configured or is invalid");
    }

    @Test
    @DisplayName("JWT TEST C3: Constructor fails fast with IllegalStateException when secret is shorter than 32 chars (256 bits)")
    void testConstructor_ShortSecret_ThrowsIllegalStateException() {
        assertThatThrownBy(() -> new JwtTokenProvider("short-secret-under-32-chars", EXPIRATION_MS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    @DisplayName("JWT TEST C4: Constructor succeeds when secret is at least 32 chars")
    void testConstructor_ValidSecret_Succeeds() {
        JwtTokenProvider provider = new JwtTokenProvider("exactly-32-characters-secret-123", EXPIRATION_MS);
        assertThat(provider).isNotNull();
    }
}
