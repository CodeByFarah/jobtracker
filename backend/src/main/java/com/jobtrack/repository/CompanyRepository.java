package com.jobtrack.repository;

import com.jobtrack.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    /** Ownership-scoped lookup: returns empty for companies that belong to someone else. */
    Optional<Company> findByIdAndUserId(Long id, Long userId);

    List<Company> findByUserIdOrderByNameAsc(Long userId);

    List<Company> findByUserIdAndNameContainingIgnoreCaseOrderByNameAsc(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
}
