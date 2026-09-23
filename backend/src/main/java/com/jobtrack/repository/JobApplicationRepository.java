package com.jobtrack.repository;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.JobApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long>,
        JpaSpecificationExecutor<JobApplication> {

    /** Search/filter/paginate; the company is fetched in the same query to avoid N+1 selects. */
    @Override
    @EntityGraph(attributePaths = "company")
    Page<JobApplication> findAll(Specification<JobApplication> spec, Pageable pageable);

    /** Ownership-scoped lookup: returns empty for applications that belong to someone else. */
    @EntityGraph(attributePaths = "company")
    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "company")
    List<JobApplication> findByCompanyIdAndUserIdOrderByUpdatedAtDesc(Long companyId, Long userId);

    boolean existsByCompanyId(Long companyId);

    long countByCompanyId(Long companyId);

    @Query("""
            select new com.jobtrack.repository.CompanyApplicationCount(a.company.id, count(a))
            from JobApplication a
            where a.user.id = :userId
            group by a.company.id
            """)
    List<CompanyApplicationCount> countByCompanyForUser(@Param("userId") Long userId);

    // --- Dashboard aggregates -------------------------------------------------------------

    long countByUserId(Long userId);

    long countByUserIdAndStatusIn(Long userId, Collection<ApplicationStatus> statuses);

    long countByUserIdAndApplicationDateBetween(Long userId, LocalDate from, LocalDate to);

    @Query("""
            select new com.jobtrack.repository.StatusCount(a.status, count(a))
            from JobApplication a
            where a.user.id = :userId
            group by a.status
            """)
    List<StatusCount> countByStatusForUser(@Param("userId") Long userId);

    /** Applications per calendar month (yyyy-MM), based on the date the user applied. */
    @Query(value = """
            select to_char(date_trunc('month', application_date), 'YYYY-MM') as month, count(*) as total
            from applications
            where user_id = :userId and application_date >= :from
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> countPerMonthSince(@Param("userId") Long userId, @Param("from") LocalDate from);
}
