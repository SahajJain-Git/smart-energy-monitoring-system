package com.smartenergy.monitoring.repository;

import com.smartenergy.monitoring.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the User entity.
 * Provides data access operations for users table.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by unique username.
     *
     * @param username the username string
     * @return an Optional containing the User if found, or empty
     */
    Optional<User> findByUsername(String username);

    /**
     * Finds a user by unique email.
     *
     * @param email the email string
     * @return an Optional containing the User if found, or empty
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks if a user exists with the given username.
     *
     * @param username the username string
     * @return true if user exists, false otherwise
     */
    boolean existsByUsername(String username);

    /**
     * Checks if a user exists with the given email.
     *
     * @param email the email string
     * @return true if user exists, false otherwise
     */
    boolean existsByEmail(String email);
}
