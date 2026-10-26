package com.jobtrack.repository;

import com.jobtrack.entity.GmailConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GmailConnectionRepository extends JpaRepository<GmailConnection, Long> {

    Optional<GmailConnection> findByUserId(Long userId);

    Optional<GmailConnection> findByGmailAddressIgnoreCase(String gmailAddress);

    /** Users with a connected mailbox, for the periodic background sync. */
    @Query("select c.user.id from GmailConnection c")
    List<Long> findAllUserIds();

    // Sync bookkeeping. These columns are not mapped on the entity on purpose: they are written by
    // whichever server runs the check, and saving the entity elsewhere must never overwrite them.

    /** Takes the mailbox's sync lease. Returns 1 if taken, 0 if another check holds it. */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE gmail_connections SET sync_lease_until = :until, sync_done = 0, sync_total = 0
            WHERE id = :id AND (sync_lease_until IS NULL OR sync_lease_until < :now)""", nativeQuery = true)
    int claimSync(@Param("id") Long id, @Param("now") Instant now, @Param("until") Instant until);

    /** Records progress and extends the lease. Returns 0 if the mailbox was disconnected meanwhile. */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE gmail_connections SET sync_done = :done, sync_total = :total, sync_lease_until = :until
            WHERE id = :id""", nativeQuery = true)
    int recordProgress(@Param("id") Long id, @Param("done") int done, @Param("total") int total,
                       @Param("until") Instant until);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE gmail_connections SET sync_lease_until = NULL, sync_done = 0, sync_total = 0,
                last_sync_report = :report
            WHERE id = :id""", nativeQuery = true)
    int finishSync(@Param("id") Long id, @Param("report") String report);

    /**
     * Saves a check's outcome on the mailbox it ran on. Moves the "read up to" mark only when given
     * (after a complete check). A mailbox removed meanwhile is simply not updated.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE gmail_connections SET last_synced_at = COALESCE(CAST(:syncedAt AS timestamptz), last_synced_at),
                last_sync_error = :error, updated_at = :now
            WHERE id = :id""", nativeQuery = true)
    int recordResult(@Param("id") Long id, @Param("syncedAt") Instant syncedAt, @Param("error") String error,
                     @Param("now") Instant now);
    @Query(value = """
            SELECT COALESCE(sync_lease_until > :now, false) AS syncing, sync_done AS done, sync_total AS total,
                   last_sync_report AS report
            FROM gmail_connections WHERE user_id = :userId""", nativeQuery = true)
    Optional<SyncState> findSyncState(@Param("userId") Long userId, @Param("now") Instant now);

    interface SyncState {
        Boolean getSyncing();

        Integer getDone();

        Integer getTotal();

        String getReport();
    }
}
