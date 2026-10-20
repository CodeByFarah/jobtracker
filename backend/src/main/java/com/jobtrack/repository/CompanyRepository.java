package com.jobtrack.repository;

import com.jobtrack.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    /** Ownership-scoped lookup: returns empty for companies that belong to someone else. */
    Optional<Company> findByIdAndUserId(Long id, Long userId);

    List<Company> findByUserIdOrderByNameAsc(Long userId);

    List<Company> findByUserIdAndNameContainingIgnoreCaseOrderByNameAsc(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    Optional<Company> findFirstByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);

    // Logo bookkeeping is written with plain SQL so it never bumps the company's updated_at or
    // races with the user editing the company at the same moment.

    @Query(value = "SELECT id FROM companies WHERE logo_checked_at IS NULL ORDER BY id", nativeQuery = true)
    List<Long> findIdsWithoutLogoCheck();

    @Transactional
    @Modifying
    @Query(value = "UPDATE companies SET logo_checked_at = :at WHERE id = :id", nativeQuery = true)
    void markLogoChecked(@Param("id") Long id, @Param("at") Instant at);

    @Transactional
    @Modifying
    @Query(value = "UPDATE companies SET logo_checked_at = :at, logo_updated_at = :at WHERE id = :id", nativeQuery = true)
    void markLogoUpdated(@Param("id") Long id, @Param("at") Instant at);

    @Transactional
    @Modifying
    @Query(value = "UPDATE companies SET website = :website WHERE id = :id AND (website IS NULL OR website = '')",
            nativeQuery = true)
    void fillWebsiteIfEmpty(@Param("id") Long id, @Param("website") String website);
}
