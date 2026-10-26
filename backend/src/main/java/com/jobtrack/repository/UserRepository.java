package com.jobtrack.repository;

import com.jobtrack.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Applications first (companies can't be deleted while applications point at them); their
     * history, interviews and emails go with them through ON DELETE CASCADE.
     */
    @Modifying
    @Query(value = "DELETE FROM applications WHERE user_id = :userId", nativeQuery = true)
    void deleteApplicationsOf(@Param("userId") Long userId);

    /** The user row; companies, logos, the Gmail connection and email records cascade. */
    @Modifying
    @Query(value = "DELETE FROM users WHERE id = :userId", nativeQuery = true)
    void deleteUserRow(@Param("userId") Long userId);
}
