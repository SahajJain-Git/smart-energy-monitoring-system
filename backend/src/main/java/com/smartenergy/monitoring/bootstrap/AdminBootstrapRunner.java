package com.smartenergy.monitoring.bootstrap;

import com.smartenergy.monitoring.entity.User;
import com.smartenergy.monitoring.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstrap runner that safely transitions the locked bootstrap administrator account
 * into an active account if and only if ADMIN_INITIAL_PASSWORD is provided via the environment.
 *
 * Safety Rules:
 * 1. Operates only on the known locked bootstrap account ('admin').
 * 2. Only transitions if is_active is false AND password_hash equals LOCKED_BOOTSTRAP_ACCOUNT_CONFIGURE_IN_PHASE_3.
 * 3. Never overwrites an already-active admin account or custom password hash.
 * 4. Never logs plaintext passwords.
 */
@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    public static final String BOOTSTRAP_ADMIN_USERNAME = "admin";
    public static final String LOCKED_BOOTSTRAP_HASH = "LOCKED_BOOTSTRAP_ACCOUNT_CONFIGURE_IN_PHASE_3";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String initialPassword;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ADMIN_INITIAL_PASSWORD:}") String initialPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.initialPassword = initialPassword != null ? initialPassword.trim() : "";
    }

    @Override
    @Transactional
    public void run(String... args) {
        provisionInitialAdmin();
    }

    /**
     * Attempts to safely provision the locked bootstrap admin account.
     *
     * @return true if account was activated, false otherwise
     */
    public boolean provisionInitialAdmin() {
        var adminOpt = userRepository.findByUsername(BOOTSTRAP_ADMIN_USERNAME);
        if (adminOpt.isEmpty()) {
            log.info("[AdminBootstrap] Bootstrap admin account ('{}') not found in database.", BOOTSTRAP_ADMIN_USERNAME);
            return false;
        }

        User admin = adminOpt.get();

        // Safety Guard 1: Do not touch an already-active account
        if (Boolean.TRUE.equals(admin.getIsActive())) {
            log.debug("[AdminBootstrap] Admin account is already active. Preserving existing credentials.");
            return false;
        }

        // Safety Guard 2: Only operate on the known locked placeholder hash
        if (!LOCKED_BOOTSTRAP_HASH.equals(admin.getPasswordHash())) {
            log.warn("[AdminBootstrap] Inactive admin account has a modified/custom hash. Will not overwrite.");
            return false;
        }

        // Safety Guard 3: If no password is provided in the environment, leave account safely locked
        if (initialPassword.isEmpty()) {
            log.info("[AdminBootstrap] Bootstrap admin account remains locked (ADMIN_INITIAL_PASSWORD environment variable not set).");
            return false;
        }

        // Securely hash with BCrypt and activate
        String hashedPassword = passwordEncoder.encode(initialPassword);
        admin.setPasswordHash(hashedPassword);
        admin.setIsActive(true);
        userRepository.save(admin);

        log.info("[AdminBootstrap] Successfully activated bootstrap admin account with BCrypt password.");
        return true;
    }
}
