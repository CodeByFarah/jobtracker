package com.jobtrack.repository;

import com.jobtrack.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Has a link been sent to this user recently? Used to stop someone flooding an inbox. */
    boolean existsByUserIdAndCreatedAtAfter(Long userId, Instant after);

    /** Once the password changes, every outstanding link for the account stops working. */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.userId = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
