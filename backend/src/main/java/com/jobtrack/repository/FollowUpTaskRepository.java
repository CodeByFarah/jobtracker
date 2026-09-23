package com.jobtrack.repository;

import com.jobtrack.entity.FollowUpTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FollowUpTaskRepository extends JpaRepository<FollowUpTask, Long> {

    @Query("""
            select t from FollowUpTask t
            where t.application.id = :applicationId
            order by t.completed asc, t.dueDate asc nulls last, t.id asc
            """)
    List<FollowUpTask> findForApplication(@Param("applicationId") Long applicationId);

    /** Ownership-scoped lookup through the parent application. */
    @EntityGraph(attributePaths = {"application", "application.company"})
    Optional<FollowUpTask> findByIdAndApplicationUserId(Long id, Long userId);

    /** Open tasks first, then by due date (tasks without a due date last). */
    @Query("""
            select t from FollowUpTask t
            join fetch t.application a
            join fetch a.company
            where a.user.id = :userId
            order by t.completed asc, t.dueDate asc nulls last, t.id asc
            """)
    List<FollowUpTask> findAllForUser(@Param("userId") Long userId);

    @Query("""
            select t from FollowUpTask t
            join fetch t.application a
            join fetch a.company
            where a.user.id = :userId and t.completed = :completed
            order by t.dueDate asc nulls last, t.id asc
            """)
    List<FollowUpTask> findForUserByCompleted(@Param("userId") Long userId,
                                              @Param("completed") boolean completed,
                                              Pageable pageable);

    long countByApplicationUserIdAndCompletedFalse(Long userId);

    long countByApplicationUserIdAndCompletedFalseAndDueDateBefore(Long userId, LocalDate date);
}
