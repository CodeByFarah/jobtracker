package com.jobtrack.repository;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.ApplicationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, Long> {

    /** Chronological timeline; the id breaks ties between entries with the same timestamp. */
    List<ApplicationStatusHistory> findByApplicationIdOrderByChangedAtAscIdAsc(Long applicationId);

    /**
     * Number of the user's applications that ever reached the given status, even if they have
     * moved on since (e.g. an offer that was later accepted or declined still counts as an offer).
     */
    @Query("""
            select count(distinct h.application.id)
            from ApplicationStatusHistory h
            where h.application.user.id = :userId and h.newStatus = :status
            """)
    long countApplicationsThatReachedStatus(@Param("userId") Long userId,
                                            @Param("status") ApplicationStatus status);
}
