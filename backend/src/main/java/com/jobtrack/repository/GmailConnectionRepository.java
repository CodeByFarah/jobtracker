package com.jobtrack.repository;

import com.jobtrack.entity.GmailConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GmailConnectionRepository extends JpaRepository<GmailConnection, Long> {

    Optional<GmailConnection> findByUserId(Long userId);

    /** Users with a connected mailbox, for the periodic background sync. */
    @Query("select c.user.id from GmailConnection c")
    List<Long> findAllUserIds();
}
