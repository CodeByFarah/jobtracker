package com.jobtrack.repository;

import com.jobtrack.entity.Interview;
import com.jobtrack.entity.InterviewStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByApplicationIdOrderByScheduledAtAsc(Long applicationId);

    /** Ownership-scoped lookup through the parent application. */
    @EntityGraph(attributePaths = {"application", "application.company"})
    Optional<Interview> findByIdAndApplicationUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"application", "application.company"})
    List<Interview> findByApplicationUserIdOrderByScheduledAtAsc(Long userId);

    @EntityGraph(attributePaths = {"application", "application.company"})
    List<Interview> findByApplicationUserIdAndStatusInAndScheduledAtAfterOrderByScheduledAtAsc(
            Long userId, Collection<InterviewStatus> statuses, Instant after, Pageable pageable);

    long countByApplicationUserId(Long userId);

    long countByApplicationUserIdAndStatusInAndScheduledAtAfter(
            Long userId, Collection<InterviewStatus> statuses, Instant after);
}
