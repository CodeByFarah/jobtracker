package com.jobtrack.repository;

import com.jobtrack.entity.EmailEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailEventRepository extends JpaRepository<EmailEvent, Long> {

    boolean existsByUserIdAndGmailMessageId(Long userId, String gmailMessageId);

    /** Most recent emails that were matched to an application (unrelated mail is not shown). */
    @EntityGraph(attributePaths = {"application", "application.company"})
    List<EmailEvent> findByUserIdAndApplicationIsNotNullOrderByReceivedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"application", "application.company"})
    List<EmailEvent> findByUserIdAndApplicationIdOrderByReceivedAtDesc(Long userId, Long applicationId);
}
