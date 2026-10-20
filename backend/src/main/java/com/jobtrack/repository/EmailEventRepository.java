package com.jobtrack.repository;

import com.jobtrack.entity.EmailEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmailEventRepository extends JpaRepository<EmailEvent, Long> {

    boolean existsByUserIdAndGmailMessageId(Long userId, String gmailMessageId);

    /** Most recent emails that were matched to an application (unrelated mail is not shown). */
    @EntityGraph(attributePaths = {"application", "application.company"})
    List<EmailEvent> findByUserIdAndApplicationIsNotNullOrderByReceivedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"application", "application.company"})
    List<EmailEvent> findByUserIdAndApplicationIdOrderByReceivedAtDesc(Long userId, Long applicationId);

    /** Who wrote about a company's applications; their email domains hint at the company's website. */
    @Query("select distinct e.sender from EmailEvent e where e.application.company.id = :companyId and e.sender is not null")
    List<String> findSendersByCompanyId(@Param("companyId") Long companyId);
}
